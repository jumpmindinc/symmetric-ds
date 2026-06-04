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
package org.jumpmind.symmetric.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;

import org.jumpmind.db.util.IPooledDataSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;
import org.jumpmind.db.model.CatalogSchema;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Transaction;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.exception.IoException;
import org.jumpmind.extension.IProgressListener;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.csv.CsvWriter;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.db.firebird.FirebirdSymmetricDialect;
import org.jumpmind.symmetric.db.mysql.MySqlSymmetricDialect;
import org.jumpmind.symmetric.io.data.DbExport;
import org.jumpmind.symmetric.io.data.DbExport.Format;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.io.data.transform.TransformTable;
import org.jumpmind.symmetric.job.IJob;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.Lock;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITransformService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.jumpmind.symmetric.service.impl.UpdateService;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.LogSummary;
import org.jumpmind.util.ZipBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@IgnoreJRERequirement
public class SnapshotUtil {
    private static final Logger log = LoggerFactory.getLogger(SnapshotUtil.class);
    protected static final int THREAD_INDENT_SPACE = 50;
    public static final String SNAPSHOT_DIR = "snapshots";
    public static final String ERROR_BATCHES_SUBDIR = "batches";

    public static File getSnapshotDirectory(ISymmetricEngine engine) {
        File snapshotsDir = new File(engine.getParameterService().getTempDirectory(), SNAPSHOT_DIR);
        snapshotsDir.mkdirs();
        return snapshotsDir;
    }

