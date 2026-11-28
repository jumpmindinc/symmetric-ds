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

import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.common.TableConstants;

/**
 * Keeps exactly one status index on outgoing_batch. idx_ob_ready_queue leads with status, so it serves every lookup that idx_ob_status serves, and it also
 * covers the ready-queue query. It is only worth its larger size when ready queues are enabled.
 */
public final class OutgoingBatchStatusIndexSelector {
    public static final String IDX_OB_STATUS = "idx_ob_status";
    public static final String IDX_OB_READY_QUEUE = "idx_ob_ready_queue";

    private OutgoingBatchStatusIndexSelector() {
    }

    public static void removeUnusedStatusIndex(Database unprefixedDatabase, boolean isReadyQueuesEnabled) {
        Table outgoingBatch = unprefixedDatabase.findTable(TableConstants.SYM_OUTGOING_BATCH);
        if (outgoingBatch != null) {
            String unusedIndexName = isReadyQueuesEnabled ? IDX_OB_STATUS : IDX_OB_READY_QUEUE;
            IIndex unusedIndex = outgoingBatch.findIndex(unusedIndexName);
            if (unusedIndex != null) {
                outgoingBatch.removeIndex(unusedIndex);
            }
        }
    }
}
