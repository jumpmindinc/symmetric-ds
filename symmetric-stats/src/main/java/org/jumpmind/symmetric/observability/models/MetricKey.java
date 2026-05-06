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
package org.jumpmind.symmetric.observability.models;

import org.jumpmind.symmetric.model.MetricFactType;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants.InstrumentType;
import org.jumpmind.symmetric.observability.repository.SurrogateKeyConstants;

/**
 * Uniquely identifies a metric time-series by host, engine, metric name, fact type, and instrument kind.
 */
public record MetricKey(long key, String hostname, String engineName, String metricId, MetricFactType factType, InstrumentType metricType,
        boolean isEnabled) {
    public boolean isSurrogateKeyMissing() {
        return (key == SurrogateKeyConstants.SURROGATE_KEY_UNASSIGNED);
    }

    public boolean equalsOnCompositeKey(MetricKey other) {
        return other != null && ((this == other)
                || ((hostname != null) && (hostname.equalsIgnoreCase(other.hostname))
                        && (engineName != null) && (engineName.equalsIgnoreCase(other.engineName))
                        && (metricId != null) && (metricId.equalsIgnoreCase(other.metricId))));
    }
}
