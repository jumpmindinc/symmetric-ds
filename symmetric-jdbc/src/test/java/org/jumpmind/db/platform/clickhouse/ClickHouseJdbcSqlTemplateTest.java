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
package org.jumpmind.db.platform.clickhouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SymmetricLobHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClickHouseJdbcSqlTemplateTest {
    private DataSource dataSource;
    private Connection connection;
    private ClickHouseJdbcSqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        sqlTemplate = new ClickHouseJdbcSqlTemplate(dataSource, new SqlTemplateSettings(), new SymmetricLobHandler(), new DatabaseInfo());
    }

    @Test
    void testIsolationLevel_isNoneBecauseTheDriverCannotChangeIt() {
        assertEquals(Connection.TRANSACTION_NONE, sqlTemplate.getIsolationLevel());
    }

    @Test
    void testStartSqlTransaction_neverTurnsOffAutoCommit() throws Exception {
        ISqlTransaction transaction = sqlTemplate.startSqlTransaction();
        verify(connection, never()).setAutoCommit(false);
        transaction.close();
    }

    @Test
    void testStartSqlTransaction_withAutoCommitFalse_stillNeverTurnsOffAutoCommit() throws Exception {
        ISqlTransaction transaction = sqlTemplate.startSqlTransaction(false);
        verify(connection, never()).setAutoCommit(false);
        transaction.close();
    }

    @Test
    void testCommitAndRollback_doNotCallTheDriver() throws Exception {
        ISqlTransaction transaction = sqlTemplate.startSqlTransaction();
        transaction.commit();
        verify(connection, never()).commit();
        transaction.rollback();
        verify(connection, never()).rollback();
        transaction.close();
    }

    @Test
    void testRequiresAutoCommitFalseToSetFetchSize_isFalse() {
        assertFalse(sqlTemplate.isRequiresAutoCommitFalseToSetFetchSize());
    }

    @Test
    void testAllowsNullForIdentityColumn_returnsFalse() {
        assertFalse(sqlTemplate.allowsNullForIdentityColumn());
    }

    @Test
    void testGetSelectLastInsertIdSql_isUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> sqlTemplate.getSelectLastInsertIdSql("sym_data_data_id"));
    }
}
