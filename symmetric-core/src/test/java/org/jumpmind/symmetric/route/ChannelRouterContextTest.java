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
package org.jumpmind.symmetric.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChannelRouterContextTest {
    private ISqlTransaction transaction;
    private IBatchAlgorithm batchAlgorithm;
    private ChannelRouterContext context;

    @BeforeEach
    void setUp() {
        transaction = mock(ISqlTransaction.class);
        batchAlgorithm = mock(IBatchAlgorithm.class);
        context = new ChannelRouterContext("store-1", new NodeChannel("default"), transaction, batchAlgorithm);
    }

    @Test
    void testConstructor_putsTransactionInBatchMode() {
        verify(transaction).setInBatchMode(true);
    }

    @Test
    void testGetDataEventList_isEmptyInitially() {
        assertTrue(context.getDataEventList().isEmpty());
    }

    @Test
    void testAddDataEvent_recordsEvent() {
        context.addDataEvent(1, 100);
        assertEquals(1, context.getDataEventList().size());
    }

    @Test
    void testClearDataEventsList() {
        context.addDataEvent(1, 100);
        context.clearDataEventsList();
        assertTrue(context.getDataEventList().isEmpty());
    }

    @Test
    void testCommit_countsDistinctDataIds() {
        context.addDataEvent(1, 100);
        context.addDataEvent(1, 101);
        context.addDataEvent(2, 100);
        context.commit();
        assertEquals(2, context.getCommittedDataIdCount());
    }

    @Test
    void testCommit_countsEveryDataEvent() {
        context.addDataEvent(1, 100);
        context.addDataEvent(1, 101);
        context.commit();
        assertEquals(2, context.getCommittedDataEventCount());
    }

    @Test
    void testCommit_commitsUnderlyingTransaction() {
        context.commit();
        verify(transaction).commit();
    }

    @Test
    void testCommit_clearsPendingState() {
        context.addDataEvent(1, 100);
        context.setEncountedTransactionBoundary(true);
        context.commit();
        assertTrue(context.getDataEventList().isEmpty());
        assertFalse(context.isEncountedTransactionBoundary());
    }

    @Test
    void testCommit_recordsCommittedDataIds() {
        context.addDataEvent(1, 100);
        context.commit();
        assertEquals(1, context.getDataIds().size());
    }

    @Test
    void testAddData_recordsDistinctIdsWithoutEvents() {
        context.addData(1);
        context.addData(1);
        context.addData(2);
        context.commit();
        assertEquals(2, context.getCommittedDataIdCount());
        assertEquals(0, context.getCommittedDataEventCount());
    }

    @Test
    void testRollback_rollsBackUnderlyingTransaction() {
        context.rollback();
        verify(transaction).rollback();
    }

    @Test
    void testRollback_clearsPendingStateEvenWhenTransactionFails() {
        context.addDataEvent(1, 100);
        doThrow(new SqlException("boom")).when(transaction).rollback();
        context.rollback();
        assertTrue(context.getDataEventList().isEmpty());
    }

    @Test
    void testCleanup_commitsAndClosesTransaction() {
        context.cleanup();
        verify(transaction).commit();
        verify(transaction).close();
    }

    @Test
    void testCleanup_closesTransactionEvenWhenCommitFails() {
        doThrow(new SqlException("boom")).when(transaction).commit();
        assertThrows(SqlException.class, () -> context.cleanup());
        verify(transaction).close();
    }

    @Test
    void testIsNeedsCommitted_isFalseByDefault() {
        assertFalse(context.isNeedsCommitted());
    }

    @Test
    void testSetNeedsCommitted() {
        context.setNeedsCommitted(true);
        assertTrue(context.isNeedsCommitted());
    }

    @Test
    void testAddUsedDataRouter_recordsRouter() {
        IDataRouter router = mock(IDataRouter.class);
        context.addUsedDataRouter(router);
        assertTrue(context.getUsedDataRouters().contains(router));
    }

    @Test
    void testAddTimesByRouter_accumulatesPerRouter() {
        context.addTimesByRouter("router-1", 10);
        context.addTimesByRouter("router-1", 5);
        context.addTimesByRouter("router-2", 3);
        assertEquals(15L, context.getTimesByRouter().get("router-1"));
        assertEquals(3L, context.getTimesByRouter().get("router-2"));
    }

    @Test
    void testIsForceNonCommon_isFalseByDefault() {
        assertFalse(context.isForceNonCommon());
    }

    @Test
    void testSetForceNonCommon_isClearedOnCommit() {
        context.setForceNonCommon(true);
        context.commit();
        assertFalse(context.isForceNonCommon());
    }

    @Test
    void testGetBatchesByNodes_isEmptyInitially() {
        assertTrue(context.getBatchesByNodes().isEmpty());
    }

    @Test
    void testGetAvailableNodes_isEmptyInitially() {
        assertTrue(context.getAvailableNodes().isEmpty());
    }

    @Test
    void testInheritsSourceNodeIdFromSimpleRouterContext() {
        assertEquals("store-1", context.getSourceNodeId());
    }

    @Test
    void testIsBatchComplete_delegatesToBatchAlgorithm() {
        OutgoingBatch batch = new OutgoingBatch();
        DataMetaData dataMetaData = mock(DataMetaData.class);
        when(batchAlgorithm.isBatchComplete(batch, dataMetaData, context)).thenReturn(true);
        assertTrue(context.isBatchComplete(batch, dataMetaData));
    }
}
