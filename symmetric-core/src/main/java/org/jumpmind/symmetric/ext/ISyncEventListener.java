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
package org.jumpmind.symmetric.ext;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.IncomingError;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.TableReloadStatus;

/**
 * Notified when a batch reaches a final status or a load starts or terminates, but only while the sync.event.enabled parameter is true. Every extension point
 * of this type is notified, synchronously on the thread that is processing the batch or load and sometimes inside its transaction, so an implementation must
 * return quickly and must not block. The transaction is the open transaction the status change was written in, or null when the status was written outside of
 * one.
 */
public interface ISyncEventListener extends IExtensionPoint {
    /**
     * @param error
     *            the row that failed the batch, or null when the batch was successful or the failing row is unknown
     */
    public void incomingBatchEnded(ISqlTransaction transaction, IncomingBatch batch, IncomingError error);

    public void outgoingBatchEnded(ISqlTransaction transaction, OutgoingBatch batch);

    /**
     * Called on both the source node and the target node of a load. The source node is notified when the load has been set up, and the target node when the
     * status of the load arrives.
     */
    public void loadStarted(ISqlTransaction transaction, TableReloadStatus status, String targetNodeId);

    /**
     * Called on both the source node and the target node of a load when the load completes or is cancelled.
     */
    public void loadTerminated(ISqlTransaction transaction, TableReloadStatus status, String targetNodeId);
}
