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
package org.jumpmind.db.platform.greenplum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GreenplumJdbcSqlTemplateTest {
    private GreenplumJdbcSqlTemplate sqlTemplate;
    private Connection connectionMock;
    private Statement statementMock;
    private PreparedStatement preparedStatementMock;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSourceMock = mock(DataSource.class);
        sqlTemplate = new GreenplumJdbcSqlTemplate(dataSourceMock, new SqlTemplateSettings(), new SymmetricLobHandler(), new DatabaseInfo());
        connectionMock = mock(Connection.class);
        statementMock = mock(Statement.class);
        preparedStatementMock = mock(PreparedStatement.class);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(connectionMock.createStatement()).thenReturn(statementMock);
        when(statementMock.executeQuery("select nextval('sym_data_seq')")).thenReturn(resultSetMock);
        when(resultSetMock.next()).thenReturn(true);
        when(resultSetMock.getLong(1)).thenReturn(99L);
        when(connectionMock.prepareStatement("insert into sym_data (99,?)")).thenReturn(preparedStatementMock);
    }

    @Test
    void testSupportsGetGeneratedKeys() {
        assertFalse(sqlTemplate.supportsGetGeneratedKeys());
    }

    @Test
    void testInsertWithGeneratedKey_readsSequenceAndReturnsKey() throws Exception {
        long key = insertWithGeneratedKey("insert into sym_data (null,?)", "sym_data");
        assertEquals(99L, key);
        verify(statementMock).executeQuery("select nextval('sym_data_seq')");
    }

    @Test
    void testInsertWithGeneratedKey_substitutesKeyForFirstNull() throws Exception {
        insertWithGeneratedKey("insert into sym_data (null,?)", "sym_data");
        verify(connectionMock).prepareStatement("insert into sym_data (99,?)");
        verify(preparedStatementMock).executeUpdate();
    }

    @Test
    void testInsertWithGeneratedKey_closesStatements() throws Exception {
        insertWithGeneratedKey("insert into sym_data (null,?)", "sym_data");
        verify(statementMock).close();
        verify(preparedStatementMock).close();
    }

    @Test
    void testInsertWithGeneratedKey_whenSequenceHasNoRow_returnsZero() throws Exception {
        ResultSet emptyResultSetMock = mock(ResultSet.class);
        when(emptyResultSetMock.next()).thenReturn(false);
        when(statementMock.executeQuery("select nextval('sym_data_seq')")).thenReturn(emptyResultSetMock);
        when(connectionMock.prepareStatement("insert into sym_data (0,?)")).thenReturn(preparedStatementMock);
        assertEquals(0L, insertWithGeneratedKey("insert into sym_data (null,?)", "sym_data"));
    }

    private long insertWithGeneratedKey(String sql, String sequenceName) throws Exception {
        return sqlTemplate.insertWithGeneratedKey(connectionMock, sql, "data_id", sequenceName,
                new Object[] { "value" }, new int[] { Types.VARCHAR });
    }
}
