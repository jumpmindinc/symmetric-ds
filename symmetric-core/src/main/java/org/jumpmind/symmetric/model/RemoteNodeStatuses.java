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
package org.jumpmind.symmetric.model;

import java.util.ArrayList;
import java.util.Map;

import org.jumpmind.exception.InterruptedException;

public class RemoteNodeStatuses extends ArrayList<RemoteNodeStatus> {
    private static final long serialVersionUID = 1L;
    Map<String, Channel> channels;

    public RemoteNodeStatuses(Map<String, Channel> channels) {
        this.channels = channels;
    }

    public boolean wasDataProcessed() {
        boolean dataProcessed = false;
        for (RemoteNodeStatus status : this) {
            dataProcessed |= status.getDataProcessed() > 0;
        }
        return dataProcessed;
    }

    public boolean wasBatchProcessed() {
        boolean batchProcessed = false;
        for (RemoteNodeStatus status : this) {
            batchProcessed |= status.getBatchesProcessed() > 0;
        }
        return batchProcessed;
    }

    public long getDataProcessedCount() {
        long dataProcessed = size() > 0 ? 0 : -1l;
        for (RemoteNodeStatus status : this) {
            dataProcessed += status.getDataProcessed();
        }
        return dataProcessed;
    }

    public boolean errorOccurred() {
        boolean errorOccurred = false;
        for (RemoteNodeStatus status : this) {
            errorOccurred |= status.failed();
        }
        return errorOccurred;
    }

    public RemoteNodeStatus add(String nodeId, String queue) {
        RemoteNodeStatus status = null;
        if (nodeId != null) {
            status = new RemoteNodeStatus(nodeId, queue, channels);
            add(status);
        }
        return status;
    }

    public RemoteNodeStatus add(String nodeId) {
        RemoteNodeStatus status = null;
        if (nodeId != null) {
            status = new RemoteNodeStatus(nodeId, null, channels);
            add(status);
        }
        return status;
    }

    public boolean isComplete() {
        boolean complete = true;
        for (RemoteNodeStatus status : this) {
            complete &= status.isComplete();
        }
        return complete;
    }

    public void waitForComplete(long timeout) {
        long deadline = System.currentTimeMillis() + timeout;
        for (RemoteNodeStatus status : this) {
            long timeLeft = deadline - System.currentTimeMillis();
            try {
                if (timeLeft <= 0 || !status.waitCompleted(timeLeft)) {
                    throw new InterruptedException(String.format(
                            "Timed out after %sms", timeout));
                }
            } catch (java.lang.InterruptedException e) {
                throw new InterruptedException(e);
            }
        }
    }
}
