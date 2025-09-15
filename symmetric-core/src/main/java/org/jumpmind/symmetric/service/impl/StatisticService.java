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
package org.jumpmind.symmetric.service.impl;

import java.sql.Types;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IStatisticService;
import org.jumpmind.symmetric.statistic.ChannelStats;
import org.jumpmind.symmetric.statistic.ChannelStatsByPeriodMap;
import org.jumpmind.symmetric.statistic.HostStats;
import org.jumpmind.symmetric.statistic.HostStatsByPeriodMap;
import org.jumpmind.symmetric.statistic.JobStats;
import org.jumpmind.symmetric.statistic.NodeStatsByPeriodMap;

/**
 * @see IStatisticService
 */
public class StatisticService extends AbstractService implements IStatisticService {
    public StatisticService(IParameterService parameterService, ISymmetricDialect dialect) {
        super(parameterService, dialect);
        setSqlMap(new StatisticServiceSqlMap(symmetricDialect.getPlatform(),
                createSqlReplacementTokens()));
    }

    public void save(ChannelStats stats) {
        sqlTemplate.update(
                getSql("insertChannelStatsSql"),
                new Object[] { stats.getNodeId(), stats.getHostName(), stats.getChannelId(),
                        stats.getStartTime(), stats.getEndTime(), stats.getDataRouted(),
                        stats.getDataUnRouted(), stats.getDataEventInserted(),
                        stats.getDataExtracted(), stats.getDataBytesExtracted(),
                        stats.getDataExtractedErrors(), stats.getDataSent(),
                        stats.getDataBytesSent(), stats.getDataSentErrors(), stats.getDataReceived(),
                        stats.getDataBytesReceived(), stats.getDataLoaded(),
                        stats.getDataBytesLoaded(), stats.getDataLoadedErrors(),
                        stats.getDataLoadedOutgoing(), stats.getDataBytesLoadedOutgoing(),
                        stats.getDataLoadedOutgoingErrors(), stats.getDataMinCreateTime(),
                        stats.getDataMaxCreateTime() }, new int[] {
                                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP,
                                Types.TIMESTAMP, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT,
                                Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT,
                                Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT,
                                Types.TIMESTAMP, Types.TIMESTAMP });
    }

    public void save(JobStats stats) {
        sqlTemplate.update(
                getSql("insertJobStatsSql"),
                new Object[] { stats.getNodeId(), stats.getHostName(), stats.getJobName(),
                        stats.getStartTime(), stats.getEndTime(), stats.getProcessedCount(),
                        stats.getTargetNodeId(), stats.getTargetNodeCount(), stats.isErrorFlag() ? 1 : 0,
                        stats.getErrorMessage() }, new int[] {
                                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP,
                                Types.TIMESTAMP, Types.BIGINT, Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.VARCHAR });
    }

    public List<JobStats> getJobStatsForPeriod(Date start, Date end, String nodeId) {
        return sqlTemplate.query(getSql("selectJobStatsForPeriodSql"), new JobStatsMapper(), start, end, nodeId);
    }

    public List<JobStats> getJobStatsForNode(String nodeId) {
        return sqlTemplate.query(getSql("selectJobStatsForNodeSql"), new JobStatsMapper(), nodeId);
    }

    public TreeMap<Date, Map<String, ChannelStats>> getChannelStatsForPeriod(Date start, Date end,
            String nodeId, int periodSizeInMinutes) {
        List<ChannelStats> list = sqlTemplateDirty.query(getSql("selectChannelStatsSql"),
                new ChannelStatsMapper(), start, end, nodeId);
        return new ChannelStatsByPeriodMap(start, end, list, periodSizeInMinutes);
    }

    public void deleteChannelStatsForPeriod(Date start, Date end, String nodeId) {
        sqlTemplate.update(getSql("deleteChannelStatsSql"), start, end, nodeId);
    }

    public TreeMap<Date, Map<String, ChannelStats>> getNodeStatsForPeriod(Date start, Date end,
            String nodeId, int periodSizeInMinutes) {
        List<ChannelStats> list = sqlTemplateDirty.query(getSql("selectNodeStatsSql"),
                new ChannelStatsMapper(), start, end, nodeId);
        return new NodeStatsByPeriodMap(start, end, list, periodSizeInMinutes);
    }

    public Date getMinNodeStats(String nodeId) {
        return sqlTemplateDirty.queryForObject(getSql("minNodeStatsTimeSql"), Date.class, nodeId);
    }

    public void save(HostStats stats) {
        sqlTemplate.update(
                getSql("insertHostStatsSql"),
                new Object[] { stats.getNodeId(), stats.getHostName(), stats.getStartTime(),
                        stats.getEndTime(), stats.getRestarted(), stats.getNodesPulled(),
                        stats.getNodesPushed(), stats.getNodesRejected(),
                        stats.getNodesRegistered(), stats.getNodesLoaded(),
                        stats.getNodesDisabled(), stats.getPurgedDataRows(),
                        stats.getPurgedDataEventRows(), stats.getPurgedBatchOutgoingRows(),
                        stats.getPurgedBatchIncomingRows(), stats.getPurgedStrandedDataRows(),
                        stats.getPurgedStrandedDataEventRows(), stats.getPurgedExpiredDataRows(),
                        stats.getTriggersCreatedCount(),
                        stats.getTriggersRebuiltCount(), stats.getTriggersRemovedCount(),
                        stats.getTotalNodesPullTime(), stats.getTotalNodesPushTime() },
                new int[] { Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP,
                        Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT,
                        Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT,
                        Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT,
                        Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT });
    }

