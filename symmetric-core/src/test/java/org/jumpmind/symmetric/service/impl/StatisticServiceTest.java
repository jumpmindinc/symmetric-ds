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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ColumnNotFoundException;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.ChannelStats;
import org.jumpmind.symmetric.statistic.HostStats;
import org.jumpmind.symmetric.statistic.JobStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticServiceTest {
    private static final Date START_TIME = Timestamp.valueOf("2023-11-14 17:13:20.456");
    private static final Date END_TIME = Timestamp.valueOf("2023-11-14 17:14:59.999");
    private ISqlTemplate sqlTemplate;
    private ISqlTemplate sqlTemplateDirty;
    private StatisticService statisticService;

    @BeforeEach
    void setUp() {
        sqlTemplate = mock(ISqlTemplate.class);
        sqlTemplateDirty = mock(ISqlTemplate.class);
        IParameterService parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        statisticService = new StatisticService(parameterService, newSymmetricDialect());
    }

    @Test
    void testTruncateToMinutes_dropsSecondsAndMilliseconds() {
        assertEquals(Timestamp.valueOf("2023-11-14 17:13:00"), new Timestamp(statisticService.truncateToMinutes(START_TIME).getTime()));
    }

    @Test
    void testSave_channelStatsWritesOneRow() {
        ChannelStats stats = new ChannelStats("store-001", "localhost", START_TIME, END_TIME, "default");
        statisticService.save(stats);
        verify(sqlTemplate).update(eq(sqlFor("insertChannelStatsSql")), any(Object[].class), any(int[].class));
    }

    @Test
    void testSave_jobStatsWritesOneRow() {
        statisticService.save(new JobStats("Routing", START_TIME.getTime(), END_TIME.getTime(), 5L));
        verify(sqlTemplate).update(eq(sqlFor("insertJobStatsSql")), any(Object[].class), any(int[].class));
    }

    @Test
    void testSave_hostStatsWritesOneRow() {
        statisticService.save(new HostStats("store-001", "localhost", START_TIME, END_TIME));
        verify(sqlTemplate).update(eq(sqlFor("insertHostStatsSql")), any(Object[].class), any(int[].class));
    }

    @Test
    void testGetJobStatsForNode_readsThroughTheTemplate() {
        when(sqlTemplate.<JobStats> query(anyString(), any(), any(Object[].class))).thenReturn(Collections.<JobStats> emptyList());
        assertTrue(statisticService.getJobStatsForNode("store-001").isEmpty());
    }

    @Test
    void testGetMinNodeStats_readsThroughTheDirtyTemplate() {
        when(sqlTemplateDirty.queryForObject(sqlFor("minNodeStatsTimeSql"), Date.class, "store-001")).thenReturn(START_TIME);
        assertEquals(START_TIME, statisticService.getMinNodeStats("store-001"));
    }

    @Test
    void testGetChannelStatsForPeriod_bucketsTheWindowOnFiveMinuteBoundaries() {
        when(sqlTemplateDirty.<ChannelStats> query(anyString(), any(), any(Object[].class))).thenReturn(Collections.<ChannelStats> emptyList());
        TreeMap<Date, Map<String, ChannelStats>> byPeriod = statisticService.getChannelStatsForPeriod(START_TIME, END_TIME, "store-001", 5);
        assertEquals(1, byPeriod.size());
        assertEquals(Timestamp.valueOf("2023-11-14 17:10:00"), new Timestamp(byPeriod.firstKey().getTime()));
        assertTrue(byPeriod.firstEntry().getValue().isEmpty());
    }

    @Test
    void testGetNodeStatsForPeriod_bucketsTheWindowOnFiveMinuteBoundaries() {
        when(sqlTemplateDirty.<ChannelStats> query(anyString(), any(), any(Object[].class))).thenReturn(Collections.<ChannelStats> emptyList());
        assertEquals(1, statisticService.getNodeStatsForPeriod(START_TIME, END_TIME, "store-001", 5).size());
    }

    @Test
    void testDeleteChannelStatsForPeriod() {
        statisticService.deleteChannelStatsForPeriod(START_TIME, END_TIME, "store-001");
        verify(sqlTemplate).update(sqlFor("deleteChannelStatsSql"), START_TIME, END_TIME, "store-001");
    }

    @Test
    void testJobStatsMapper_mapsEveryColumn() {
        Row row = new Row(8);
        row.put("node_id", "store-001");
        row.put("host_name", "localhost");
        row.put("job_name", "Routing");
        row.put("start_time", START_TIME);
        row.put("end_time", END_TIME);
        row.put("processed_count", 42L);
        row.put("error_flag", 1);
        row.put("error_message", "boom");
        JobStats stats = statisticService.new JobStatsMapper().mapRow(row);
        assertEquals("store-001", stats.getNodeId());
        assertEquals("localhost", stats.getHostName());
        assertEquals("Routing", stats.getJobName());
        assertEquals(START_TIME, stats.getStartTime());
        assertEquals(END_TIME, stats.getEndTime());
        assertEquals(42L, stats.getProcessedCount());
        assertTrue(stats.isErrorFlag());
        assertEquals("boom", stats.getErrorMessage());
    }

    @Test
    void testJobStatsMapper_withAMissingColumn() {
        Row row = new Row("node_id", "store-001");
        StatisticService.JobStatsMapper mapper = statisticService.new JobStatsMapper();
        assertThrows(ColumnNotFoundException.class, () -> mapper.mapRow(row));
    }

    @Test
    void testChannelStatsMapper_truncatesTheTimesAndCarriesTheCounters() {
        ChannelStats stats = statisticService.new ChannelStatsMapper().mapRow(newChannelStatsRow(true));
        assertEquals("store-001", stats.getNodeId());
        assertEquals("localhost", stats.getHostName());
        assertEquals("default", stats.getChannelId());
        assertEquals(Timestamp.valueOf("2023-11-14 17:13:00"), new Timestamp(stats.getStartTime().getTime()));
        assertEquals(Timestamp.valueOf("2023-11-14 17:14:00"), new Timestamp(stats.getEndTime().getTime()));
        assertEquals(1L, stats.getDataRouted());
        assertEquals(17L, stats.getDataBytesLoadedOutgoing());
        assertEquals(START_TIME, stats.getDataMinCreateTime());
        assertEquals(END_TIME, stats.getDataMaxCreateTime());
    }

    @Test
    void testChannelStatsMapper_toleratesAMissingHostNameAndChannelId() {
        ChannelStats stats = statisticService.new ChannelStatsMapper().mapRow(newChannelStatsRow(false));
        assertNull(stats.getHostName());
        assertNull(stats.getChannelId());
        assertEquals("store-001", stats.getNodeId());
    }

    @Test
    void testHostStatsMapper_mapsEveryColumn() {
        HostStats stats = statisticService.new HostStatsMapper().mapRow(newHostStatsRow());
        assertEquals("store-001", stats.getNodeId());
        assertEquals("localhost", stats.getHostName());
        assertEquals(Timestamp.valueOf("2023-11-14 17:13:00"), new Timestamp(stats.getStartTime().getTime()));
        assertEquals(1L, stats.getRestarted());
        assertEquals(2L, stats.getNodesPulled());
        assertEquals(14L, stats.getPurgedExpiredDataRows());
        assertEquals(20L, stats.getDataGapCount());
        assertEquals(21L, stats.getDataUnroutedCount());
    }

    @Test
    void testGetHostStatsForPeriod_readsThroughTheTemplate() {
        when(sqlTemplate.<HostStats> query(anyString(), any(), any(Object[].class))).thenReturn(Collections.<HostStats> emptyList());
        assertTrue(statisticService.getHostStatsForPeriod(START_TIME, END_TIME, "store-001").isEmpty());
    }

    @Test
    void testGetHostStatsForPeriod_bucketsTheWindowWithABlankEntry() {
        when(sqlTemplate.<HostStats> query(anyString(), any(), any(Object[].class))).thenReturn(Collections.<HostStats> emptyList());
        TreeMap<Date, HostStats> byPeriod = statisticService.getHostStatsForPeriod(START_TIME, END_TIME, "store-001", 5);
        assertEquals(1, byPeriod.size());
        assertEquals(0L, byPeriod.firstEntry().getValue().getRestarted());
    }

    private ISymmetricDialect newSymmetricDialect() {
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(platform.scrubSql(anyString())).thenAnswer(returnsFirstArg());
        return symmetricDialect;
    }

    private Row newChannelStatsRow(boolean withHostAndChannel) {
        Row row = new Row(24);
        row.put("node_id", "store-001");
        if (withHostAndChannel) {
            row.put("host_name", "localhost");
            row.put("channel_id", "default");
        }
        row.put("start_time", START_TIME);
        row.put("end_time", END_TIME);
        String[] counters = new String[] { "data_routed", "data_unrouted", "data_event_inserted", "data_extracted", "data_bytes_extracted",
                "data_extracted_errors", "data_sent", "data_bytes_sent", "data_sent_errors", "data_received", "data_bytes_received", "data_loaded",
                "data_bytes_loaded", "data_loaded_errors", "data_loaded_outgoing", "data_loaded_outgoing_errors", "data_bytes_loaded_outgoing" };
        putCounters(row, counters);
        row.put("data_min_create_time", START_TIME);
        row.put("data_max_create_time", END_TIME);
        return row;
    }

    private Row newHostStatsRow() {
        Row row = new Row(25);
        row.put("node_id", "store-001");
        row.put("host_name", "localhost");
        row.put("start_time", START_TIME);
        row.put("end_time", END_TIME);
        String[] counters = new String[] { "restarted", "nodes_pulled", "nodes_pushed", "nodes_rejected", "nodes_registered", "nodes_loaded",
                "nodes_disabled", "purged_data_rows", "purged_data_event_rows", "purged_batch_outgoing_rows", "purged_batch_incoming_rows",
                "purged_stranded_data_rows", "purged_stranded_event_rows", "purged_expired_data_rows", "triggers_created_count", "triggers_rebuilt_count",
                "triggers_removed_count", "total_nodes_pull_time", "total_nodes_push_time", "data_gap_count", "data_unrouted_count" };
        putCounters(row, counters);
        return row;
    }

    private void putCounters(Row row, String[] columnNames) {
        for (int i = 0; i < columnNames.length; i++) {
            row.put(columnNames[i], Long.valueOf(i + 1));
        }
    }

    private String sqlFor(String key) {
        return statisticService.getSql(key);
    }
}
