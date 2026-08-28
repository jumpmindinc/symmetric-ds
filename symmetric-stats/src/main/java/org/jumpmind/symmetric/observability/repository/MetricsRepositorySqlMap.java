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
            "INSERT INTO $(metric_key) (metric_key, metric_id, engine_name, hostname, fact_type, metric_type, enabled, create_time, last_update_time)" +
            " SELECT (?*(COALESCE(MAX(metric_key),1)+?))/?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp FROM $(metric_key)");

        putSql("insertMetricKeySql",
            "INSERT INTO $(metric_key) (metric_key, hostname, engine_name, metric_id, fact_type, metric_type, enabled, create_time, last_update_time)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)");

        putSql("updateMetricKeySql",
            "UPDATE $(metric_key) SET metric_key=?,fact_type=?,metric_type=?,enabled=?,last_update_time=current_timestamp WHERE metric_id=? AND engine_name=? AND hostname=?");

        putSql("selectMetricKeyByIdSql",
            "SELECT metric_key, metric_id, hostname, engine_name, fact_type, metric_type, enabled FROM $(metric_key)" +
            " WHERE metric_id=? AND hostname=? AND engine_name IN (?, ?)");

        putSql("selectMetricKeysByHostnameSql",
            "SELECT metric_key, metric_id, hostname, engine_name, fact_type, metric_type, enabled FROM $(metric_key)" +
            " WHERE hostname = ? AND engine_name IN (?, ?)");

        putSql("selectRecentIntervalsSql",
            "SELECT interval_start_time, interval_end_millis, avg_value, min_value, max_value, std_dev, observation_count, mean, outlier" +
            " FROM $(metric_stats_float64)" +
            " WHERE metric_key = ? AND interval_start_time >= ?" +
            " ORDER BY interval_start_time");

        putSql("insertMetricIntervalFloat64Sql",
            "INSERT INTO $(metric_stats_float64)" +
            " (metric_key, context_id, interval_start_time, duration_seconds, interval_end_millis, observation_count, min_value, max_value, avg_value, mean, std_dev, outlier)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        putSql("selectRecentIntervalsInt64Sql",
            "SELECT interval_start_time, interval_end_millis, avg_value, min_value, max_value, std_dev, observation_count, mean, outlier" +
            " FROM $(metric_stats_int64)" +
            " WHERE metric_key = ? AND interval_start_time >= ?" +
            " ORDER BY interval_start_time");

        putSql("insertMetricIntervalInt64Sql",
            "INSERT INTO $(metric_stats_int64)" +
            " (metric_key, context_id, interval_start_time, duration_seconds, interval_end_millis, observation_count, min_value, max_value, avg_value, mean, std_dev, outlier)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        putSql("generateContextSurrogateSql",
            "INSERT INTO $(metric_context)" +
            " (context_id, attributes_hash, attr1_name, attr1_value, attr2_name, attr2_value, attr3_name, attr3_value, create_time)" +
            " SELECT (?*(COALESCE(MAX(context_id),1)+?))/?, ?, ?, ?, ?, ?, ?, ?, current_timestamp FROM $(metric_context)");

        putSql("insertMetricContextSql",
            "INSERT INTO $(metric_context)" +
            " (context_id, attributes_hash, attr1_name, attr1_value, attr2_name, attr2_value, attr3_name, attr3_value, create_time)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)");

        putSql("selectMetricContextByHashSql",
            "SELECT context_id, attributes_hash, attr1_name, attr1_value, attr2_name, attr2_value, attr3_name, attr3_value" +
            " FROM $(metric_context) WHERE attributes_hash = ?");

        putSql("purgeMetricStatsFloat64Sql",
            "delete from $(metric_stats_float64) where interval_start_time < ?");

        putSql("purgeMetricStatsInt64Sql",
            "delete from $(metric_stats_int64) where interval_start_time < ?");

        putSql("purgeOrphanedMetricContextsSql",
            "delete from $(metric_context) where context_id not in" +
            " (select context_id from $(metric_stats_float64)" +
            "  union select context_id from $(metric_stats_int64))");
        // @formatter:on
    }
}
