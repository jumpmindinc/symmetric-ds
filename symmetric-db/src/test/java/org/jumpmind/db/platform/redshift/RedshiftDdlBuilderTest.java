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
package org.jumpmind.db.platform.redshift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedshiftDdlBuilderTest {
    private RedshiftDdlBuilder ddlBuilder;
    private DatabaseInfo databaseInfo;

    @BeforeEach
    void setUp() {
        ddlBuilder = new RedshiftDdlBuilder();
        databaseInfo = ddlBuilder.getDatabaseInfo();
    }

    @Test
    void testConstructor_disablesUnsupportedFeatures() {
        assertFalse(databaseInfo.isTriggersSupported());
        assertFalse(databaseInfo.isIndicesSupported());
        assertFalse(databaseInfo.isIndicesEmbedded());
        assertFalse(databaseInfo.isForeignKeysSupported());
    }

    @Test
    void testConstructor_setsTransactionAndIdentifierBehavior() {
        assertTrue(databaseInfo.isRequiresSavePointsInTransaction());
        assertTrue(databaseInfo.isRequiresAutoCommitForDdl());
        assertEquals(127, databaseInfo.getMaxColumnNameLength());
    }

    @Test
    void testConstructor_setsCharColumnPaddingBehavior() {
        assertTrue(databaseInfo.isNonBlankCharColumnSpacePadded());
        assertTrue(databaseInfo.isBlankCharColumnSpacePadded());
        assertFalse(databaseInfo.isCharColumnSpaceTrimmed());
        assertFalse(databaseInfo.isEmptyStringNulled());
    }

    @Test
    void testConstructor_mapsBitToBoolean() {
        assertEquals("BOOLEAN", databaseInfo.getNativeType(Types.BIT));
    }

    @Test
    void testConstructor_mapsDoubleAndFloatToDoublePrecision() {
        assertEquals("DOUBLE PRECISION", databaseInfo.getNativeType(Types.DOUBLE));
        assertEquals("DOUBLE PRECISION", databaseInfo.getNativeType(Types.FLOAT));
        assertEquals(Types.DOUBLE, databaseInfo.getTargetJdbcType(Types.DOUBLE));
    }

    @Test
    void testConstructor_mapsLongVarcharAndClobTo65535Varchar() {
        assertEquals("VARCHAR(65535)", databaseInfo.getNativeType(Types.LONGVARCHAR));
        assertEquals("VARCHAR(65535)", databaseInfo.getNativeType(Types.CLOB));
    }

    @Test
    void testConstructor_mapsTinyIntToSmallInt() {
        assertEquals("SMALLINT", databaseInfo.getNativeType(Types.TINYINT));
        assertEquals(Types.SMALLINT, databaseInfo.getTargetJdbcType(Types.TINYINT));
    }

    @Test
    void testConstructor_mapsTimeToTimestamp() {
        assertEquals("TIMESTAMP", databaseInfo.getNativeType(Types.TIME));
        assertEquals(Types.TIMESTAMP, databaseInfo.getTargetJdbcType(Types.TIME));
    }

    @Test
    void testConstructor_setsMaxSizesForDateTimeTypes() {
        assertEquals(6, databaseInfo.getMaxSize("TIMESTAMP"));
        assertEquals(6, databaseInfo.getMaxSize("TIMESTAMPTZ"));
        assertEquals(6, databaseInfo.getMaxSize("TIME"));
        assertEquals(6, databaseInfo.getMaxSize("TIMETZ"));
    }

    @Test
    void testConstructor_setsDefaultSizesForCharAndVarchar() {
        assertEquals(256, databaseInfo.getDefaultSize(Types.CHAR));
        assertEquals(256, databaseInfo.getDefaultSize(Types.VARCHAR));
    }

    @Test
    void testWriteColumnAutoIncrementStmt_writesNothing() {
        Table table = new Table("item");
        Column column = new Column("id", true, Types.INTEGER, 10, 0);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeColumnAutoIncrementStmt(table, column, ddl);
        assertEquals("", ddl.toString());
    }

    @Test
    void testWriteCascadeAttributesForForeignKey_writesNothing() {
        ForeignKey foreignKey = new ForeignKey("fk_item_order");
        foreignKey.setOnDeleteAction(ForeignKeyAction.CASCADE);
        foreignKey.setOnUpdateAction(ForeignKeyAction.CASCADE);
        StringBuilder ddl = new StringBuilder();
        ddlBuilder.writeCascadeAttributesForForeignKey(foreignKey, ddl);
        assertEquals("", ddl.toString());
    }
}
