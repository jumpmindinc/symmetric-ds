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

import static org.jumpmind.symmetric.db.OutgoingBatchStatusIndexSelector.IDX_OB_READY_QUEUE;
import static org.jumpmind.symmetric.db.OutgoingBatchStatusIndexSelector.IDX_OB_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.TableConstants;
import org.junit.jupiter.api.Test;

class OutgoingBatchStatusIndexSelectorTest {
    private static final String IDX_OB_NODE_STATUS = "idx_ob_node_status";

    @Test
    void testReadyQueueIndexKeptWhenReadyQueuesEnabled() {
        Table outgoingBatch = buildOutgoingBatchWithStatusIndexes();
        OutgoingBatchStatusIndexSelector.removeUnusedStatusIndex(buildDatabase(outgoingBatch), true);
        assertNotNull(outgoingBatch.findIndex(IDX_OB_READY_QUEUE));
        assertNull(outgoingBatch.findIndex(IDX_OB_STATUS));
        assertNotNull(outgoingBatch.findIndex(IDX_OB_NODE_STATUS));
    }

    @Test
    void testStatusIndexKeptWhenReadyQueuesDisabled() {
        Table outgoingBatch = buildOutgoingBatchWithStatusIndexes();
        OutgoingBatchStatusIndexSelector.removeUnusedStatusIndex(buildDatabase(outgoingBatch), false);
        assertNotNull(outgoingBatch.findIndex(IDX_OB_STATUS));
        assertNull(outgoingBatch.findIndex(IDX_OB_READY_QUEUE));
        assertNotNull(outgoingBatch.findIndex(IDX_OB_NODE_STATUS));
    }

    @Test
    void testOtherTablesUnchanged() {
        Table incomingBatch = new Table(TableConstants.SYM_INCOMING_BATCH);
        incomingBatch.addIndex(new NonUniqueIndex(IDX_OB_STATUS));
        OutgoingBatchStatusIndexSelector.removeUnusedStatusIndex(buildDatabase(incomingBatch), true);
        assertEquals(1, incomingBatch.getIndexCount());
    }

    @Test
    void testMissingReadyQueueIndexTolerated() {
        Table outgoingBatch = new Table(TableConstants.SYM_OUTGOING_BATCH);
        outgoingBatch.addIndex(new NonUniqueIndex(IDX_OB_STATUS));
        OutgoingBatchStatusIndexSelector.removeUnusedStatusIndex(buildDatabase(outgoingBatch), false);
        assertNotNull(outgoingBatch.findIndex(IDX_OB_STATUS));
    }

    private static Table buildOutgoingBatchWithStatusIndexes() {
        Table outgoingBatch = new Table(TableConstants.SYM_OUTGOING_BATCH);
        outgoingBatch.addIndex(new NonUniqueIndex(IDX_OB_NODE_STATUS));
        outgoingBatch.addIndex(new NonUniqueIndex(IDX_OB_STATUS));
        outgoingBatch.addIndex(new NonUniqueIndex(IDX_OB_READY_QUEUE));
        return outgoingBatch;
    }

    private static Database buildDatabase(Table table) {
        Database database = new Database();
        database.addTable(table);
        return database;
    }
}