    public List<HostStats> getHostStatsForPeriod(Date start, Date end, String nodeId) {
        return sqlTemplate.query(getSql("selectHostStatsSql"), new HostStatsMapper(), start, end, nodeId);
    }

    public TreeMap<Date, HostStats> getHostStatsForPeriod(Date start, Date end, String nodeId,
            int periodSizeInMinutes) {
        return new HostStatsByPeriodMap(start, end, getHostStatsForPeriod(start, end, nodeId), periodSizeInMinutes);
    }

    public Date truncateToMinutes(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.SECOND, 0);
        return cal.getTime();
    }

    class JobStatsMapper implements ISqlRowMapper<JobStats> {
        public JobStats mapRow(Row rs) {
            JobStats stats = new JobStats();
            stats.setNodeId(rs.getString("node_id"));
            stats.setHostName(rs.getString("host_name"));
            stats.setJobName(rs.getString("job_name"));
            stats.setStartTime(rs.getDateTime("start_time"));
            stats.setEndTime(rs.getDateTime("end_time"));
            stats.setProcessedCount(rs.getLong("processed_count"));
            stats.setErrorFlag(rs.getBoolean("error_flag"));
            stats.setErrorMessage(rs.getString("error_message"));
            return stats;
        }
    }

    class ChannelStatsMapper implements ISqlRowMapper<ChannelStats> {
        public ChannelStats mapRow(Row rs) {
            ChannelStats stats = new ChannelStats();
            stats.setNodeId(rs.getString("node_id"));
            if (rs.containsKey("host_name")) {
                stats.setHostName(rs.getString("host_name"));
            }
            if (rs.containsKey("channel_id")) {
                stats.setChannelId(rs.getString("channel_id"));
            }
            stats.setStartTime(truncateToMinutes(rs.getDateTime("start_time")));
            stats.setEndTime(truncateToMinutes(rs.getDateTime("end_time")));
            stats.setDataRouted(rs.getLong("data_routed"));
            stats.setDataUnRouted(rs.getLong("data_unrouted"));
            stats.setDataEventInserted(rs.getLong("data_event_inserted"));
            stats.setDataExtracted(rs.getLong("data_extracted"));
            stats.setDataBytesExtracted(rs.getLong("data_bytes_extracted"));
            stats.setDataExtractedErrors(rs.getLong("data_extracted_errors"));
            stats.setDataSent(rs.getLong("data_sent"));
            stats.setDataBytesSent(rs.getLong("data_bytes_sent"));
            stats.setDataSentErrors(rs.getLong("data_sent_errors"));
            stats.setDataReceived(rs.getLong("data_received"));
            stats.setDataBytesReceived(rs.getLong("data_bytes_received"));
            stats.setDataLoaded(rs.getLong("data_loaded"));
            stats.setDataBytesLoaded(rs.getLong("data_bytes_loaded"));
            stats.setDataLoadedErrors(rs.getLong("data_loaded_errors"));
            stats.setDataLoadedOutgoing(rs.getLong("data_loaded_outgoing"));
            stats.setDataLoadedOutgoingErrors(rs.getLong("data_loaded_outgoing_errors"));
            stats.setDataBytesLoadedOutgoing(rs.getLong("data_bytes_loaded_outgoing"));
            stats.setDataMinCreateTime(rs.getDateTime("data_min_create_time"));
            stats.setDataMaxCreateTime(rs.getDateTime("data_max_create_time"));
            return stats;
        }
    }

    class HostStatsMapper implements ISqlRowMapper<HostStats> {
        public HostStats mapRow(Row rs) {
            HostStats stats = new HostStats();
            stats.setNodeId(rs.getString("node_id"));
            stats.setHostName(rs.getString("host_name"));
            stats.setStartTime(truncateToMinutes(rs.getDateTime("start_time")));
            stats.setEndTime(truncateToMinutes(rs.getDateTime("end_time")));
            stats.setRestarted(rs.getLong("restarted"));
            stats.setNodesPulled(rs.getLong("nodes_pulled"));
            stats.setNodesPushed(rs.getLong("nodes_pushed"));
            stats.setNodesRejected(rs.getLong("nodes_rejected"));
            stats.setNodesRegistered(rs.getLong("nodes_registered"));
            stats.setNodesLoaded(rs.getLong("nodes_loaded"));
            stats.setNodesDisabled(rs.getLong("nodes_disabled"));
            stats.setPurgedDataRows(rs.getLong("purged_data_rows"));
            stats.setPurgedDataEventRows(rs.getLong("purged_data_event_rows"));
            stats.setPurgedBatchOutgoingRows(rs.getLong("purged_batch_outgoing_rows"));
            stats.setPurgedBatchIncomingRows(rs.getLong("purged_batch_incoming_rows"));
            stats.setPurgedStrandedDataRows(rs.getLong("purged_stranded_data_rows"));
            stats.setPurgedStrandedDataEventRows(rs.getLong("purged_stranded_event_rows"));
            stats.setPurgedExpiredDataRows(rs.getLong("purged_expired_data_rows"));
            stats.setTriggersCreatedCount(rs.getLong("triggers_created_count"));
            stats.setTriggersRebuiltCount(rs.getLong("triggers_rebuilt_count"));
            stats.setTriggersRemovedCount(rs.getLong("triggers_removed_count"));
            stats.setTotalNodesPullTime(rs.getLong("total_nodes_pull_time"));
            stats.setTotalNodesPushTime(rs.getLong("total_nodes_push_time"));
            return stats;
        }
    }
}