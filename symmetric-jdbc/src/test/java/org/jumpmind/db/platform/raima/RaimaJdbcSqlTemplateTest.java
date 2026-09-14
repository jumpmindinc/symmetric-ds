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
package org.jumpmind.db.platform.raima;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.JdbcSqlTransaction;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RaimaJdbcSqlTemplateTest {
    private RaimaJdbcSqlTemplate template;
    private DataSource dataSource;
    private SqlTemplateSettings settings;

    @BeforeEach
    void setup() {
        dataSource = mock(DataSource.class);
        settings = mock(SqlTemplateSettings.class);
        template = new RaimaJdbcSqlTemplate(dataSource, settings, null, new DatabaseInfo());
    }

    @Test
    void testConstructor_setsReadCommittedIsolationLevel() {
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, template.getIsolationLevel());
    }

    @Test
    void testConstructor_setsPrimaryKeyViolationSqlState() {
        SQLException matchingState = new SQLException("duplicate key", "40002");
        assertTrue(template.isUniqueKeyViolation(matchingState));
    }

    @Test
    void testIsUniqueKeyViolation_nonMatchingSqlState_returnsFalse() {
        SQLException nonMatchingState = new SQLException("some other error", "23000");
        assertFalse(template.isUniqueKeyViolation(nonMatchingState));
    }

    @Test
    void testVerifyArgType_numericMapsToDecimal() {
        assertEquals(Types.DECIMAL, template.verifyArgType(new BigDecimal("1.0"), Types.NUMERIC));
    }

    @Test
    void testVerifyArgType_otherTypesDelegateToSuper() {
        assertEquals(Types.VARCHAR, template.verifyArgType("some value", Types.VARCHAR));
    }

    @Test
    void testVerifyArgType_bigIntegerWithIntegerType_stillDelegatesToSuperLogic() {
        assertEquals(Types.DECIMAL, template.verifyArgType(new BigInteger("100"), Types.INTEGER));
    }

    @Test
    void testGetSelectLastInsertIdSql_returnsFixedSql() {
        assertEquals("select last_insert_id()", template.getSelectLastInsertIdSql("any_sequence"));
    }

    @Test
    void testGetSelectLastInsertIdSql_ignoresSequenceNameArgument() {
        assertEquals(template.getSelectLastInsertIdSql("seq_a"), template.getSelectLastInsertIdSql("seq_b"));
    }

    @Test
    void testStartSqlTransaction_returnsAutoCommitJdbcSqlTransaction() throws SQLException {
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        ISqlTransaction transaction = template.startSqlTransaction();
        assertTrue(transaction instanceof JdbcSqlTransaction);
        verify(connection).setAutoCommit(true);
    }
}
