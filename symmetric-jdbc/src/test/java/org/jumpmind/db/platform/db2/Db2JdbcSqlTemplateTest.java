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
package org.jumpmind.db.platform.db2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Db2JdbcSqlTemplateTest {
    private Db2JdbcSqlTemplate template;

    @BeforeEach
    void setup() {
        DataSource dataSource = mock(DataSource.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        template = new Db2JdbcSqlTemplate(dataSource, settings, null, new DatabaseInfo());
    }

    @Test
    void testConstructor_setsPrimaryKeyViolationCode() {
        SQLException matching = new SQLException("duplicate key", "23505", -803);
        SQLException nonMatching = new SQLException("other error", "42000", -104);
        assertTrue(template.isUniqueKeyViolation(matching));
        assertFalse(template.isUniqueKeyViolation(nonMatching));
    }

    @Test
    void testConstructor_setsForeignKeyViolationCode() {
        SQLException matching = new SQLException("fk violation", "23503", -530);
        SQLException nonMatching = new SQLException("other error", "42000", -104);
        assertTrue(template.isForeignKeyViolation(matching));
        assertFalse(template.isForeignKeyViolation(nonMatching));
    }

    @Test
    void testConstructor_setsForeignKeyChildExistsViolationCodes() {
        SQLException childExists1 = new SQLException("child exists", "23001", -531);
        SQLException childExists2 = new SQLException("child exists", "23001", -532);
        SQLException nonMatching = new SQLException("other error", "42000", -104);
        assertTrue(template.isForeignKeyChildExistsViolation(childExists1));
        assertTrue(template.isForeignKeyChildExistsViolation(childExists2));
        assertFalse(template.isForeignKeyChildExistsViolation(nonMatching));
    }

    @Test
    void testConstructor_setsDeadlockCode() {
        SQLException matching = new SQLException("deadlock", "40001", -911);
        SQLException nonMatching = new SQLException("other error", "42000", -104);
        assertTrue(template.isDeadlock(matching));
        assertFalse(template.isDeadlock(nonMatching));
    }

    @Test
    void testGetSelectLastInsertIdSql_returnsIdentityValLocalQuery() {
        assertEquals("SELECT IDENTITY_VAL_LOCAL() FROM SYSIBM.SYSDUMMY1", template.getSelectLastInsertIdSql("SEQ_NAME"));
    }

    @Test
    void testSupportsGetGeneratedKeys_returnsFalse() {
        assertFalse(template.supportsGetGeneratedKeys());
    }

    @Test
    void testAllowsNullForIdentityColumn_returnsFalse() {
        assertFalse(template.allowsNullForIdentityColumn());
    }

    @Test
    void testGetUniqueKeyViolationIndexName_uniqueIndexViolationMessage_returnsWildcard() {
        SQLException ex = new SQLException("SQLCODE=-803, SQLSTATE=23505, SQLERRMC=2;INDEXNAME");
        assertEquals("%", template.getUniqueKeyViolationIndexName(ex));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_unrelatedMessage_returnsNull() {
        SQLException ex = new SQLException("SQLCODE=-104, SQLSTATE=42601");
        assertNull(template.getUniqueKeyViolationIndexName(ex));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_wrappedSqlException_stillDetected() {
        SQLException cause = new SQLException("SQLSTATE=23505, SQLERRMC=2;INDEXNAME");
        RuntimeException wrapper = new RuntimeException("wrapped", cause);
        assertEquals("%", template.getUniqueKeyViolationIndexName(wrapper));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_noSqlExceptionInChain_returnsNull() {
        assertNull(template.getUniqueKeyViolationIndexName(new RuntimeException("no sql exception here")));
    }
}
