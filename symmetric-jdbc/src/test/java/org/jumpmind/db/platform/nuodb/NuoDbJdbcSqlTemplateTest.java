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
package org.jumpmind.db.platform.nuodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NuoDbJdbcSqlTemplateTest {
    private NuoDbJdbcSqlTemplate template;

    @BeforeEach
    void setUp() {
        DataSource dataSource = mock(DataSource.class);
        SqlTemplateSettings settings = mock(SqlTemplateSettings.class);
        template = new NuoDbJdbcSqlTemplate(dataSource, settings, null, new DatabaseInfo());
    }

    @Test
    void testConstructor_setsPrimaryKeyViolationCode() {
        SQLException matching = new SQLException("duplicate value", "23000", -27);
        SQLException nonMatching = new SQLException("other error", "42000", -104);
        assertTrue(template.isUniqueKeyViolation(matching));
        assertFalse(template.isUniqueKeyViolation(nonMatching));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_matchingMessage_returnsIndexName() {
        SQLException ex = new SQLException("unique index MY_IDX, some other detail");
        assertEquals("MY_IDX", template.getUniqueKeyViolationIndexName(ex));
    }

    @Test
    void testGetUniqueKeyViolationIndexName_nonMatchingMessage_returnsNull() {
        SQLException ex = new SQLException("some unrelated failure");
        assertNull(template.getUniqueKeyViolationIndexName(ex));
    }

    @Test
    void testGetSelectLastInsertIdSql_returnsLastInsertIdFromDual() {
        assertEquals("select last_insert_id() from dual", template.getSelectLastInsertIdSql("SEQ_NAME"));
    }

    @Test
    void testVerifyArgType_bitType_mapsToBoolean() {
        assertEquals(Types.BOOLEAN, template.verifyArgType(Boolean.TRUE, Types.BIT));
    }

    @Test
    void testVerifyArgType_nonBitType_fallsBackToSuper() {
        assertEquals(Types.VARCHAR, template.verifyArgType("a", Types.VARCHAR));
    }
}
