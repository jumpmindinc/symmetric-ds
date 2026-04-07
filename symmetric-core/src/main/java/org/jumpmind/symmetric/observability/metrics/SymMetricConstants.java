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

public abstract class SymMetricConstants {
    public static final String OTEL_INSTRUMENTATION_SCOPE = "symmetricds";
    public static final String METRIC_CONNECTIONS_UTILIZATION_ID = OTEL_INSTRUMENTATION_SCOPE + ".node_connections.utilization";
    public static final String METRIC_CONNECTIONS_UTILIZATION_DESC = "Active reservations as a percentage of max concurrent workers";
    public static final String METRIC_CONNECTIONS_UTILIZATION_UNIT = "percent";
    public static final String METRIC_CONNECTIONS_RESERVATIONS_ID = OTEL_INSTRUMENTATION_SCOPE + ".node_connections.reservations";
    public static final String METRIC_CONNECTIONS_RESERVATIONS_DESC = "Active node connection reservations";
    public static final String METRIC_CONNECTIONS_RESERVATIONS_UNIT = "connections";
}
