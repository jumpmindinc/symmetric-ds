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
package org.jumpmind.symmetric.db;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.db.ase.AseSymmetricDialect;
import org.jumpmind.symmetric.db.db2.Db2SymmetricDialect;
import org.jumpmind.symmetric.db.db2.Db2v9SymmetricDialect;
import org.jumpmind.symmetric.db.db2.Db2zOsSymmetricDialect;
import org.jumpmind.symmetric.db.firebird.Firebird20SymmetricDialect;
import org.jumpmind.symmetric.db.hsqldb2.HsqlDb2SymmetricDialect;
import org.jumpmind.symmetric.db.nuodb.NuoDbSymmetricDialect;
import org.jumpmind.symmetric.db.raima.RaimaSymmetricDialect;
import org.jumpmind.symmetric.db.sqlanywhere.SqlAnywhereSymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisableSyncTriggersNodeIdTest {
    private static final String NODE_ID_WITH_QUOTE = "a'b";
    private static final String ESCAPED_NODE_ID_LITERAL = "'a''b'";
    private IParameterService parameterService;
    private IDatabasePlatform platform;
    private ISqlTransaction transaction;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        DatabaseInfo databaseInfo = new DatabaseInfo();
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(ddlBuilder.getDatabaseInfo()).thenReturn(databaseInfo);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(platform.getDatabaseInfo()).thenReturn(databaseInfo);
        transaction = mock(ISqlTransaction.class);
    }

    @Test
    void hsqlDb2_bindsNodeId() {
        new HsqlDb2SymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, NODE_ID_WITH_QUOTE);
        verify(transaction).prepareAndExecute("CALL sym_set_session('node_value',?)", NODE_ID_WITH_QUOTE);
    }

    @Test
    void hsqlDb2_setsNullNodeValueWhenNodeIdMissing() {
        new HsqlDb2SymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, null);
        verify(transaction).prepareAndExecute("CALL sym_set_session('node_value',null)");
    }

    @Test
    void nuoDb_bindsNodeId() {
        new NuoDbSymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, NODE_ID_WITH_QUOTE);
        verify(transaction).prepareAndExecute("select sym_set_session_variable('sync_node_disabled',?) from dual", NODE_ID_WITH_QUOTE);
    }

    @Test
    void firebird20_bindsNodeId() {
        new Firebird20SymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, NODE_ID_WITH_QUOTE);
        verify(transaction).queryForInt("select rdb$set_context('USER_SESSION','sync_node_disabled',cast(? as varchar(50))) from rdb$database",
                NODE_ID_WITH_QUOTE);
    }

    @Test
    void ase_escapesNodeId() {
        new AseSymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, NODE_ID_WITH_QUOTE);
        verify(transaction).prepareAndExecute("set clientname " + ESCAPED_NODE_ID_LITERAL);
    }

    @Test
    void db2_escapesNodeId() {
        new Db2SymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, NODE_ID_WITH_QUOTE);
        verify(transaction).prepareAndExecute("set sym_source_node_id = " + ESCAPED_NODE_ID_LITERAL);
    }

    @Test
    void db2zOs_escapesNodeId() {
        new Db2zOsSymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, NODE_ID_WITH_QUOTE);
        verify(transaction).prepareAndExecute("set sym_source_node_id = " + ESCAPED_NODE_ID_LITERAL);
    }

    @Test
    void db2v9_escapesNodeId() {
        new Db2v9SymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, NODE_ID_WITH_QUOTE);
        verify(transaction).execute("set sym_sync_node_disabled=" + ESCAPED_NODE_ID_LITERAL);
    }

    @Test
    void raima_escapesNodeId() {
        new RaimaSymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, NODE_ID_WITH_QUOTE);
        verify(transaction).prepareAndExecute("set sync_node_disabled = " + ESCAPED_NODE_ID_LITERAL + ";");
    }

    @Test
    void sqlAnywhere_escapesNodeId() {
        new SqlAnywhereSymmetricDialect(parameterService, platform).disableSyncTriggers(transaction, NODE_ID_WITH_QUOTE);
        verify(transaction).prepareAndExecute("set sync_node_disabled = " + ESCAPED_NODE_ID_LITERAL);
    }
}
