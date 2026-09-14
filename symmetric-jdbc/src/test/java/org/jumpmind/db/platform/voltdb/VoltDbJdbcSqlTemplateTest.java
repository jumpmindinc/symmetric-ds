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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoltDbJdbcSqlTemplateTest {
    private SqlTemplateSettings settings;
    private DataSource dataSource;
    private VoltDbJdbcSqlTemplate template;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        settings = new SqlTemplateSettings();
        template = new VoltDbJdbcSqlTemplate(dataSource, settings, null, new DatabaseInfo());
    }

    @Test
    void testConstructor_forcesScrollInsensitiveResultSets() {
        assertEquals(ResultSet.TYPE_SCROLL_INSENSITIVE, settings.getResultSetType());
    }

    @Test
    void testConstructor_forcesSerializableIsolation() {
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, settings.getOverrideIsolationLevel());
    }

    @Test
    void testIsUniqueKeyViolation() {
        assertTrue(template.isUniqueKeyViolation(new SQLException("VOLTDB ERROR: Constraint Type UNIQUE violated")));
    }

    @Test
    void testIsUniqueKeyViolation_withUnrelatedMessage() {
        assertFalse(template.isUniqueKeyViolation(new SQLException("something else went wrong")));
    }

    @Test
    void testIsUniqueKeyViolation_withNoSqlExceptionInChain() {
        assertFalse(template.isUniqueKeyViolation(new RuntimeException("no sql exception here")));
    }

    @Test
    void testGetIsolationLevel() {
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, template.getIsolationLevel());
    }

    @Test
    void testStartSqlTransaction() throws SQLException {
        Connection connectionMock = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connectionMock);
        ISqlTransaction transaction = template.startSqlTransaction();
        assertTrue(transaction instanceof org.jumpmind.db.sql.JdbcSqlTransaction);
    }

    @Test
    void testSupportsGetGeneratedKeys() {
        assertFalse(template.supportsGetGeneratedKeys());
    }

    @Test
    void testSetTinyIntValue_withInteger() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        template.setTinyIntValue(ps, 1, Integer.valueOf(7), Types.TINYINT);
        verify(ps).setObject(1, Byte.valueOf((byte) 7), Types.TINYINT);
    }

    @Test
    void testSetTinyIntValue_withNonInteger() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        template.setTinyIntValue(ps, 1, Byte.valueOf((byte) 7), Types.TINYINT);
        verify(ps).setObject(1, Byte.valueOf((byte) 7), Types.TINYINT);
    }

    @Test
    void testVerifyArgType_mapsCharToVarchar() {
        assertEquals(Types.VARCHAR, template.verifyArgType("a", Types.CHAR));
    }

    @Test
    void testVerifyArgType_mapsByTheArgumentClass() {
        assertEquals(Types.TINYINT, template.verifyArgType(Byte.valueOf((byte) 1), Types.OTHER));
        assertEquals(Types.SMALLINT, template.verifyArgType(Short.valueOf((short) 1), Types.OTHER));
        assertEquals(Types.INTEGER, template.verifyArgType(Integer.valueOf(1), Types.OTHER));
        assertEquals(Types.BIGINT, template.verifyArgType(Long.valueOf(1L), Types.OTHER));
        assertEquals(Types.DOUBLE, template.verifyArgType(Double.valueOf(1d), Types.OTHER));
        assertEquals(Types.DECIMAL, template.verifyArgType(BigDecimal.ONE, Types.OTHER));
        assertEquals(Types.TIMESTAMP, template.verifyArgType(new Timestamp(0L), Types.OTHER));
    }

    @Test
    void testVerifyArgType_fallsBackToSuperForUnmappedTypes() {
        assertEquals(Types.VARCHAR, template.verifyArgType("a", Types.VARCHAR));
    }

    @Test
    void testInsertWithGeneratedKey_readsNextDataIdAndSubstitutesIt() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select max(data_id)+1 from sym_data")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(42L);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        long key = template.insertWithGeneratedKey(connection, "insert into sym_data (null, 'x')", "data_id", "seq", new Object[0], new int[0]);
        assertEquals(42L, key);
        verify(connection).prepareStatement("insert into sym_data (42, 'x')");
        verify(ps).executeUpdate();
    }

    @Test
    void testInsertWithGeneratedKey_whenNoRowIsReturned() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        when(connection.prepareStatement(anyString())).thenReturn(ps);
        long key = template.insertWithGeneratedKey(connection, "insert into sym_data (null, 'x')", "data_id", "seq", new Object[0], new int[0]);
        assertEquals(0L, key);
        verify(ps).setQueryTimeout(anyInt());
    }
}
