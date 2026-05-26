/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.observability.metrics;

import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;

/**
 * Holds a pair of {@link ISymLongGauge} instruments — one for batch count and one for row count — scoped to a single (nodeId, batchStatus) combination.
 */
public class NodeBatchStatusGauge implements INodeBatchStatusGauge {
    private final String nodeId;
    private final String batchStatus;
    private final ISymLongGauge batchesGauge;
    private final ISymLongGauge rowsGauge;

    NodeBatchStatusGauge(String nodeId, String batchStatus, ISymLongGauge batchesGauge, ISymLongGauge rowsGauge) {
        this.nodeId = nodeId;
        this.batchStatus = batchStatus;
        this.batchesGauge = batchesGauge;
        this.rowsGauge = rowsGauge;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public String getBatchStatus() {
        return batchStatus;
    }

    @Override
    public ISymLongGauge getBatchesGauge() {
        return batchesGauge;
    }

    @Override
    public ISymLongGauge getRowsGauge() {
        return rowsGauge;
    }

    @Override
    public long getBatchCount() {
        return batchesGauge.getValue();
    }

    @Override
    public long getRowCount() {
        return rowsGauge.getValue();
    }
}
