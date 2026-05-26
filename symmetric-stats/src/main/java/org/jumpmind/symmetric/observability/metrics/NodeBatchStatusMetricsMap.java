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

import static org.jumpmind.symmetric.observability.interfaces.MetricAttributeConstants.BATCH_STATUS;
import static org.jumpmind.symmetric.observability.interfaces.MetricAttributeConstants.NODE_ID;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jumpmind.symmetric.model.OutgoingBatchSummary;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusGauge;
import org.jumpmind.symmetric.observability.interfaces.INodeBatchStatusMetricsMap;
import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;
import org.jumpmind.symmetric.observability.interfaces.MetricAttribute;
import org.jumpmind.symmetric.observability.interfaces.MetricAttributeList;

/**
 * Keyed collection of {@link NodeBatchStatusGauge} instances, one per (nodeId, batchStatus) pair. Gauge instruments are registered lazily on first access via
 * {@link IEngineMetricsService#registerLongGauge(String, List)}; the metric definitions for {@code batchesMetricId} and {@code rowsMetricId} must already be
 * present in {@code MetricDefinitionFactory} or registration will throw {@code InvalidMetricDataException}.
 */
public class NodeBatchStatusMetricsMap extends AbstractKeyedMetricsMap<NodeBatchStatusGauge> implements INodeBatchStatusMetricsMap {
    static final String ENTRY_KEY_DELIMITER = "&?";
    private final IEngineMetricsService metricsService;
    private final String batchesMetricId;
    private final String rowsMetricId;

    public NodeBatchStatusMetricsMap(IEngineMetricsService metricsService, String batchesMetricId, String rowsMetricId) {
        this.metricsService = metricsService;
        this.batchesMetricId = batchesMetricId;
        this.rowsMetricId = rowsMetricId;
    }

    @Override
    protected String generateEntryKey(NodeBatchStatusGauge entry) {
        return compositeKey(entry.getNodeId(), entry.getBatchStatus());
    }

    private static String compositeKey(String nodeId, String batchStatus) {
        return nodeId + ENTRY_KEY_DELIMITER + batchStatus;
    }

    @Override
    public INodeBatchStatusGauge getOrCreate(String nodeId, String batchStatus) {
        return this.getOrCreate(compositeKey(nodeId, batchStatus), () -> createEntry(nodeId, batchStatus));
    }

    private NodeBatchStatusGauge createEntry(String nodeId, String batchStatus) {
        MetricAttributeList attrs = MetricAttributeList.of(
                new MetricAttribute(NODE_ID, nodeId),
                new MetricAttribute(BATCH_STATUS, batchStatus));
        ISymLongGauge batchesGauge = metricsService.registerLongGauge(batchesMetricId, attrs);
        ISymLongGauge rowsGauge = metricsService.registerLongGauge(rowsMetricId, attrs);
        return new NodeBatchStatusGauge(nodeId, batchStatus, batchesGauge, rowsGauge);
    }

    @Override
    public long getBatchCount(String nodeId, String batchStatus) {
        return get(compositeKey(nodeId, batchStatus)).map(NodeBatchStatusGauge::getBatchCount).orElse(0L);
    }

    @Override
    public long getRowCount(String nodeId, String batchStatus) {
        return get(compositeKey(nodeId, batchStatus)).map(NodeBatchStatusGauge::getRowCount).orElse(0L);
    }

    @Override
    public void setBatchAndRowCounts(String nodeId, String batchStatus, long batchCount, long rowCount) {
        INodeBatchStatusGauge mapEntry = getOrCreate(nodeId, batchStatus);
        mapEntry.getBatchesGauge().setValue(batchCount);
        mapEntry.getRowsGauge().setValue(rowCount);
    }

    @Override
    public void setBatchCount(String nodeId, String batchStatus, long value) {
        INodeBatchStatusGauge mapEntry = getOrCreate(nodeId, batchStatus);
        mapEntry.getBatchesGauge().setValue(value);
    }

    @Override
    public void setRowCount(String nodeId, String batchStatus, long value) {
        INodeBatchStatusGauge mapEntry = getOrCreate(nodeId, batchStatus);
        mapEntry.getRowsGauge().setValue(value);
    }

    @Override
    public void addBatchCount(String nodeId, String batchStatus, long delta) {
        INodeBatchStatusGauge mapEntry = getOrCreate(nodeId, batchStatus);
        mapEntry.getBatchesGauge().add(delta);
    }

    @Override
    public void addRowCount(String nodeId, String batchStatus, long delta) {
        INodeBatchStatusGauge mapEntry = getOrCreate(nodeId, batchStatus);
        mapEntry.getRowsGauge().add(delta);
    }

    @Override
    public void setAllMetrics(List<OutgoingBatchSummary> summaries) {
        Set<String> activeKeys = new HashSet<>();
        for (OutgoingBatchSummary s : summaries) {
            String nodeId = s.getNodeId();
            String batchStatus = s.getStatus().name();
            setBatchAndRowCounts(nodeId, batchStatus, s.getBatchCount(), s.getDataCount());
            activeKeys.add(compositeKey(nodeId, batchStatus));
        }
        for (NodeBatchStatusGauge mapEntry : all()) {
            if (!activeKeys.contains(compositeKey(mapEntry.getNodeId(), mapEntry.getBatchStatus()))) {
                mapEntry.getBatchesGauge().setValue(0L);
                mapEntry.getRowsGauge().setValue(0L);
            }
        }
    }

    @Override
    public void setSpecifiedMetrics(List<OutgoingBatchSummary> summaries) {
        for (OutgoingBatchSummary s : summaries) {
            String nodeId = s.getNodeId();
            String batchStatus = s.getStatus().name();
            setBatchAndRowCounts(nodeId, batchStatus, s.getBatchCount(), s.getDataCount());
        }
    }

    @Override
    public List<INodeBatchStatusGauge> gaugesForNode(String nodeId) {
        return all().stream()
                .filter(g -> nodeId.equals(g.getNodeId()))
                .<INodeBatchStatusGauge> map(g -> g)
                .toList();
    }
}
