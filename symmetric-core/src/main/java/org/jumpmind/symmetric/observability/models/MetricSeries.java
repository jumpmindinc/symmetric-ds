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

/**
 * Represents a row from the {@code metric_key} table: a compact surrogate ID paired with the {@link MetricKey} identity (hostname, engine name, metric ID) it
 * maps to.
 *
 * <p>
 * {@code metricAttrId} is the {@code BIGINT} primary key used in {@code metric_interval} rows to avoid repeating the string-valued identity on every interval
 * record.
 */
public record MetricSeries(long metricAttrId, MetricKey key) {
}