    public static File createSnapshot(ISymmetricEngine engine, IProgressListener listener) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String dirName = engine.getEngineName().replaceAll(" ", "-") + "-" + dateFormat.format(new Date());
        IParameterService parameterService = engine.getParameterService();
        File tmpDir = new File(parameterService.getTempDirectory(), dirName);
        tmpDir.mkdirs();
        log.info("Creating snapshot file in " + tmpDir.getAbsolutePath());
        int stepNumber = 0, totalSteps = 36;
        checkpoint(engine, listener, stepNumber++, totalSteps);
        try {
            log.info("Calling beforeSnapshot()");
            for (ISnapshotUtilListener snapshotListener : engine.getExtensionService().getExtensionPointList(ISnapshotUtilListener.class)) {
                snapshotListener.beforeSnapshot(engine, tmpDir);
            }
        } catch (Exception e) {
            log.info("Call to beforeSnapshot() threw exception", e);
        }
        log.info("Exporting configuration");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        try (FileWriter fwriter = new FileWriter(new File(tmpDir, "config-export.csv"))) {
            engine.getDataExtractorService().extractConfigurationStandalone(engine.getNodeService().findIdentity(),
                    fwriter, TableConstants.getConfigTablesExcludedFromExport());
        } catch (Exception e) {
            log.warn("Failed to export symmetric configuration", e);
        }
        checkpoint(engine, listener, stepNumber++, totalSteps);
        File serviceConfFile = new File("conf/sym_service.conf");
        try {
            if (serviceConfFile.exists()) {
                FileUtils.copyFileToDirectory(serviceConfFile, tmpDir);
            }
        } catch (Exception e) {
            log.warn("Failed to copy " + serviceConfFile.getName() + " to the snapshot directory", e);
        }
        checkpoint(engine, listener, stepNumber++, totalSteps);
        log.info("Writing table definitions");
        IDatabasePlatform targetPlatform = engine.getSymmetricDialect().getTargetPlatform();
        ISymmetricDialect targetDialect = engine.getTargetDialect();
        try {
            HashMap<CatalogSchema, List<Table>> catalogSchemas = getTablesForCaptureByCatalogSchema(engine);
            checkpoint(engine, listener, stepNumber++, totalSteps);
            addTablesForLoadByCatalogSchema(engine, catalogSchemas);
            checkpoint(engine, listener, stepNumber++, totalSteps);
            for (CatalogSchema catalogSchema : catalogSchemas.keySet()) {
                DbExport export = new DbExport(targetPlatform);
                boolean isDefaultCatalog = Strings.CI.equals(catalogSchema.getCatalog(), targetPlatform.getDefaultCatalog());
                boolean isDefaultSchema = Strings.CI.equals(catalogSchema.getSchema(), targetPlatform.getDefaultSchema());
                String filename = null;
                if (isDefaultCatalog && isDefaultSchema) {
                    filename = "table-definitions.xml";
                } else {
                    String extra = "";
                    if (!isDefaultCatalog && catalogSchema.getCatalog() != null) {
                        extra += catalogSchema.getCatalog();
                        export.setCatalog(catalogSchema.getCatalog());
                    }
                    if (!isDefaultSchema && catalogSchema.getSchema() != null) {
                        if (!extra.equals("")) {
                            extra += "-";
                        }
                        extra += catalogSchema.getSchema();
                        export.setSchema(catalogSchema.getSchema());
                    }
                    log.info("Writing table definitions for {}", extra);
                    filename = "table-definitions-" + extra + ".xml";
                }
                try (FileOutputStream fos = new FileOutputStream(new File(tmpDir, filename))) {
                    List<Table> tables = catalogSchemas.get(catalogSchema);
                    export.setFormat(Format.XML);
                    export.setNoData(true);
                    export.exportTables(fos, tables.toArray(new Table[tables.size()]));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to export table definitions", e);
        }
        checkpoint(engine, listener, stepNumber++, totalSteps);
        log.info("Writing runtime data - nodes");
        File exportDir = new File(tmpDir, "export");
        exportDir.mkdirs();
        String tablePrefix = engine.getTablePrefix();
        DbExport export = new DbExport(engine.getDatabasePlatform());
        export.setFormat(Format.CSV_DQUOTE);
        export.setNoCreateInfo(true);
        export.setUseReadUncommitted(true);
        int maxBatches = parameterService.getInt(ParameterConstants.SNAPSHOT_MAX_BATCHES);
        int maxNodeChannels = parameterService.getInt(ParameterConstants.SNAPSHOT_MAX_NODE_CHANNELS);
        extract(export, new File(exportDir, "node_identity.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_IDENTITY));
        extract(export, new File(exportDir, "node.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE));
        extract(export, new File(exportDir, "node_security.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_SECURITY));
        extract(export, new File(exportDir, "node_host.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST));
        extract(export, maxNodeChannels, "", new File(exportDir, "node_channel_ctl.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_CHANNEL_CTL));
        checkpoint(engine, listener, stepNumber++, totalSteps);
        log.info("Writing runtime data - locks");
        try {
            if (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
                engine.getNodeCommunicationService().persistToTableForSnapshot();
                engine.getClusterService().persistToTableForSnapshot();
            }
        } catch (Exception e) {
            log.warn("Unable to add SYM_NODE_COMMUNICATION to the snapshot.", e);
        }
        extract(export, maxNodeChannels, "", new File(exportDir, "node_communication.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_COMMUNICATION));
        extract(export, new File(exportDir, "context.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_CONTEXT));
        extract(export, new File(exportDir, "lock.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_LOCK));
        log.info("Writing runtime data - outgoing batch");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        Map<String, NodeSecurity> nodeSecurities = engine.getNodeService().findAllNodeSecurity(true);
        Map<String, Channel> channels = engine.getConfigurationService().getChannels(false);
        String byChannelId = "";
        if (nodeSecurities != null && channels != null && nodeSecurities.size() * channels.size() < maxNodeChannels) {
            byChannelId = "channel_id ,";
        }
        Object[] systemChannelIds = new String[] { Constants.CHANNEL_CONFIG, Constants.CHANNEL_SYSTEM,
                Constants.CHANNEL_MONITOR, Constants.CHANNEL_HEARTBEAT, Constants.CHANNEL_DYNAMIC };
        extract(export, maxBatches, String.format("where status = 'OK' and channel_id not in ('%s', '%s', '%s', '%s', '%s') order by batch_id desc",
                systemChannelIds), new File(exportDir, "outgoing_batch_ok.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_OUTGOING_BATCH));
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, maxBatches, String.format("where status = 'OK' and channel_id in ('%s', '%s', '%s', '%s', '%s') order by batch_id desc",
                systemChannelIds), new File(exportDir, "outgoing_batch_system_ok.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_OUTGOING_BATCH));
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, maxBatches, "where status != 'OK' order by batch_id", new File(exportDir, "outgoing_batch_not_ok.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_OUTGOING_BATCH));
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, maxBatches, "order by start_id, end_id desc", new File(exportDir, "data_gap.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_DATA_GAP));
        extractQuery(engine.getDatabasePlatform().getSqlTemplateDirty(), exportDir + File.separator + "outgoing_batch_summary.csv",
                "select node_id, " + byChannelId + "status, count(*) batch_count, sum(data_row_count) data_row_count, sum(byte_count) byte_count, " +
                        "sum(error_flag) error_flag, min(create_time) min_create_time, sum(router_millis) router_millis, sum(extract_millis) extract_millis, " +
                        "sum(network_millis) network_millis, sum(filter_millis) filter_millis, sum(load_millis) load_millis, " +
                        "sum(fallback_insert_count) fallback_insert_count, sum(fallback_update_count) fallback_update_count, " +
                        "sum(missing_delete_count) missing_delete_count, sum(skip_count) skip_count, sum(ignore_count) ignore_count " +
                        "from " + TableConstants.getTableName(tablePrefix, TableConstants.SYM_OUTGOING_BATCH) +
                        " group by node_id, " + byChannelId + "status");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        try {
            outputSymDataForBatchesInError(engine, tmpDir);
        } catch (Exception e) {
            log.warn("Failed to export data from batch in error", e);
        }
        log.info("Writing runtime data - incoming batch");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, maxBatches, String.format("where status = 'OK' and channel_id not in ('%s', '%s', '%s', '%s', '%s') order by batch_id desc",
                systemChannelIds), new File(exportDir, "incoming_batch_ok.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_INCOMING_BATCH));
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, maxBatches, String.format("where status = 'OK' and channel_id in ('%s', '%s', '%s', '%s', '%s') order by batch_id desc",
                systemChannelIds), new File(exportDir, "incoming_batch_system_ok.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_INCOMING_BATCH));
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, maxBatches, "where status != 'OK' order by create_time", new File(exportDir, "incoming_batch_not_ok.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_INCOMING_BATCH));
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extractQuery(engine.getDatabasePlatform().getSqlTemplateDirty(), exportDir + File.separator + "incoming_batch_summary.csv",
                "select node_id, " + byChannelId + "status, count(*) batch_count, sum(data_row_count) data_row_count, sum(byte_count) byte_count, " +
                        "sum(error_flag) error_flag, min(create_time) min_create_time, sum(router_millis) router_millis, sum(extract_millis) extract_millis, " +
                        "sum(network_millis) network_millis, sum(filter_millis) filter_millis, sum(load_millis) load_millis, " +
                        "sum(fallback_insert_count) fallback_insert_count, sum(fallback_update_count) fallback_update_count, " +
                        "sum(missing_delete_count) missing_delete_count, sum(skip_count) skip_count, sum(ignore_count) ignore_count " +
                        "from " + TableConstants.getTableName(tablePrefix, TableConstants.SYM_INCOMING_BATCH) +
                        " group by node_id, " + byChannelId + "status");
        log.info("Writing runtime data - requests");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, new File(exportDir, "table_reload_request.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_TABLE_RELOAD_REQUEST));
        extract(export, new File(exportDir, "table_reload_status.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_TABLE_RELOAD_STATUS));
        extract(export, new File(exportDir, "extract_request.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_EXTRACT_REQUEST));
        extract(export, new File(exportDir, "registration_request.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_REGISTRATION_REQUEST));
        log.info("Writing runtime data - history and stats");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, new File(exportDir, "trigger_hist.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_TRIGGER_HIST));
        extract(export, 10000, "order by start_time desc", new File(exportDir, "node_host_channel_stats.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST_CHANNEL_STATS));
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, 10000, "order by start_time desc", new File(exportDir, "node_host_stats.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST_STATS));
        extract(export, 10000, "order by start_time desc", new File(exportDir, "node_host_job_stats.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST_JOB_STATS));
        checkpoint(engine, listener, stepNumber++, totalSteps);
        if (parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)) {
            extract(export, 5000, "order by relative_dir, file_name", new File(exportDir, "file_snapshot.csv"),
                    TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_SNAPSHOT));
        }
        log.info("Writing runtime data - export config");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, new File(exportDir, "channel.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_CHANNEL));
        extract(export, new File(exportDir, "conflict.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_CONFLICT));
        extract(export, new File(exportDir, "extension.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_EXTENSION));
        extract(export, new File(exportDir, "file_trigger.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_TRIGGER));
        extract(export, new File(exportDir, "file_trigger_router.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_TRIGGER_ROUTER));
        extract(export, new File(exportDir, "incoming_error.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_INCOMING_ERROR));
        extract(export, new File(exportDir, "job.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_JOB));
        extract(export, new File(exportDir, "load_filter.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_LOAD_FILTER));
        extract(export, new File(exportDir, "node_group.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_GROUP));
        extract(export, new File(exportDir, "node_group_channel_wnd.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_GROUP_CHANNEL_WND));
        extract(export, new File(exportDir, "node_group_link.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_GROUP_LINK));
        extract(export, new File(exportDir, "outgoing_error.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_OUTGOING_ERROR));
        extract(export, new File(exportDir, "parameter.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_PARAMETER));
        extract(export, new File(exportDir, "registration_redirect.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_REGISTRATION_REDIRECT));
        extract(export, new File(exportDir, "router.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_ROUTER));
        extract(export, new File(exportDir, "sequence.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_SEQUENCE));
        extract(export, new File(exportDir, "transform_column.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_TRANSFORM_COLUMN));
        extract(export, new File(exportDir, "transform_table.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_TRANSFORM_TABLE));
        extract(export, new File(exportDir, "trigger.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_TRIGGER));
        extract(export, new File(exportDir, "trigger_router.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_TRIGGER_ROUTER));
        // Pro tables can be ignored if they are missing
        export.setIgnoreMissingTables(true);
        log.info("Writing runtime data - pro tables");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        extract(export, new File(exportDir, "console_event.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_CONSOLE_EVENT));
        extract(export, 10000, "order by start_time desc", new File(exportDir, "console_table_stats.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_CONSOLE_TABLE_STATS));
        extract(export, new File(exportDir, "monitor.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_MONITOR));
        extract(export, 10000, "order by event_time desc", new File(exportDir, "monitor_event.csv"),
                TableConstants.getTableName(tablePrefix, TableConstants.SYM_MONITOR_EVENT));
        extract(export, new File(exportDir, "notification.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_NOTIFICATION));
        extract(export, new File(exportDir, "compare_request.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_COMPARE_REQUEST));
        extract(export, new File(exportDir, "compare_status.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_COMPARE_STATUS));
        extract(export, new File(exportDir, "compare_table_status.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_COMPARE_TABLE_STATUS));
        extract(export, new File(exportDir, "table_group.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_TABLE_GROUP));
        extract(export, new File(exportDir, "table_group_hier.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_TABLE_GROUP_HIER));
        extract(export, new File(exportDir, "console_role.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_CONSOLE_ROLE));
        extract(export, new File(exportDir, "console_role_privilege.csv"), TableConstants.getTableName(tablePrefix, TableConstants.SYM_CONSOLE_ROLE_PRIVILEGE));
        log.info("Writing runtime data - parameters");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        writeRuntimeParameters(engine, tmpDir);
        writeChangedParameters(engine, tmpDir);
        try {
            Properties props = new Properties();
            props.putAll(System.getProperties());
            writeProperties(props, tmpDir, "system.properties");
        } catch (Exception e) {
            log.warn("Failed to export system information", e);
        }
        log.info("Writing runtime data - log summaries");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        File logSummaryFile = new File(tmpDir, "log-summary.csv");
        try (OutputStream outputStream = new FileOutputStream(logSummaryFile);
                CsvWriter csvWriter = new CsvWriter(outputStream, ',', Charset.defaultCharset())) {
            csvWriter.setEscapeMode(CsvWriter.ESCAPE_MODE_DOUBLED);
            csvWriter.setForceQualifier(true);
            csvWriter.writeRecord(new String[] { "Level", "First Time", "Last Time", "Count", "Message", "Stack Trace" });
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            List<LogSummary> logSummaries = LogSummaryAppenderUtils.getLogSummaryErrors(engine.getEngineName());
            logSummaries.addAll(LogSummaryAppenderUtils.getLogSummaryWarnings(engine.getEngineName()));
            for (LogSummary s : logSummaries) {
                csvWriter.writeRecord(new String[] { s.getLevel().name(), df.format(new Date(s.getFirstOccurranceTime())), df.format(new Date(s
                        .getMostRecentTime())), String.valueOf(s.getCount()), s.getMessage(), s.getStackTrace() });
            }
            csvWriter.flush();
        } catch (Exception e) {
            log.warn("Failed to write log summaries");
        }
        log.info("Writing runtime data - platform specific");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        if (targetDialect instanceof FirebirdSymmetricDialect) {
            log.info("Writing Firebird info");
            final String[] monTables = { "mon$database", "mon$attachments", "mon$transactions", "mon$statements", "mon$io_stats",
                    "mon$record_stats", "mon$memory_usage", "mon$call_stack", "mon$context_variables" };
            DbExport dbexport = new DbExport(targetPlatform);
            dbexport.setFormat(Format.CSV_DQUOTE);
            dbexport.setNoCreateInfo(true);
            for (String table : monTables) {
                extract(dbexport, new File(tmpDir, "firebird-" + table + ".csv"), table);
            }
        }
        if (targetDialect instanceof MySqlSymmetricDialect) {
            log.info("Writing MySQL info");
            extractQuery(targetPlatform.getSqlTemplate(), tmpDir + File.separator + "mysql-processlist.csv",
                    "show processlist");
            extractQuery(targetPlatform.getSqlTemplate(), tmpDir + File.separator + "mysql-global-variables.csv",
                    "show global variables");
            extractQuery(targetPlatform.getSqlTemplate(), tmpDir + File.separator + "mysql-session-variables.csv",
                    "show session variables");
        }
        checkpoint(engine, listener, stepNumber++, totalSteps);
        if (!engine.getParameterService().is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            try (FileOutputStream fos = new FileOutputStream(new File(tmpDir, "sym_data_gap_cache.csv"))) {
                List<DataGap> gaps = engine.getRouterService().getDataGaps();
                SimpleDateFormat dformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                dformat.setTimeZone(TimeZone.getTimeZone("GMT"));
                fos.write("start_id,end_id,create_time\n".getBytes(Charset.defaultCharset()));
                if (gaps != null) {
                    for (DataGap gap : gaps) {
                        fos.write((gap.getStartId() + "," + gap.getEndId() + ",\"" + dformat.format(gap.getCreateTime()) + "\",\"" + "\"\n").getBytes(Charset
                                .defaultCharset()));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to export data gap information", e);
            }
        }
        log.info("Writing threads info");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        createThreadsFile(tmpDir.getPath(), false);
        createThreadsFile(tmpDir.getPath(), true);
        createThreadStatsFile(tmpDir.getPath());
        createProcessInfoFile(engine, tmpDir.getPath());
        checkpoint(engine, listener, stepNumber++, totalSteps);
        try {
            log.info("Writing transactions file");
            List<Transaction> transactions = targetPlatform.getTransactions();
            if (!transactions.isEmpty()) {
                createTransactionsFile(engine, tmpDir.getPath(), transactions);
            }
        } catch (Throwable e) {
            log.warn("Failed to create transactions file", e);
        }
        log.info("Writing runtime stats");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        File logDir = getOrCreateLogDir();
        writeRuntimeStats(engine, tmpDir, logDir);
        log.info("Writing job stats");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        writeJobsStats(engine, tmpDir);
        checkpoint(engine, listener, stepNumber++, totalSteps);
        if ("true".equals(System.getProperty(SystemConstants.SYSPROP_STANDALONE_WEB))) {
            writeDirectoryListing(engine, tmpDir);
        }
        checkpoint(engine, listener, stepNumber++, totalSteps);
        writeDirectoryStaging(engine, tmpDir);
        checkpoint(engine, listener, stepNumber++, totalSteps);
        if (logDir.exists()) {
            log.info("Copying log files");
            File[] files = logDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    if ((name.endsWith(".log") || name.endsWith(".log.1") || name.endsWith(".log.2") || name.endsWith(".log.3")) && (name.contains("symmetric")
                            || name.contains("wrapper"))) {
                        try {
                            FileUtils.copyFileToDirectory(file, tmpDir);
                        } catch (Exception e) {
                            log.warn("Failed to copy " + file.getName() + " to the snapshot directory", e);
                        }
                    }
                }
            }
        }
        File backupConfig = new File("conf/.config");
        if (backupConfig.canRead()) {
            try {
                FileUtils.copyFileToDirectory(backupConfig, tmpDir);
            } catch (IOException e) {
                log.warn("Failed to copy {}", backupConfig.getName());
            }
        }
        log.info("Packaging ZIP file");
        checkpoint(engine, listener, stepNumber++, totalSteps);
        File jarFile = null;
        try {
            String filename = tmpDir.getName() + ".zip";
            if (parameterService.is(ParameterConstants.SNAPSHOT_FILE_INCLUDE_HOSTNAME)) {
                filename = AppUtils.getHostName() + "_" + filename;
            }
            jarFile = new File(getSnapshotDirectory(engine), filename);
            ZipBuilder builder = new ZipBuilder(tmpDir, jarFile, new File[] { tmpDir });
            builder.build();
            FileUtils.deleteDirectory(tmpDir);
        } catch (Exception e) {
            throw new IoException("Failed to package snapshot files into archive", e);
        }
        checkpoint(engine, listener, stepNumber++, totalSteps);
        try {
            log.info("Calling afterSnapshot()");
            for (ISnapshotUtilListener snapshotListener : engine.getExtensionService().getExtensionPointList(ISnapshotUtilListener.class)) {
                snapshotListener.afterSnapshot(engine, tmpDir);
            }
        } catch (Exception e) {
            log.info("Call to afterSnapshot() threw exception", e);
        }
        checkpoint(engine, listener, stepNumber, totalSteps);
        log.info("Done creating snapshot file in {} steps", stepNumber);
        return jarFile;
    }

    protected static void checkpoint(ISymmetricEngine engine, IProgressListener listener, int stepNumber, int totalSteps) {
        try {
            if (listener != null) {
                listener.checkpoint(engine.getEngineName(), stepNumber, totalSteps);
            }
        } catch (Exception e) {
            log.info("Call to checkpoint threw exception", e);
        }
    }

    protected static void extract(DbExport export, File file, String... tables) {
        extract(export, Integer.MAX_VALUE, null, file, tables);
    }

    protected static void extract(DbExport export, int maxRows, String whereClause, File file, String... tables) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            export.setMaxRows(maxRows);
            export.setWhereClause(whereClause);
            export.exportTables(fos, tables);
        } catch (Exception e) {
            log.warn("Failed to export table definitions", e);
        }
        if (export.getRowCount() == 0) {
            FileUtils.deleteQuietly(file);
        }
    }

    protected static void extractQuery(ISqlTemplate sqlTemplate, String fileName, String sql) {
        try (CsvWriter writer = new CsvWriter(fileName)) {
            List<Row> rows = sqlTemplate.query(sql);
            writer.setEscapeMode(CsvWriter.ESCAPE_MODE_DOUBLED);
            writer.setForceQualifier(true);
            boolean isFirstRow = true;
            for (Row row : rows) {
                if (isFirstRow) {
                    for (String key : row.keySet()) {
                        writer.write(key);
                    }
                    writer.endRecord();
                    isFirstRow = false;
                }
                for (String key : row.keySet()) {
                    writer.write(row.getString(key));
                }
                writer.endRecord();
            }
        } catch (Exception e) {
            log.warn("Failed to run extract query " + sql, e);
        }
    }

    protected static void writeDirectoryListing(ISymmetricEngine engine, File tmpDir) {
        try {
            File home = new File(System.getProperty("user.dir"));
            if (home.getName().equalsIgnoreCase("bin")) {
                home = home.getParentFile();
            }
            log.info("Writing directory listing of {}", home);
            StringBuilder output = new StringBuilder();
            int maxFiles = engine.getParameterService().getInt(ParameterConstants.SNAPSHOT_MAX_FILES);
            printDirectoryContents(home, output, new PrintDirConfig(maxFiles, engine.getStagingManager().getStagingDirectory().getParentFile()));
            FileUtils.write(new File(tmpDir, "directory-listing.txt"), output, Charset.defaultCharset(), false);
        } catch (Exception ex) {
            log.warn("Failed to output the directory listing", ex);
        }
    }

    protected static void writeDirectoryStaging(ISymmetricEngine engine, File tmpDir) {
        try {
            log.info("Writing staging listing of {}", engine.getStagingManager().getStagingDirectory());
            StringBuilder output = new StringBuilder();
            int maxFiles = engine.getParameterService().getInt(ParameterConstants.SNAPSHOT_MAX_FILES);
            printDirectoryContents(engine.getStagingManager().getStagingDirectory(), output, new PrintDirConfig(maxFiles));
            FileUtils.write(new File(tmpDir, "directory-staging.txt"), output, Charset.defaultCharset(), false);
        } catch (Exception ex) {
            log.warn("Failed to output the directory staging", ex);
        }
    }

    protected static void printDirectoryContents(File dir, StringBuilder output, PrintDirConfig config) throws IOException {
        if (config.getFileCount() >= config.getMaxCount()) {
            return;
        }
        output.append("\n");
        output.append(dir.getCanonicalPath());
        output.append("\n");
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.parallelSort(files, config.getFileComparator());
            for (File file : files) {
                output.append("  ");
                output.append(file.isDirectory() ? "d" : "-");
                output.append(file.canRead() ? "r" : "-");
                output.append(file.canWrite() ? "w" : "-");
                output.append(file.canExecute() ? "x" : "-");
                output.append(StringUtils.leftPad(file.length() + "", 11));
                output.append(" ");
                output.append(config.getDateFormat().format(new Date(file.lastModified())));
                output.append(" ");
                output.append(file.getName());
                output.append("\n");
                if (config.incrementFileCount() >= config.getMaxCount()) {
                    output.append("\n*** MAX LIMIT OF " + config.getMaxCount() + " FILES ***\n");
                    return;
                }
            }
            for (File file : files) {
                if (file.isDirectory() && (config.getExcludeDir() == null || (!config.getExcludeDir().equals(dir)
                        && !file.getName().equalsIgnoreCase("tmp")))) {
                    printDirectoryContents(file, output, config);
                }
            }
        }
    }

    protected static void writeRuntimeStats(ISymmetricEngine engine, File tmpDir, File logDir) {
        try {
            Properties runtimeProperties = new Properties();
            DataSource dataSource = engine.getDatabasePlatform().getDataSource();
            IPooledDataSource pooledDataSource = IPooledDataSource.of(dataSource);
            if (pooledDataSource != null) {
                runtimeProperties.setProperty("connections.idle", String.valueOf(pooledDataSource.getNumIdle()));
                runtimeProperties.setProperty("connections.used", String.valueOf(pooledDataSource.getNumActive()));
                runtimeProperties.setProperty("connections.max", String.valueOf(pooledDataSource.getMaxTotal()));
            }
            Runtime rt = Runtime.getRuntime();
            DecimalFormat df = new DecimalFormat("#,###");
            runtimeProperties.setProperty("memory.jvm.free", df.format(rt.freeMemory()));
            runtimeProperties.setProperty("memory.jvm.used", df.format(rt.totalMemory() - rt.freeMemory()));
            runtimeProperties.setProperty("memory.jvm.max", df.format(rt.maxMemory()));
            List<MemoryPoolMXBean> memoryPools = new ArrayList<MemoryPoolMXBean>(ManagementFactory.getMemoryPoolMXBeans());
            long usedHeapMemory = 0;
            for (MemoryPoolMXBean memoryPool : memoryPools) {
                if (memoryPool.getType() == MemoryType.HEAP) {
                    MemoryUsage memoryUsage = memoryPool.getCollectionUsage();
                    runtimeProperties.setProperty("memory.heap." + memoryPool.getName().toLowerCase().replaceAll(" ", "."),
                            df.format(memoryUsage.getUsed()));
                    usedHeapMemory += memoryUsage.getUsed();
                }
            }
            runtimeProperties.setProperty("memory.heap.total", df.format(usedHeapMemory));
            try {
                Method method = ManagementFactory.getOperatingSystemMXBean().getClass().getMethod("getTotalPhysicalMemorySize");
                method.setAccessible(true);
                runtimeProperties.setProperty("memory.system.total", df.format(method.invoke(ManagementFactory.getOperatingSystemMXBean())));
            } catch (Exception ignore) {
            }
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            runtimeProperties.setProperty("os.name", System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
            runtimeProperties.setProperty("os.processors", String.valueOf(osBean.getAvailableProcessors()));
            runtimeProperties.setProperty("os.load.average", String.valueOf(osBean.getSystemLoadAverage()));
            runtimeProperties.setProperty("engine.is.started", Boolean.toString(engine.isStarted()));
            runtimeProperties.setProperty("engine.last.restart", engine.getLastRestartTime() != null ? engine.getLastRestartTime().toString() : "");
            runtimeProperties.setProperty("time.server", new Date().toString());
            runtimeProperties.setProperty("time.database", new Date(engine.getTargetDialect().getDatabaseTime()).toString());
            runtimeProperties.setProperty("batch.unrouted.data.count", df.format(engine.getRouterService().getUnroutedDataCount()));
            runtimeProperties.setProperty("batch.outgoing.errors.count",
                    df.format(engine.getOutgoingBatchService().countOutgoingBatchesInError()));
            runtimeProperties.setProperty("batch.outgoing.tosend.count",
                    df.format(engine.getOutgoingBatchService().countOutgoingBatchesUnsent()));
            runtimeProperties.setProperty("batch.outgoing.tosend.offline.count",
                    df.format(engine.getOutgoingBatchService().countOutgoingBatchesUnsentOfflineNodes(
                            ParameterConstants.OFFLINE_NODE_DETECTION_PERIOD_MINUTES)));
            Map<String, Long> blockedBatches = engine.getOutgoingBatchService().countUnsentBatchesBlocked();
            runtimeProperties.setProperty("batch.outgoing.tosend.blocked.count", blockedBatches.get("batch_count") + "");
            runtimeProperties.setProperty("batch.outgoing.tosend.blocked.nodes", blockedBatches.get("node_count") + "");
            runtimeProperties.setProperty("batch.incoming.errors.count",
                    df.format(engine.getIncomingBatchService().countIncomingBatchesInError()));
            List<DataGap> gaps = engine.getDataService().findDataGapsUnchecked();
            runtimeProperties.setProperty("data.gap.count", df.format(gaps.size()));
            if (gaps.size() > 0) {
                runtimeProperties.setProperty("data.gap.start.id", df.format(gaps.get(0).getStartId()));
                runtimeProperties.setProperty("data.gap.end.id", df.format(gaps.get(gaps.size() - 1).getEndId()));
            }
            runtimeProperties.setProperty("file.snapshot.entries.count", df.format(engine.getFileSyncService().countEntriesInFileSnapshot()));
            runtimeProperties.setProperty("data.id.min", df.format(engine.getDataService().findMinDataId()));
            runtimeProperties.setProperty("data.id.max", df.format(engine.getDataService().findMaxDataId()));
            String jvmTitle = Runtime.class.getPackage().getImplementationTitle();
            runtimeProperties.put("jvm.title", jvmTitle != null ? jvmTitle : "Unknown");
            String jvmVendor = Runtime.class.getPackage().getImplementationVendor();
            runtimeProperties.put("jvm.vendor", jvmVendor != null ? jvmVendor : "Unknown");
            String jvmVersion = Runtime.class.getPackage().getImplementationVersion();
            runtimeProperties.put("jvm.version", jvmVersion != null ? jvmVersion : "Unknown");
            RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
            List<String> arguments = runtimeMxBean.getInputArguments();
            runtimeProperties.setProperty("jvm.arguments", arguments.toString());
            runtimeProperties.setProperty("jvm.bits", System.getProperty("sun.arch.data.model", System.getProperty("com.ibm.vm.bitmode")));
            runtimeProperties.setProperty("hostname", AppUtils.getHostName());
            addUsableDiskSpaceProperties(runtimeProperties, engine, tmpDir, logDir);
            runtimeProperties.setProperty("instance.id", engine.getClusterService().getInstanceId());
            runtimeProperties.setProperty("server.id", engine.getClusterService().getServerId());
            try {
                runtimeProperties.setProperty("charset.server", System.getProperty("file.encoding"));
                runtimeProperties.setProperty("charset.database", engine.getTargetDialect().getTargetPlatform().getCharSetName());
            } catch (Exception e) {
            }
            try {
                MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
                ObjectName oName = new ObjectName("java.lang:type=OperatingSystem");
                runtimeProperties.setProperty("file.descriptor.open.count", mbeanServer.getAttribute(oName, "OpenFileDescriptorCount").toString());
                runtimeProperties.setProperty("file.descriptor.max.count", mbeanServer.getAttribute(oName, "MaxFileDescriptorCount").toString());
            } catch (Exception e) {
            }
            ISnapshotHelper helper = engine.getExtensionService().getExtensionPoint(ISnapshotHelper.class);
            if (helper != null) {
                runtimeProperties.putAll(helper.getRuntimeProperties());
            }
            writeProperties(runtimeProperties, tmpDir, "runtime-stats.properties");
        } catch (Exception e) {
            log.warn("Failed to export runtime-stats information", e);
        }
    }

    protected static File getOrCreateLogDir() {
        File logDir = LogSummaryAppenderUtils.getLogDir();
        if (logDir == null || !logDir.exists()) {
            logDir = new File("logs");
        }
        if (!logDir.exists()) {
            logDir = new File("../logs");
        }
        if (!logDir.exists()) {
            logDir = new File("target");
        }
        return logDir;
    }

    protected static void addUsableDiskSpaceProperties(Properties properties, ISymmetricEngine engine, File tmpDir, File logDir) {
        properties.setProperty("log.directory.space.usable",
                FileUtils.byteCountToDisplaySize(logDir.getUsableSpace()));
        properties.setProperty("staging.directory.space.usable",
                FileUtils.byteCountToDisplaySize(engine.getStagingManager().getStagingDirectory().getUsableSpace()));
        properties.setProperty("temp.directory.space.usable",
                FileUtils.byteCountToDisplaySize(tmpDir.getUsableSpace()));
    }

    protected static void writeProperties(Properties properties, File tmpDir, String fileName) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(tmpDir, fileName)))) {
            List<String> keys = new ArrayList<String>();
            for (Object key : properties.keySet()) {
                keys.add(key.toString());
            }
            Collections.sort(keys);
            for (String key : keys) {
                bw.write(key + "=" + properties.getProperty(key).replace("\n", "\\n").replace("\r", "\\r"));
                bw.newLine();
            }
        } catch (Exception e) {
            log.warn("Failed to write " + fileName, e);
        }
    }

    protected static void writeJobsStats(ISymmetricEngine engine, File tmpDir) {
        try {
            File file = new File(tmpDir, "job-status.csv");
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            IClusterService clusterService = engine.getClusterService();
            List<IJob> jobs = engine.getJobManager().getJobs();
            Map<String, Lock> locks = clusterService.findLocks();
            try (OutputStream outputStream = new FileOutputStream(file);
                    CsvWriter csvWriter = new CsvWriter(outputStream, ',', Charset.defaultCharset())) {
                csvWriter.setEscapeMode(CsvWriter.ESCAPE_MODE_DOUBLED);
                String[] heading = { "Job Name", "Schedule", "Status", "Server ID", "Last Server ID", "Last Finish Time", "Next Run Time",
                        "Last Run Period", "Avg Run Period" };
                csvWriter.writeRecord(heading);
                for (IJob job : jobs) {
                    Lock lock = locks.get(job.getName());
                    String status = getJobStatus(job, lock);
                    String runningServerId = lock != null ? lock.getLockingServerId() : "";
                    String lastServerId = clusterService.getServerId();
                    if (lock != null) {
                        lastServerId = lock.getLastLockingServerId();
                    }
                    String lastFinishTime = getLastFinishTime(job, lock, df);
                    String nextRunTime = job.getNextExecutionTime() == null ? "" : df.format(job.getNextExecutionTime());
                    String[] row = { job.getName().replace("_", " "), job.getSchedule(), status, runningServerId == null ? "" : runningServerId,
                            lastServerId == null ? "" : lastServerId, lastFinishTime == null ? "" : lastFinishTime, nextRunTime,
                            job.getLastExecutionTimeInMs() + "", job.getAverageExecutionTimeInMs() + "" };
                    csvWriter.writeRecord(row);
                }
                csvWriter.flush();
            }
        } catch (Exception e) {
            log.warn("Failed to write jobs information", e);
        }
    }

    protected static String getJobStatus(IJob job, Lock lock) {
        String status = "IDLE";
        if (lock != null) {
            if (lock.isStopped()) {
                status = "STOPPED";
            } else if (lock.getLockTime() != null) {
                status = "RUNNING";
            }
        } else {
            status = job.isRunning() ? "RUNNING" : job.isPaused() ? "PAUSED" : job.isStarted() ? "IDLE" : "STOPPED";
        }
        return status;
    }

    protected static String getLastFinishTime(IJob job, Lock lock, SimpleDateFormat df) {
        if (lock != null && lock.getLastLockTime() != null) {
            return df.format(lock.getLastLockTime());
        } else {
            return job.getLastFinishTime() == null ? null : df.format(job.getLastFinishTime());
        }
    }

    public static File createThreadsFile(String parent, boolean isFiltered) {
        File file = new File(parent, isFiltered ? "threads-filtered.txt" : "threads.txt");
        try (FileWriter fwriter = new FileWriter(file)) {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long[] threadIds = threadBean.getAllThreadIds();
            for (long l : threadIds) {
                ThreadInfo info = threadBean.getThreadInfo(l, 100);
                if (info != null) {
                    String threadName = info.getThreadName();
                    boolean skip = isFiltered;
                    if (isFiltered) {
                        for (StackTraceElement element : info.getStackTrace()) {
                            String name = element.getClassName();
                            if (name.startsWith("com.jumpmind.") || name.startsWith("org.jumpmind.")) {
                                skip = false;
                            }
                            if (name.equals(SnapshotUtil.class.getName()) || name.startsWith(UpdateService.class.getName())) {
                                skip = true;
                                break;
                            }
                        }
                    }
                    if (!skip) {
                        fwriter.append(StringUtils.rightPad(threadName, THREAD_INDENT_SPACE));
                        fwriter.append(AppUtils.formatStackTrace(info.getStackTrace(), THREAD_INDENT_SPACE, false));
                        fwriter.append("\n");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to export thread information", e);
        }
        return file;
    }

    public static File createThreadStatsFile(String parent) {
        File file = new File(parent, "threads-stats.csv");
        try (OutputStream outputStream = new FileOutputStream(file);
                CsvWriter csvWriter = new CsvWriter(outputStream, ',', Charset.forName("ISO-8859-1"))) {
            csvWriter.setEscapeMode(CsvWriter.ESCAPE_MODE_DOUBLED);
            csvWriter.setForceQualifier(true);
            String[] heading = { "Thread", "Allocated Memory (Bytes)", "CPU Time (Seconds)" };
            csvWriter.writeRecord(heading);
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long[] threadIds = threadBean.getAllThreadIds();
            for (long l : threadIds) {
                ThreadInfo info = threadBean.getThreadInfo(l, 100);
                if (info != null) {
                    String threadName = info.getThreadName();
                    long threadId = info.getThreadId();
                    long allocatedBytes = 0;
                    try {
                        Method method = threadBean.getClass().getMethod("getThreadAllocatedBytes");
                        method.setAccessible(true);
                        allocatedBytes = (Long) method.invoke(threadBean, threadId);
                    } catch (Exception ignore) {
                    }
                    String[] row = { threadName, Long.toString(allocatedBytes), Float.toString(threadBean.getThreadCpuTime(threadId) / 1000000000f) };
                    csvWriter.writeRecord(row);
                }
            }
            csvWriter.flush();
        } catch (Exception e) {
            log.warn("Failed to export thread information", e);
        }
        return file;
    }

    public static void createProcessInfoFile(ISymmetricEngine engine, String parent) {
        try {
            File file = new File(parent, "process-info.csv");
            File fileActive = new File(parent, "process-info-active.csv");
            List<ProcessInfo> infos = engine.getStatisticManager().getProcessInfos();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try (OutputStream outputStream = new FileOutputStream(file);
                    OutputStream outputActiveStream = new FileOutputStream(fileActive);
                    CsvWriter csvWriter = new CsvWriter(outputStream, ',', Charset.defaultCharset());
                    CsvWriter csvActiveWriter = new CsvWriter(outputActiveStream, ',', Charset.defaultCharset())) {
                csvWriter.setEscapeMode(CsvWriter.ESCAPE_MODE_DOUBLED);
                String[] heading = { "Thread Name", "Source Node", "Target Node", "Type", "Queue", "Current Channel ID", "Status", "Current Data Count",
                        "Total Data Count", "Total Batch Count", "Current Batch ID", "Current Batch Count", "Current Table Name", "Batch Start Time", "Load ID",
                        "Start Time", "End Time" };
                csvWriter.writeRecord(heading);
                csvActiveWriter.writeRecord(heading);
                for (ProcessInfo i : infos) {
                    Thread t = i.getThread();
                    String[] row = { t == null ? null : t.getName(), i.getSourceNodeId(), i.getTargetNodeId(), i.getProcessType().toString(),
                            i.getQueue(), i.getCurrentChannelId(), i.getStatus().toString(), String.valueOf(i.getCurrentDataCount()),
                            String.valueOf(i.getTotalDataCount()), String.valueOf(i.getTotalBatchCount()), String.valueOf(i.getCurrentBatchId()),
                            String.valueOf(i.getCurrentBatchCount()), i.getCurrentTableName(),
                            i.getCurrentBatchStartTime() == null ? null : df.format(i.getCurrentBatchStartTime()),
                            String.valueOf(i.getCurrentLoadId()), i.getStartTime() == null ? null : df.format(i.getStartTime()),
                            i.getEndTime() == null ? null : df.format(i.getEndTime()) };
                    csvWriter.writeRecord(row);
                    if (i.getEndTime() == null) {
                        csvActiveWriter.writeRecord(row);
                    }
                }
                csvWriter.flush();
                csvActiveWriter.flush();
            }
        } catch (Exception e) {
            log.warn("Failed to write process info", e);
        }
    }

    private static File createTransactionsFile(ISymmetricEngine engine, String parent, List<Transaction> transactions) {
        Map<String, Transaction> transactionMap = new HashMap<String, Transaction>();
        for (Transaction transaction : transactions) {
            transactionMap.put(transaction.getId(), transaction);
        }
        List<Transaction> filteredTransactions = new ArrayList<Transaction>();
        String dbUser = engine.getParameterService().getString("db.user");
        for (Transaction transaction : transactions) {
            SymmetricUtils.filterTransactions(transaction, transactionMap, filteredTransactions, dbUser, false, false);
        }
        File file = new File(parent, "transactions.csv");
        try (OutputStream outputStream = new FileOutputStream(file);
                CsvWriter csvWriter = new CsvWriter(outputStream, ',', Charset.forName("ISO-8859-1"))) {
            csvWriter.setEscapeMode(CsvWriter.ESCAPE_MODE_DOUBLED);
            String[] heading = { "ID", "Username", "Remote IP", "Remote Host", "Status", "Reads", "Writes",
                    "Blocking ID", "Duration", "Text" };
            csvWriter.writeRecord(heading);
            for (Transaction transaction : filteredTransactions) {
                String[] row = { transaction.getId(), transaction.getUsername(), transaction.getRemoteIp(),
                        transaction.getRemoteHost(), transaction.getStatus(),
                        transaction.getReads() == -1 ? "" : String.valueOf(transaction.getReads()),
                        transaction.getWrites() == -1 ? "" : String.valueOf(transaction.getWrites()),
                        transaction.getBlockingId(), String.valueOf(transaction.getDuration()) + " ms",
                        transaction.getText() };
                csvWriter.writeRecord(row);
            }
            csvWriter.flush();
        } catch (Exception e) {
            log.warn("Failed to create transactions file", e);
        }
        return file;
    }

    public static void outputSymDataForBatchesInError(ISymmetricEngine engine, File tmpDir) {
        String tablePrefix = engine.getTablePrefix();
        DbExport export = new DbExport(engine.getDatabasePlatform());
        export.setFormat(Format.CSV_DQUOTE);
        export.setNoCreateInfo(true);
        // Create files for each batch in error
        File errorDir = null;
        for (OutgoingBatch batch : engine.getOutgoingBatchService().getOutgoingBatchErrors(10000).getBatches()) {
            if (batch.getFailedDataId() > 0) {
                Data data = engine.getDataService().findData(batch.getFailedDataId());
                if (data != null) {
                    // Write sym_data to file
                    String filenameCaptured = batch.getBatchId() + "_captured.csv";
                    String whereClause = "where data_id = " + data.getDataId();
                    if (errorDir == null) {
                        errorDir = new File(tmpDir, ERROR_BATCHES_SUBDIR);
                        errorDir.mkdirs();
                    }
                    extract(export, 10000, whereClause, new File(errorDir, filenameCaptured),
                            TableConstants.getTableName(tablePrefix, TableConstants.SYM_DATA));
                    // Write parsed row data to file
                    String filenameParsed = errorDir + File.separator + batch.getBatchId() + "_parsed.csv";
                    try (CsvWriter writer = new CsvWriter(filenameParsed)) {
                        writer.setEscapeMode(CsvWriter.ESCAPE_MODE_DOUBLED);
                        writer.writeRecord(data.getTriggerHistory().getParsedColumnNames());
                        writer.writeRecord(data.toParsedRowData());
                        writer.writeRecord(data.toParsedOldData());
                    } catch (Exception e) {
                        log.warn("Failed to write parsed row data from sym_data to file " + filenameParsed, e);
                    }
                } else {
                    log.warn("Could not find data ID: " + batch.getFailedDataId() + " for batch ID: " + batch.getBatchId() + " in error");
                }
            }
        }
    }

    public static HashMap<CatalogSchema, List<Table>> getTablesForCaptureByCatalogSchema(ISymmetricEngine engine) {
        IDatabasePlatform targetPlatform = engine.getSymmetricDialect().getTargetPlatform();
        HashMap<CatalogSchema, List<Table>> catalogSchemas = new HashMap<CatalogSchema, List<Table>>();
        ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
        List<TriggerHistory> triggerHistories = triggerRouterService.getActiveTriggerHistories();
        String tablePrefix = engine.getTablePrefix().toUpperCase();
        Set<String> triggerIds = new HashSet<String>();
        boolean isClonedTables = engine.getParameterService().is("sync.triggers.expand.table.clone", true);
        long timeoutMillis = engine.getParameterService().getLong(ParameterConstants.SNAPSHOT_OPERATION_TIMEOUT_MS, 30000);
        long ts = System.currentTimeMillis();
        for (TriggerHistory triggerHistory : triggerHistories) {
            if (!triggerHistory.getSourceTableName().toUpperCase().startsWith(tablePrefix)) {
                if (isClonedTables && !triggerIds.add(triggerHistory.getTriggerId())) {
                    Trigger trigger = triggerRouterService.getTriggerById(triggerHistory.getTriggerId(), false);
                    if (trigger != null && trigger.getSourceTableName().contains("$(targetExternalId)")) {
                        // for multi-tenant database where the same table is repeated for each node, just need one definition
                        continue;
                    }
                }
                Table table = targetPlatform.getTableFromCache(triggerHistory.getSourceCatalogName(),
                        triggerHistory.getSourceSchemaName(), triggerHistory.getSourceTableName(), false);
                if (table != null) {
                    addTableToMap(catalogSchemas, new CatalogSchema(table.getCatalog(), table.getSchema()), table);
                }
                if (System.currentTimeMillis() - ts > timeoutMillis) {
                    log.info("Reached time limit for capture table definitions");
                    break;
                }
            }
        }
        return catalogSchemas;
    }

    public static HashMap<CatalogSchema, List<Table>> getTablesForLoadByCatalogSchema(ISymmetricEngine engine) {
        HashMap<CatalogSchema, List<Table>> tables = new HashMap<CatalogSchema, List<Table>>();
        addTablesForLoadByCatalogSchema(engine, tables);
        return tables;
    }

    protected static void addTablesForLoadByCatalogSchema(ISymmetricEngine engine, HashMap<CatalogSchema, List<Table>> catalogSchemas) {
        ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
        IParameterService parameterService = engine.getParameterService();
        IDatabasePlatform targetPlatform = engine.getSymmetricDialect().getTargetPlatform();
        Node targetNode = engine.getNodeService().findIdentity();
        List<Node> nodes = engine.getNodeService().findAllNodes();
        Map<String, Node> sampleNodeForGroup = new HashMap<String, Node>();
        for (Node node : nodes) {
            sampleNodeForGroup.put(node.getNodeGroupId(), node);
        }
        Map<NodeGroupLink, Map<String, List<TransformTable>>> extractTransformMap = new HashMap<NodeGroupLink, Map<String, List<TransformTable>>>();
        Map<NodeGroupLink, Map<String, List<TransformTable>>> loadTransformMap = new HashMap<NodeGroupLink, Map<String, List<TransformTable>>>();
        List<TriggerRouter> triggerRouters = triggerRouterService.getTriggerRoutersForTargetNode(parameterService.getNodeGroupId());
        long timeoutMillis = engine.getParameterService().getLong(ParameterConstants.SNAPSHOT_OPERATION_TIMEOUT_MS, 30000);
        long ts = System.currentTimeMillis();
        for (TriggerRouter triggerRouter : triggerRouters) {
            Trigger trigger = triggerRouter.getTrigger();
            if (!trigger.isSourceWildCarded()) {
                String catalog = null;
                String schema = null;
                String tableName = trigger.getSourceTableName();
                Router router = triggerRouter.getRouter();
                Node sourceNode = sampleNodeForGroup.get(router.getNodeGroupLink().getSourceNodeGroupId());
                if (router.isUseSourceCatalogSchema()) {
                    catalog = trigger.getSourceCatalogName();
                    schema = trigger.getSourceSchemaName();
                }
                if (Strings.CS.equals(Constants.NONE_TOKEN, router.getTargetCatalogName())) {
                    catalog = null;
                } else if (StringUtils.isNotBlank(router.getTargetCatalogName())) {
                    catalog = SymmetricUtils.replaceNodeVariables(sourceNode, targetNode, router.getTargetCatalogName());
                }
                if (Strings.CS.equals(Constants.NONE_TOKEN, router.getTargetSchemaName())) {
                    schema = null;
                } else if (StringUtils.isNotBlank(router.getTargetSchemaName())) {
                    schema = SymmetricUtils.replaceNodeVariables(sourceNode, targetNode, router.getTargetSchemaName());
                }
                if (StringUtils.isNotBlank(router.getTargetTableName())) {
                    tableName = router.getTargetTableName();
                }
                List<Table> tablesToLookup = new ArrayList<Table>();
                Map<String, List<TransformTable>> byTableExtractTransforms = getByTableTransforms(engine.getTransformService(), extractTransformMap, router
                        .getNodeGroupLink(), TransformPoint.EXTRACT);
                String tableKey = Table.getFullyQualifiedTableName(catalog, schema, tableName).toLowerCase();
                List<TransformTable> extractTransforms = byTableExtractTransforms.get(tableKey);
                if (extractTransforms != null && extractTransforms.size() > 0) {
                    for (TransformTable transform : extractTransforms) {
                        tablesToLookup.add(new Table(transform.getTargetCatalogName(), transform.getTargetSchemaName(), transform.getTargetTableName()));
                    }
                }
                if (tablesToLookup.size() == 0) {
                    tablesToLookup.add(new Table(catalog, schema, tableName));
                }
                Map<String, List<TransformTable>> byTableLoadTransforms = getByTableTransforms(engine.getTransformService(), loadTransformMap, router
                        .getNodeGroupLink(), TransformPoint.LOAD);
                ListIterator<Table> iterator = tablesToLookup.listIterator();
                while (iterator.hasNext()) {
                    Table table = iterator.next();
                    List<TransformTable> loadTransforms = byTableLoadTransforms.get(table.getFullyQualifiedTableName().toLowerCase());
                    if (loadTransforms != null && loadTransforms.size() > 0) {
                        iterator.remove();
                        for (TransformTable transform : loadTransforms) {
                            iterator.add(new Table(transform.getTargetCatalogName(), transform.getTargetSchemaName(), transform.getTargetTableName()));
                        }
                    }
                }
                for (Table table : tablesToLookup) {
                    table = targetPlatform.getTableFromCache(table.getCatalog(), table.getSchema(), table.getName(), false);
                    if (table != null) {
                        addTableToMap(catalogSchemas, new CatalogSchema(table.getCatalog(), table.getSchema()), table);
                    }
                }
                if (System.currentTimeMillis() - ts > timeoutMillis) {
                    log.info("Reached time limit for load table definitions");
                    break;
                }
            }
        }
    }

    private static void addTableToMap(HashMap<CatalogSchema, List<Table>> catalogSchemas, CatalogSchema catalogSchema, Table table) {
        List<Table> tables = catalogSchemas.get(catalogSchema);
        if (tables == null) {
            tables = new ArrayList<Table>();
            catalogSchemas.put(catalogSchema, tables);
        }
        if (!tables.contains(table)) {
            tables.add(table);
        }
    }

    protected static Map<String, List<TransformTable>> getByTableTransforms(ITransformService transformService,
            Map<NodeGroupLink, Map<String, List<TransformTable>>> transformMap, NodeGroupLink nodeGroupLink, TransformPoint transformPoint) {
        Map<String, List<TransformTable>> byTableTransforms = transformMap.get(nodeGroupLink);
        if (byTableTransforms == null) {
            List<TransformTableNodeGroupLink> transforms = transformService.findTransformsFor(nodeGroupLink, transformPoint);
            byTableTransforms = toMap(transforms);
            transformMap.put(nodeGroupLink, byTableTransforms);
        }
        return byTableTransforms;
    }

    protected static Map<String, List<TransformTable>> toMap(List<TransformTableNodeGroupLink> transforms) {
        Map<String, List<TransformTable>> transformsByTable = new HashMap<String, List<TransformTable>>();
        if (transforms != null) {
            for (TransformTable transformTable : transforms) {
                String sourceTableName = transformTable.getFullyQualifiedSourceTableName().toLowerCase();
                List<TransformTable> tables = transformsByTable.get(sourceTableName);
                if (tables == null) {
                    tables = new ArrayList<TransformTable>();
                    transformsByTable.put(sourceTableName, tables);
                }
                tables.add(transformTable);
            }
        }
        return transformsByTable;
    }

    private static void writeRuntimeParameters(ISymmetricEngine engine, File tmpDir) {
        try {
            Properties effectiveParameters = engine.getParameterService().getAllParameters();
            Properties parameters = ParametersUtil.deepCopy(effectiveParameters);
            parameters.putAll(effectiveParameters);
            ParametersUtil.redactParameters(parameters);
            writeProperties(parameters, tmpDir, "parameters.properties");
        } catch (Exception e) {
            log.warn("Failed to export parameter information", e);
        }
    }

    private static void writeChangedParameters(ISymmetricEngine engine, File tmpDir) {
        try {
            Properties defaultParameters = new Properties();
            InputStream in = SnapshotUtil.class.getResourceAsStream("/symmetric-default.properties");
            defaultParameters.load(in);
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            in = SnapshotUtil.class.getResourceAsStream("/symmetric-console-default.properties");
            if (in != null) {
                defaultParameters.load(in);
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            Properties effectiveParameters = engine.getParameterService().getAllParameters();
            Properties changedParameters = new Properties();
            Properties sysProp = System.getProperties();
            Map<String, String> env = System.getenv();
            for (String key : effectiveParameters.stringPropertyNames()) {
                String defaultValue = defaultParameters.getProperty(key);
                String currentValue = effectiveParameters.getProperty(key);
                if ((defaultValue == null && currentValue != null || (defaultValue != null && !defaultValue.equals(currentValue))) && ((!sysProp.containsKey(
                        key) && !env.containsKey(key)) || defaultParameters.containsKey(key))) {
                    changedParameters.put(key, currentValue == null ? "" : currentValue);
                }
            }
            ParametersUtil.redactParameters(changedParameters);
            writeProperties(changedParameters, tmpDir, "parameters-changed.properties");
        } catch (Exception e) {
            log.warn("Failed to export parameters-changed information", e);
        }
    }

    static class FileComparator implements Comparator<File> {
        @Override
        public int compare(File o1, File o2) {
            return o1.getPath().compareToIgnoreCase(o2.getPath());
        }
    }

    static class PrintDirConfig {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Comparator<File> fileComparator = new FileComparator();
        int fileCount;
        int maxCount;
        File excludeDir;

        public PrintDirConfig(int maxCount) {
            this.maxCount = maxCount;
        }

        public PrintDirConfig(int maxCount, File excludeDir) {
            this.maxCount = maxCount;
            this.excludeDir = excludeDir;
        }

        public Comparator<File> getFileComparator() {
            return fileComparator;
        }

        public SimpleDateFormat getDateFormat() {
            return df;
        }

        public int incrementFileCount() {
            return fileCount++;
        }

        public int getFileCount() {
            return fileCount;
        }

        public int getMaxCount() {
            return maxCount;
        }

        public File getExcludeDir() {
            return excludeDir;
        }
    }
}
