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
package org.jumpmind.symmetric.integrate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunSqlReloadListenerTest {
    private static final long LOAD_ID = 77L;
    private RunSqlReloadListener listener;
    private IDataService dataService;
    private ISqlTransaction transaction;
    private Node node;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
        listener = new RunSqlReloadListener();
        listener.setSymmetricEngine(engine);
        transaction = mock(ISqlTransaction.class);
        node = new Node("store-001", "store");
    }

    @Test
    void testBeforeReload_insertsSqlEvent() {
        listener.setSqlToRunAtTargetBeforeReload("delete from item");
        listener.beforeReload(transaction, node, LOAD_ID);
        verify(dataService).insertSqlEvent(transaction, node, "delete from item", true, LOAD_ID, null);
    }

    @Test
    void testBeforeReload_skipsWhenSqlNotSet() {
        listener.beforeReload(transaction, node, LOAD_ID);
        verify(dataService, never()).insertSqlEvent(any(), any(), anyString(), eq(true), anyLong(), any());
    }

    @Test
    void testBeforeReload_skipsWhenSqlIsBlank() {
        listener.setSqlToRunAtTargetBeforeReload("   ");
        listener.beforeReload(transaction, node, LOAD_ID);
        verify(dataService, never()).insertSqlEvent(any(), any(), anyString(), eq(true), anyLong(), any());
    }

    @Test
    void testAfterReload_insertsSqlEvent() {
        listener.setSqlToRunAtTargetAfterReload("update item set x = 1");
        listener.afterReload(transaction, node, LOAD_ID);
        verify(dataService).insertSqlEvent(transaction, node, "update item set x = 1", true, LOAD_ID, null);
    }

    @Test
    void testAfterReload_skipsWhenSqlNotSet() {
        listener.afterReload(transaction, node, LOAD_ID);
        verify(dataService, never()).insertSqlEvent(any(), any(), anyString(), eq(true), anyLong(), any());
    }

    @Test
    void testAfterReload_skipsWhenSqlIsBlank() {
        listener.setSqlToRunAtTargetAfterReload("");
        listener.afterReload(transaction, node, LOAD_ID);
        verify(dataService, never()).insertSqlEvent(any(), any(), anyString(), eq(true), anyLong(), any());
    }

    @Test
    void testBeforeReload_doesNotRunAfterReloadSql() {
        listener.setSqlToRunAtTargetAfterReload("update item set x = 1");
        listener.beforeReload(transaction, node, LOAD_ID);
        verify(dataService, never()).insertSqlEvent(any(), any(), anyString(), eq(true), anyLong(), any());
    }
}
