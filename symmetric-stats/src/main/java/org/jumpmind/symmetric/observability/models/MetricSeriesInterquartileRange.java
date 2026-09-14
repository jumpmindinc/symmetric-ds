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
package org.jumpmind.symmetric.observability.models;

/**
 * Immutable snapshot of quartile statistics and Tukey fences computed over a window of {@link MetricInterval} values.
 *
 * <ul>
 * <li>{@code q1} — first quartile (25th percentile)</li>
 * <li>{@code q2} — second quartile / median (50th percentile)</li>
 * <li>{@code iqr} — interquartile range: Q3 − Q1</li>
 * <li>{@code lowerFence} — Q1 − k × IQR (Tukey inner lower fence)</li>
 * <li>{@code upperFence} — Q3 + k × IQR (Tukey inner upper fence)</li>
 * </ul>
 *
 * <p>
 * Q3 is not stored directly; it can be recovered as {@code q1 + iqr}.
 */
public record MetricSeriesInterquartileRange(
        double q1,
        double q2,
        double iqr,
        double lowerOutlierFence,
        double upperOutlierFence) {
}
