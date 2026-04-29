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
package org.jumpmind.symmetric.observability.interfaces;

import java.util.List;

import org.jumpmind.symmetric.model.OutgoingBatchSummary;

/**
 * Keyed collection of {@link INodeBatchStatusGauge} instances, one per (nodeId, batchStatus) pair. Obtain via
 * {@link IEngineMetricsService#createNodeBatchStatusMetricsMap}.
 */
public interface INodeBatchStatusMetricsMap {
    /** Returns the gauge entry for (nodeId, batchStatus), creating and registering gauge instruments on first access. */
    INodeBatchStatusGauge getOrCreate(String nodeId, String batchStatus);

    /** Returns the current batch count for (nodeId, batchStatus), or 0 if no entry exists. */
    long getBatchCount(String nodeId, String batchStatus);

    /** Returns the current row count for (nodeId, batchStatus), or 0 if no entry exists. */
    long getRowCount(String nodeId, String batchStatus);

    /** Sets both batch count and row count for (nodeId, batchStatus) in a single call, creating the entry if absent. */
    void setBatchAndRowCounts(String nodeId, String batchStatus, long batchCount, long rowCount);

    /** Sets the batch count gauge for (nodeId, batchStatus), creating the entry if absent. */
    void setBatchCount(String nodeId, String batchStatus, long value);

    /** Sets the row count gauge for (nodeId, batchStatus), creating the entry if absent. */
    void setRowCount(String nodeId, String batchStatus, long value);

    /** Adds {@code delta} to the batch count gauge for (nodeId, batchStatus), creating the entry if absent. */
    void addBatchCount(String nodeId, String batchStatus, long delta);

    /** Adds {@code delta} to the row count gauge for (nodeId, batchStatus), creating the entry if absent. */
    void addRowCount(String nodeId, String batchStatus, long delta);

    /**
     * Snapshot update from a batch summary list: sets values for all gauges (nodeId+status). Either from {@code summaries} or zeros for any previously-seen
     * pair (specific to current hostname/cluster member).
     */
    void setAllMetrics(List<OutgoingBatchSummary> summaries);

    /**
     * Updates metrics from a batch summary list: sets values for gauges (nodeId+status) specified in {@code summaries}.
     */
    void setSpecifiedMetrics(List<OutgoingBatchSummary> summaries);

    /** Returns all gauge entries whose nodeId matches the given value. */
    List<? extends INodeBatchStatusGauge> gaugesForNode(String nodeId);
}
