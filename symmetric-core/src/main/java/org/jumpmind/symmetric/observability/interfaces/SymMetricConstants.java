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

/** Metric ID constants. Referenced by callers that register or look up pre-registered metrics by ID. */
public abstract class SymMetricConstants {
    public static final String OTEL_SCOPE = "symmetricds";
    public static final String METRIC_UNIT_PERCENT = "percent";
    public static final String METRIC_UNIT_CONNECTIONS = "connections";
    public static final String METRIC_UNIT_BYTES = "bytes";
    public static final String METRIC_UNIT_MB = "megabytes";
    public static final String METRIC_UNIT_MILLIS = "milliseconds";
    public static final String METRIC_UNIT_SECONDS = "seconds";
    public static final String METRIC_UNIT_MINUTES = "minutes";
    public static final String METRIC_UNIT_HOURS = "hours";
    public static final String METRIC_UNIT_DAYS = "days";
    public static final String METRIC_UNIT_MONTHS = "months";
    public static final String METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS = OTEL_SCOPE + ".server.reservations.count";
    public static final String METRIC_ID_SERVER_CONNECTIONS_UTILIZATION = OTEL_SCOPE + ".server.connections.utilization";
    public static final String[] DEFAULT_METRIC_IDS = {
            METRIC_ID_SERVER_CONNECTIONS_RESERVATIONS,
            METRIC_ID_SERVER_CONNECTIONS_UTILIZATION
    };
}
