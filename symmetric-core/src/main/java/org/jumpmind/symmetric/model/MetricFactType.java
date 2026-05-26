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
package org.jumpmind.symmetric.model;

/** Determines which fact table and numeric precision are used when persisting interval statistics. */
public enum MetricFactType {
    /** 64-bit floating-point statistics. Used for gauge-type metrics. Written to {@code metric_stats_float64}. */
    FLOAT64,
    /**
     * 64-bit integer statistics. avg, min, max, mean are truncated to {@code long} at persistence time. Used for counter-type metrics. Written to
     * {@code metric_stats_int64}.
     */
    INT64
}
