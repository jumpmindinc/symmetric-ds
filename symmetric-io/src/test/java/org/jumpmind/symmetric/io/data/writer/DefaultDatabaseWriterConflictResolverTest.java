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
package org.jumpmind.symmetric.io.data.writer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.io.data.DataContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultDatabaseWriterConflictResolverTest {
    private static final String DISABLE_NODE_SQL = "select set_config('symmetric.node_disabled', ?, false)";
    private DefaultDatabaseWriterConflictResolver resolver;
    private IDatabasePlatform platform;
    private DefaultDatabaseWriter databaseWriter;
    private ISqlTransaction abortedRecoveryTransaction;
    private DataContext context;

    @BeforeEach
    void setUp() {
        resolver = new DefaultDatabaseWriterConflictResolver();
        platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        DatabaseInfo databaseInfo = new DatabaseInfo();
        databaseInfo.setRequiresSavePointsInTransaction(true);
        when(platform.getDatabaseInfo()).thenReturn(databaseInfo);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        abortedRecoveryTransaction = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction(true)).thenReturn(abortedRecoveryTransaction);
        context = new DataContext();
        context.put(AbstractDatabaseWriter.TRANSACTION_ABORTED, Boolean.TRUE);
        databaseWriter = mock(DefaultDatabaseWriter.class);
        when(databaseWriter.getContext()).thenReturn(context);
    }

    @Test
    void prepareAndExecute_bindsSourceNodeIdWhenTransactionAborted() {
        String sourceNodeId = "a'b";
        context.put("sourceNodeId", sourceNodeId);
        resolver.prepareAndExecute(platform, databaseWriter, "delete from t");
        verify(abortedRecoveryTransaction).prepareAndExecute(DISABLE_NODE_SQL, sourceNodeId);
    }

    @Test
    void prepareAndExecute_bindsEmptyNodeIdWhenSourceNodeIdMissing() {
        resolver.prepareAndExecute(platform, databaseWriter, "delete from t");
        verify(abortedRecoveryTransaction).prepareAndExecute(DISABLE_NODE_SQL, "");
    }
}
