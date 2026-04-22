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
package org.jumpmind.symmetric.observability.repository;

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.service.impl.AbstractSqlMap;

public class MetricsRepositorySqlMap extends AbstractSqlMap {
    public MetricsRepositorySqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        // @formatter:off
        putSql("generateSurrogateSql",
            "INSERT INTO $(metric_key) (metric_key, metric_id, engine_name, hostname, fact_type, create_time, last_update_time)" +
            " SELECT (?*(COALESCE(MAX(metric_key),1)+?))/?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM $(metric_key)");

        putSql("insertMetricKeySql",
            "INSERT INTO $(metric_key) (metric_key, hostname, engine_name, metric_id, fact_type, create_time, last_update_time)" +
            " VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        putSql("updateMetricKeySql",
            "UPDATE $(metric_key) SET metric_key=? WHERE metric_id=? AND engine_name=? AND hostname=? AND fact_type=?");

        putSql("selectMetricKeyByIdSql",
            "SELECT metric_key, metric_id, hostname, engine_name, fact_type FROM $(metric_key)" +
            " WHERE metric_id=? AND hostname=? AND engine_name IN (?, ?) AND fact_type=?");

        putSql("selectMetricKeysByHostnameSql",
            "SELECT metric_key, metric_id, hostname, engine_name, fact_type FROM $(metric_key)" +
            " WHERE hostname = ? AND engine_name IN (?, ?)");

        putSql("selectRecentIntervalsSql",
            "SELECT interval_start, end_time, avg, min, max, std_dev, observation_count, mean" +
            " FROM $(metric_stats_float64)" +
            " WHERE metric_key = ? AND interval_start >= ?" +
            " ORDER BY interval_start");

        putSql("insertMetricIntervalSql",
            "INSERT INTO $(metric_stats_float64)" +
            " (metric_key, interval_start, end_time, avg, min, max, std_dev, observation_count, mean, duration_seconds)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        putSql("selectRecentIntervalsInt64Sql",
            "SELECT interval_start, end_time, avg, min, max, std_dev, observation_count, mean" +
            " FROM $(metric_stats_int64)" +
            " WHERE metric_key = ? AND interval_start >= ?" +
            " ORDER BY interval_start");

        putSql("insertMetricIntervalInt64Sql",
            "INSERT INTO $(metric_stats_int64)" +
            " (metric_key, interval_start, end_time, avg, min, max, std_dev, observation_count, mean, duration_seconds)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        // @formatter:on
    }
}
