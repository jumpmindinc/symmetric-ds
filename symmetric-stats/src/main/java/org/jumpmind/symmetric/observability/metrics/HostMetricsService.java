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
package org.jumpmind.symmetric.observability.metrics;

import io.opentelemetry.api.common.Attributes;

/**
 * This class is intended for collecting a small number of in-memory metrics, which are not engine-specific, but rather describe the host (server) as a whole.
 */
class HostMetricsService extends AbstractMetricsService {
    HostMetricsService(MetricsManager metricsManager, boolean isOtelPublishingEnabled) {
        super(metricsManager, Attributes.empty(), isOtelPublishingEnabled);
        log.debug("Started Host metrics service");
    }

    @Override
    public void saveCompletedIntervalStats() {
        // Host-level metrics are in-memory only; no engine database to persist to.
    }

    @Override
    public void shutdown() {
        log.info("Host metrics service shut down");
    }
}
