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
package org.jumpmind.db.platform.hsqldb2;

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

class HsqlDb2JdbcSqlTemplateTest {
    private HsqlDb2JdbcSqlTemplate template;

    @BeforeEach
    void setup() {
        DataSource dataSource = mock(DataSource.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        template = new HsqlDb2JdbcSqlTemplate(dataSource, settings, null, new DatabaseInfo());
    }

    @Test
    void testConstructor_setsPrimaryKeyViolationSqlState() {
        SQLException matching = new SQLException("unique constraint violation", "23505");
        SQLException nonMatching = new SQLException("other error", "42000");
        assertTrue(template.isUniqueKeyViolation(matching));
        assertFalse(template.isUniqueKeyViolation(nonMatching));
    }

    @Test
    void testConstructor_setsForeignKeyViolationSqlState() {
        SQLException matching = new SQLException("fk violation", "23503");
        SQLException nonMatching = new SQLException("other error", "42000");
        assertTrue(template.isForeignKeyViolation(matching));
        assertFalse(template.isForeignKeyViolation(nonMatching));
    }

    @Test
    void testConstructor_setsForeignKeyChildExistsViolationSqlState() {
        SQLException matching = new SQLException("child exists", "23504");
        SQLException nonMatching = new SQLException("other error", "42000");
        assertTrue(template.isForeignKeyChildExistsViolation(matching));
        assertFalse(template.isForeignKeyChildExistsViolation(nonMatching));
    }

    @Test
    void testAllowsNullForIdentityColumn_returnsFalse() {
        assertFalse(template.allowsNullForIdentityColumn());
    }

    @Test
    void testGetSelectLastInsertIdSql_returnsCallIdentity() {
        assertEquals("call IDENTITY()", template.getSelectLastInsertIdSql("SEQ_NAME"));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_matchingMessage_returnsCapturedIndexName() {
        SQLException ex = new SQLException("unique constraint or index violation: \"SYS_PK_10\"");
        assertEquals("SYS_PK_10", template.getUniqueKeyViolationIndexName(ex));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_unrelatedMessage_returnsNull() {
        SQLException ex = new SQLException("some other failure");
        assertNull(template.getUniqueKeyViolationIndexName(ex));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_wrappedSqlException_stillDetected() {
        SQLException cause = new SQLException("unique constraint or index violation: \"IDX_NAME\"");
        RuntimeException wrapper = new RuntimeException("wrapped", cause);
        assertEquals("IDX_NAME", template.getUniqueKeyViolationIndexName(wrapper));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_noSqlExceptionInChain_returnsNull() {
        assertNull(template.getUniqueKeyViolationIndexName(new RuntimeException("no sql exception here")));
    }
}
