/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.db.platform.voltdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.util.BinaryEncoding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoltDbDatabasePlatformTest {
    private DataSource dataSource;
    private VoltDbDatabasePlatform platform;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        platform = new VoltDbDatabasePlatform(dataSource, new SqlTemplateSettings());
    }

    @Test
    void testConstructor_configuresDatabaseInfo() {
        assertTrue(platform.getDatabaseInfo().isRequiresAutoCommitForDdl());
        assertEquals("", platform.getDatabaseInfo().getDelimiterToken());
        assertFalse(platform.getDatabaseInfo().isDelimitedIdentifiersSupported());
        assertFalse(platform.getDatabaseInfo().isTriggersSupported());
        assertFalse(platform.getDatabaseInfo().isForeignKeysSupported());
    }

    @Test
    void testConstructor_disablesPrecisionAndScaleForDecimalAndFloat() {
        assertFalse(platform.getDatabaseInfo().hasPrecisionAndScale(Types.DECIMAL));
        assertFalse(platform.getDatabaseInfo().hasPrecisionAndScale(Types.FLOAT));
    }

    @Test
    void testGetName() {
        assertEquals(DatabaseNamesConstants.VOLTDB, platform.getName());
    }

    @Test
    void testGetClassName() {
        VoltDbDatabasePlatform mockPlatform = mock(VoltDbDatabasePlatform.class, CALLS_REAL_METHODS);
        assertEquals(VoltDbDatabasePlatform.class.getName(), mockPlatform.getClassName());
    }

    @Test
    void testGetDefaultSchema() {
        assertNull(platform.getDefaultSchema());
    }

    @Test
    void testGetDefaultCatalog() {
        assertNull(platform.getDefaultCatalog());
    }

    @Test
    void testGetDataSource() {
        assertSame(dataSource, platform.getDataSource());
    }

    @Test
    void testCreateDdlBuilder() {
        assertTrue(platform.getDdlBuilder() instanceof VoltDbDdlBuilder);
    }

    @Test
    void testCreateDdlReader() {
        assertTrue(platform.getDdlReader() instanceof VoltDbDdlReader);
    }

    @Test
    void testCreateSqlTemplate() {
        assertTrue(platform.getSqlTemplate() instanceof VoltDbJdbcSqlTemplate);
    }

    @Test
    void testCreateSqlTemplateDirty_reusesTheSqlTemplate() {
        assertSame(platform.getSqlTemplate(), platform.getSqlTemplateDirty());
    }

    @Test
    void testGetCreateSymTablePermission() {
        assertUnimplemented(platform.getCreateSymTablePermission(new Database()), PermissionType.CREATE_TABLE);
    }

    @Test
    void testGetDropSymTablePermission() {
        assertUnimplemented(platform.getDropSymTablePermission(), PermissionType.DROP_TABLE);
    }

    @Test
    void testGetAlterSymTablePermission() {
        assertUnimplemented(platform.getAlterSymTablePermission(new Database()), PermissionType.ALTER_TABLE);
    }

    @Test
    void testGetDropSymTriggerPermission() {
        assertUnimplemented(platform.getDropSymTriggerPermission(), PermissionType.DROP_TRIGGER);
    }

    @Test
    void testSupportsLimitOffset() {
        assertTrue(platform.supportsLimitOffset());
    }

    @Test
    void testMassageForLimitOffset() {
        assertEquals("select * from item limit 10 offset 5", platform.massageForLimitOffset("select * from item", 10, 5));
    }

    @Test
    void testMassageForLimitOffset_stripsTrailingSemicolon() {
        assertEquals("select * from item limit 10 offset 0", platform.massageForLimitOffset("select * from item;", 10, 0));
    }

    @Test
    void testReadDatabaseFromXml_turnsOffAutoIncrement() {
        String xml = "<?xml version=\"1.0\"?><database name=\"test\"><table name=\"item\">"
                + "<column name=\"item_id\" type=\"INTEGER\" required=\"true\" autoIncrement=\"true\" primaryKey=\"true\"/>"
                + "</table></database>";
        Database database = platform.readDatabaseFromXml(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), false);
        assertFalse(database.getTable(0).getColumn(0).isAutoIncrement());
    }

    @Test
    void testGetObjectValue_hexEncodesVarbinary() throws Exception {
        Column column = new Column("data", false, Types.VARBINARY, 100, 0);
        assertEquals("4142", platform.getObjectValue("4142", column, BinaryEncoding.HEX, false, false));
    }

    @Test
    void testGetObjectValue_reencodesBase64VarbinaryAsHex() throws Exception {
        Column column = new Column("data", false, Types.VARBINARY, 100, 0);
        assertEquals("4142", platform.getObjectValue("QUI=", column, BinaryEncoding.BASE64, false, false));
    }

    @Test
    void testGetObjectValue_leavesClobValuesAlone() throws Exception {
        // Dead branch pinned, not endorsed: the CLOB arm of the check never fires because
        // the superclass only returns a byte[] for the binary type codes, never for CLOB
        Column column = new Column("notes", false, Types.CLOB, 100, 0);
        assertEquals("4142", platform.getObjectValue("4142", column, BinaryEncoding.HEX, false, false));
    }

    @Test
    void testGetObjectValue_leavesNonBinaryValuesAlone() throws Exception {
        Column column = new Column("name", false, Types.VARCHAR, 100, 0);
        assertEquals("widget", platform.getObjectValue("widget", column, BinaryEncoding.HEX, false, false));
    }

    private void assertUnimplemented(PermissionResult result, PermissionType permissionType) {
        assertEquals(Status.UNIMPLEMENTED, result.getStatus());
        assertEquals(permissionType, result.getPermissionType());
    }
}
