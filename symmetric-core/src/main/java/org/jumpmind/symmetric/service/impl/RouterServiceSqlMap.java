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
package org.jumpmind.symmetric.service.impl;

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;

public class RouterServiceSqlMap extends AbstractSqlMap {
    public RouterServiceSqlMap(IDatabasePlatform platform, Map<String, String> replacementTokens) {
        super(platform, replacementTokens);
        putSql("selectChannelsUsingGapsSql", "select distinct channel_id from $(data) where $(dataRange)");
        putSql("selectChannelsUsingStartDataId", "select distinct channel_id from $(data) where data_id >= ?");
        putSql("selectChannelDataCreateTimeRangeUsingGapsSql",
                "select channel_id, min(create_time) as min_create_time, max(create_time) as max_create_time "
                        + "from $(data) where $(dataRange) group by channel_id");
        putSql("selectChannelDataCreateTimeRangeUsingStartDataId",
                "select channel_id, min(create_time) as min_create_time, max(create_time) as max_create_time "
                        + "from $(data) where data_id >= ? group by channel_id");
        putSql("selectDataUsingGapsSql",
                "select $(selectDataUsingGapsSqlHint) d.data_id, d.table_name, d.event_type, d.row_data as row_data, d.pk_data as pk_data, d.old_data as old_data, "
                        + " d.create_time, d.trigger_hist_id, d.channel_id, d.transaction_id, d.source_node_id, d.external_data, d.node_list, d.is_prerouted "
                        + " from $(data) d where d.channel_id=? $(dataRange) ");
        putSql("selectDataUsingStartDataId",
                "select $(selectDataUsingStartDataIdHint) d.data_id, d.table_name, d.event_type, d.row_data as row_data, d.pk_data as pk_data, d.old_data as old_data, "
                        + " d.create_time, d.trigger_hist_id, d.channel_id, d.transaction_id, d.source_node_id, d.external_data, d.node_list, d.is_prerouted "
                        + " from $(data) d where d.channel_id=? and data_id >= ? ");
        putSql("orderByDataId", " order by d.data_id asc ");
        putSql("orderByCreateTime", " order by d.create_time asc, d.data_id asc ");
        putSql("selectDistinctDataIdFromDataEventUsingGapsSql",
                "select distinct data_id from $(data_event) where data_id >=? and data_id <= ? order by data_id asc ");
        putSql("selectUnroutedCountForChannelSql", "select count(*) from $(data) where channel_id=? and data_id >=? ");
        putSql("selectLastDataIdRoutedUsingDataGapSql", "select max(start_id) from $(data_gap) ");
        putSql("selectOracleNextValueSql", "select nextvalue from gv$_sequences where sequence_name = ?");
    }
}