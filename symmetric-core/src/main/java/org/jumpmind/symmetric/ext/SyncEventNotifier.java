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

import java.util.function.Consumer;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.IncomingError;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls every {@link ISyncEventListener} of an engine while the sync.event.enabled parameter is true. A failing listener is logged and never interrupts the
 * batch or load processing that notified it.
 */
public final class SyncEventNotifier {
    private static final Logger log = LoggerFactory.getLogger(SyncEventNotifier.class);

    /**
     * Prevents use of default constructor
     */
    private SyncEventNotifier() {
    }

    public static boolean isEnabled(ISymmetricEngine engine) {
        return engine.getParameterService().is(ParameterConstants.SYNC_EVENT_ENABLED);
    }

    public static void incomingBatchEnded(ISymmetricEngine engine, ISqlTransaction transaction, IncomingBatch batch, IncomingError error) {
        notifyListeners(engine, listener -> listener.incomingBatchEnded(transaction, batch, error));
    }

    public static void outgoingBatchEnded(ISymmetricEngine engine, ISqlTransaction transaction, OutgoingBatch batch) {
        notifyListeners(engine, listener -> listener.outgoingBatchEnded(transaction, batch));
    }

    public static void loadStarted(ISymmetricEngine engine, ISqlTransaction transaction, TableReloadStatus status, String targetNodeId) {
        notifyListeners(engine, listener -> listener.loadStarted(transaction, status, targetNodeId));
    }

    public static void loadTerminated(ISymmetricEngine engine, ISqlTransaction transaction, TableReloadStatus status, String targetNodeId) {
        notifyListeners(engine, listener -> listener.loadTerminated(transaction, status, targetNodeId));
    }

    private static void notifyListeners(ISymmetricEngine engine, Consumer<ISyncEventListener> notification) {
        if (!isEnabled(engine)) {
            return;
        }
        for (ISyncEventListener listener : engine.getExtensionService().getExtensionPointList(ISyncEventListener.class)) {
            try {
                notification.accept(listener);
            } catch (Throwable ex) {
                log.error("Sync event listener " + listener.getClass().getName() + " failed", ex);
            }
        }
    }
}
