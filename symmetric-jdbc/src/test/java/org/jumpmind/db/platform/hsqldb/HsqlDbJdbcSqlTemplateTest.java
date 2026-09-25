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
package org.jumpmind.db.platform.hsqldb;

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

class HsqlDbJdbcSqlTemplateTest {
    private HsqlDbJdbcSqlTemplate template;

    @BeforeEach
    void setup() {
        DataSource dataSource = mock(DataSource.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        template = new HsqlDbJdbcSqlTemplate(dataSource, settings, null, new DatabaseInfo());
    }

    @Test
    void testConstructor_setsPrimaryKeyViolationSqlState_matchingState_returnsTrue() {
        SQLException matching = new SQLException("unique constraint violation", "23505");
        assertTrue(template.isUniqueKeyViolation(matching));
    }

    @Test
    void testConstructor_setsPrimaryKeyViolationSqlState_nonMatchingState_returnsFalse() {
        SQLException nonMatching = new SQLException("other error", "42000");
        assertFalse(template.isUniqueKeyViolation(nonMatching));
    }

    @Test
    void testConstructor_setsForeignKeyViolationCode_matchingCode_returnsTrue() {
        SQLException matching = new SQLException("fk violation", "23000", 23506);
        assertTrue(template.isForeignKeyViolation(matching));
    }

    @Test
    void testConstructor_setsForeignKeyViolationCode_nonMatchingCode_returnsFalse() {
        SQLException nonMatching = new SQLException("other error", "23000", 42000);
        assertFalse(template.isForeignKeyViolation(nonMatching));
    }

    @Test
    void testGetSelectLastInsertIdSql_returnsCallIdentity() {
        assertEquals("call IDENTITY()", template.getSelectLastInsertIdSql("SEQ_NAME"));
    }

    @Test
    void testAllowsNullForIdentityColumn_returnsFalse() {
        assertFalse(template.allowsNullForIdentityColumn());
    }

    @Test
    void testGetUniqueKeyViolationIndexName_regexNotConfigured_returnsNull() {
        SQLException ex = new SQLException("unique constraint or index violation: \"SYS_PK_10\"");
        assertNull(template.getUniqueKeyViolationIndexName(ex));
    }
}
