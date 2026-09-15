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
package org.jumpmind.db.platform.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqliteJdbcSqlTemplateTest {
    private SqliteJdbcSqlTemplate sqlTemplate;
    private PreparedStatement preparedStatementMock;
    private SymmetricLobHandler lobHandlerMock;

    @BeforeEach
    void setUp() {
        DataSource dataSourceMock = mock(DataSource.class);
        sqlTemplate = new SqliteJdbcSqlTemplate(dataSourceMock, new SqlTemplateSettings(), new SymmetricLobHandler(), new DatabaseInfo());
        preparedStatementMock = mock(PreparedStatement.class);
        lobHandlerMock = mock(SymmetricLobHandler.class);
    }

    @Test
    void testSupportsGetGeneratedKeys() {
        assertFalse(sqlTemplate.supportsGetGeneratedKeys());
    }

    @Test
    void testGetSelectLastInsertIdSql() {
        assertEquals("SELECT last_insert_rowid();", sqlTemplate.getSelectLastInsertIdSql("sym_data_seq"));
    }

    @Test
    void testIsUniqueKeyViolation_withConstraintMessage() {
        assertTrue(sqlTemplate.isUniqueKeyViolation(new SQLException("[SQLITE_CONSTRAINT] abort due to constraint violation")));
    }

    @Test
    void testIsUniqueKeyViolation_withPrimaryKeyConstraintMessage() {
        assertTrue(sqlTemplate.isUniqueKeyViolation(new SQLException("[SQLITE_CONSTRAINT_PRIMARYKEY] failed")));
    }

    @Test
    void testIsUniqueKeyViolation_withUniqueConstraintMessage() {
        assertTrue(sqlTemplate.isUniqueKeyViolation(new SQLException("[SQLITE_CONSTRAINT_UNIQUE] failed")));
    }

    @Test
    void testIsUniqueKeyViolation_withWrappedException() {
        assertTrue(sqlTemplate.isUniqueKeyViolation(new RuntimeException(new SQLException("[SQLITE_CONSTRAINT_UNIQUE] failed"))));
    }

    @Test
    void testIsUniqueKeyViolation_withUnrelatedSqlException() {
        assertFalse(sqlTemplate.isUniqueKeyViolation(new SQLException("database is locked")));
    }

    @Test
    void testIsUniqueKeyViolation_withNullMessage() {
        assertFalse(sqlTemplate.isUniqueKeyViolation(new SQLException((String) null)));
    }

    @Test
    void testIsUniqueKeyViolation_withNonSqlException() {
        assertFalse(sqlTemplate.isUniqueKeyViolation(new RuntimeException("boom")));
    }

    @Test
    void testGetObjectFromResultSet_withTimestamp() throws SQLException {
        ResultSet resultSetMock = mock(ResultSet.class);
        when(resultSetMock.getString(1)).thenReturn("2020-01-02 10:15:30.000");
        Timestamp timestamp = sqlTemplate.getObjectFromResultSet(resultSetMock, Timestamp.class);
        assertEquals("2020-01-02 10:15:30.0", timestamp.toString());
    }

    @Test
    void testGetObjectFromResultSet_withDate() throws SQLException {
        ResultSet resultSetMock = mock(ResultSet.class);
        when(resultSetMock.getString(1)).thenReturn("2020-01-02 10:15:30.000");
        Date date = sqlTemplate.getObjectFromResultSet(resultSetMock, Date.class);
        assertEquals("2020-01-02 10:15:30.0", new Timestamp(date.getTime()).toString());
    }

    @Test
    void testGetObjectFromResultSet_withNullTimestamp() throws SQLException {
        ResultSet resultSetMock = mock(ResultSet.class);
        when(resultSetMock.getString(1)).thenReturn(null);
        assertNull(sqlTemplate.getObjectFromResultSet(resultSetMock, Timestamp.class));
    }

    @Test
    void testSetValues_withBlobBytes() throws SQLException {
        byte[] payload = "hello".getBytes(Charset.defaultCharset());
        sqlTemplate.setValues(preparedStatementMock, new Object[] { payload }, new int[] { Types.BLOB }, lobHandlerMock);
        verify(lobHandlerMock).setBlobAsBytes(preparedStatementMock, 1, payload);
    }

    @Test
    void testSetValues_withBlobString() throws SQLException {
        sqlTemplate.setValues(preparedStatementMock, new Object[] { "hello" }, new int[] { Types.BLOB }, lobHandlerMock);
        verify(lobHandlerMock).setBlobAsBytes(preparedStatementMock, 1, "hello".getBytes(Charset.defaultCharset()));
    }

    @Test
    void testSetValues_withClob() throws SQLException {
        sqlTemplate.setValues(preparedStatementMock, new Object[] { "notes" }, new int[] { Types.CLOB }, lobHandlerMock);
        verify(lobHandlerMock).setClobAsString(preparedStatementMock, 1, "notes");
    }

    @Test
    void testSetValues_withDateTruncatesToDay() throws SQLException {
        Object[] args = new Object[] { Timestamp.valueOf("2020-01-02 10:15:30") };
        sqlTemplate.setValues(preparedStatementMock, args, new int[] { Types.DATE }, lobHandlerMock);
        assertEquals("2020-01-02 00:00:00.000", args[0]);
    }

    @Test
    void testSetValues_withTimestampFormatsValue() throws SQLException {
        Object[] args = new Object[] { Timestamp.valueOf("2020-01-02 10:15:30") };
        sqlTemplate.setValues(preparedStatementMock, args, new int[] { Types.TIMESTAMP }, lobHandlerMock);
        assertEquals("2020-01-02 10:15:30.000", args[0]);
    }

    @Test
    void testSetValues_withBigDecimalConvertsToDouble() throws SQLException {
        Object[] args = new Object[] { new BigDecimal("12.50") };
        sqlTemplate.setValues(preparedStatementMock, args, new int[] { Types.DECIMAL }, lobHandlerMock);
        assertEquals(12.5d, args[0]);
    }

    @Test
    void testSetValues_withPlainStringIsUnchanged() throws SQLException {
        Object[] args = new Object[] { "abc" };
        sqlTemplate.setValues(preparedStatementMock, args, new int[] { Types.VARCHAR }, lobHandlerMock);
        assertEquals("abc", args[0]);
    }

    @Test
    void testSetValues_withoutArgTypesFormatsTimestamp() throws SQLException {
        Object[] args = new Object[] { Timestamp.valueOf("2020-01-02 10:15:30") };
        sqlTemplate.setValues(preparedStatementMock, args);
        assertEquals("2020-01-02 10:15:30.000", args[0]);
        verify(preparedStatementMock).setString(1, "2020-01-02 10:15:30.000");
    }

    @Test
    void testSetValues_withNullArgsDoesNothing() throws SQLException {
        sqlTemplate.setValues(preparedStatementMock, null);
        verifyNoInteractions(preparedStatementMock);
    }
}
