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

import org.jumpmind.symmetric.ISymmetricEngine;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

/**
 * This class collects observations for registered metrics (counters, gauges, histograms). It is intended for engine/node-specific metrics.
 */
public class EngineMetricsService extends AbstractMetricsService implements IEngineMetricsService {
    private final ISymmetricEngine engine;

    public EngineMetricsService(ISymmetricEngine engine, MetricsManager metricsManager, boolean isOtelPublishingEnabled) {
        super(metricsManager, Attributes.of(AttributeKey.stringKey("engine.name"), engine.getEngineName()), isOtelPublishingEnabled);
        this.engine = engine;
        metricsManager.register(this);
    }

    @Override
    public String getEngineName() {
        return engine.getEngineName();
    }

    @Override
    public void shutdown() {
        metricsManager.unregister(this);
    }
}
