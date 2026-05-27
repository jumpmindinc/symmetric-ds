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
package org.jumpmind.symmetric.route;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.io.DatabaseXmlUtil;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.SchemaObject;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.file.IFileSourceTracker;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.FileTrigger;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerReBuildReason;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IDataService;

public abstract class AbstractFileParsingRouter extends AbstractDataRouter {
    public abstract List<String> parse(InputStream in, String fileName, int lineNumber, int tableId);

    public abstract String getColumnNames();

    public abstract ISymmetricEngine getEngine();

    public final static String TRIGGER_ID_FILE_PARSER = "SYM_VIRTUAL_FILE_PARSE_TRIGGER";
    public final static String EXTERNAL_DATA_ROUTER_KEY = "R";
    public final static String EXTERNAL_DATA_TRIGGER_KEY = "T";
    public final static String EXTERNAL_DATA_FILE_DATA_ID = "D";
    public final static String CONTEXT_FILE_SOURCE_TRACKERS = "trackers";

    @Override
    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData, Set<Node> nodes,
            boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        Map<String, String> newData = getNewDataAsString(null, dataMetaData, getEngine().getSymmetricDialect());
        String targetTableName = dataMetaData.getRouter().getTargetTableName();
        String fileName = newData.get("FILE_NAME");
        String relativeDir = newData.get("RELATIVE_DIR");
        String triggerId = newData.get("TRIGGER_ID");
        String lastEventType = newData.get("LAST_EVENT_TYPE");
        String routerExpression = dataMetaData.getRouter().getRouterExpression();
        String channelId = Constants.CHANNEL_DEFAULT;
        FileParsingOptions options = new FileParsingOptions();
        String filePath = relativeDir + "/" + fileName;
        IContextService contextService = getEngine().getContextService();
        if (lastEventType.equals(DataEventType.DELETE.toString())) {
            log.debug("File deleted (" + filePath + "), cleaning up context value.");
            contextService.delete(filePath);
        } else {
            options = FileParsingOptions.parse(routerExpression);
            if (triggerId != null) {
                String baseDir = getEngine().getFileSyncService().getFileTrigger(triggerId).getBaseDir();
                IFileSourceTracker tracker = getFileSourceTracker(context, baseDir);
                File file = createSourceFile(baseDir, relativeDir, fileName, triggerId, dataMetaData.getRouter().getRouterId(), tracker);
                String nodeList = buildNodeList(nodes);
                String externalData = new StringBuilder(EXTERNAL_DATA_TRIGGER_KEY).append("=").append(triggerId).append(",")
                        .append(EXTERNAL_DATA_ROUTER_KEY).append("=").append(dataMetaData.getRouter().getRouterId()).append(",")
                        .append(EXTERNAL_DATA_FILE_DATA_ID).append("=").append(dataMetaData.getData().getDataId()).toString();
                try (InputStream in = getInputStream(tracker, file)) {
                    Map<Integer, String> tableNames = getTableNames(getTargetTableName(targetTableName, fileName), in);
                    if (options.isStandardizeNames()) {
                        standardizeTableNames(tableNames);
                    }
                    int tableIndex = 0;
                    String transactionId = null;
                    if (options.isIncludeTransactionId()) {
                        transactionId = String.valueOf(System.currentTimeMillis());
                    }
                    for (Map.Entry<Integer, String> tableEntry : tableNames.entrySet()) {
                        String contextId = filePath + "[" + tableEntry.getValue() + "]";
                        Integer lineNumber = 0;
                        if (options.isTailFile()) {
                            lineNumber = contextService.getInt(contextId, 0);
                        }
                        List<String> dataRows = parse(in, fileName, lineNumber, tableEntry.getKey());
                        String columnNames = getColumnNames();
                        if (options.isStandardizeNames()) {
                            columnNames = standardizeColumnNames(columnNames);
                        }
                        TriggerHistory hist = getTriggerHistory(tableEntry.getValue(), columnNames);
                        if (options.isSendDdl()) {
                            sendTableDefinition(dataRows, channelId, hist, nodes, externalData);
                        }
                        if (options.isOverwrite()) {
                            clearTable(channelId, hist, triggerRouter, nodes, externalData);
                        }
                        insertIntoData(dataRows, channelId, hist, nodeList, externalData, transactionId);
                        if (!dataRows.isEmpty()) {
                            lineNumber += dataRows.size();
                            if (options.isTailFile()) {
                                contextService.save(contextId, lineNumber.toString());
                            }
                            if ((tableNames.size() - 1) == tableIndex) {
                                deleteFileIfNecessary(dataMetaData);
                            }
                        }
                        log.info("Finished parsing file[table] " + fileName + "[" + tableEntry.getValue() + "]");
                        tableIndex++;
                    }
                } catch (IOException ioe) {
                    log.error("Unable to load file", ioe);
                }
            }
        }
        return new HashSet<String>();
    }

    public Map<Integer, String> getTableNames(String tableName, InputStream in) throws IOException {
        Map<Integer, String> tableNames = new HashMap<Integer, String>();
        tableNames.put(1, tableName);
        return tableNames;
    }

    public String getTargetTableName(String targetTableName, String fileName) {
        if (targetTableName == null) {
            int i = fileName.indexOf(".");
            if (i > 0) {
                targetTableName = fileName.substring(0, i);
            } else {
                targetTableName = fileName;
            }
        }
        return targetTableName;
    }

    protected String standardizeName(String name) {
        if (name != null) {
            name = name.trim().toLowerCase();
            name = StringUtils.stripAccents(name);
            name = name.replaceAll("[^\\w_]", "_").replaceAll("_+", "_");
            name = StringUtils.strip(name, "_");
            if (name.length() > 0 && StringUtils.isNumeric(name.substring(0, 1))) {
                name = "_" + name;
            }
        }
        return name;
    }

    protected Map<Integer, String> standardizeTableNames(Map<Integer, String> tableNames) {
        for (Map.Entry<Integer, String> entry : tableNames.entrySet()) {
            entry.setValue(standardizeName(entry.getValue()));
        }
        return tableNames;
    }

    protected String standardizeColumnNames(String names) {
        String[] tokens = CsvUtils.tokenizeCsvData(names);
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = standardizeName(tokens[i]);
        }
        return CsvUtils.escapeCsvData(tokens);
    }

    public String buildNodeList(Set<Node> nodes) {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(n.getNodeId());
        }
        return sb.toString();
    }

    public File createSourceFile(String baseDir, String relativeDir, String fileName, String triggerId, String routerId, IFileSourceTracker tracker) {
        File sourceFile = null;
        if (tracker != null) {
            FileSnapshot snapshot = new FileSnapshot();
            snapshot.setTriggerId(triggerId);
            snapshot.setRouterId(routerId);
            snapshot.setRelativeDir(relativeDir);
            snapshot.setFileName(fileName);
            sourceFile = tracker.createSourceFile(snapshot);
        } else {
            File sourceBaseDir = new File(baseDir);
            if (!relativeDir.equals(".")) {
                String sourcePath = relativeDir + "/";
                sourceBaseDir = new File(sourceBaseDir, sourcePath);
            }
            sourceFile = new File(sourceBaseDir, fileName);
        }
        return sourceFile;
    }

    protected void sendTableDefinition(List<String> dataRows, String channelId, TriggerHistory hist, Set<Node> nodes, String externalData) {
        Database db = new Database();
        Table table = new Table(hist.getSourceTableName());
        db.addTable(table);
        for (String name : hist.getParsedColumnNames()) {
            Column column = new Column(name);
            column.setTypeCode(Types.VARCHAR);
            table.addColumn(column);
        }
        IDataService dataService = getEngine().getDataService();
        ISqlTransaction transaction = null;
        try {
            transaction = getEngine().getSqlTemplate().startSqlTransaction();
            String xml = CsvUtils.escapeCsvData(DatabaseXmlUtil.toXml(db));
            for (Node node : nodes) {
                dataService.insertCreateEvent(transaction, node, hist, channelId, false, 0,
                        getClass().getSimpleName(), false, false, false, xml, externalData);
            }
            transaction.commit();
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
    }

    protected void clearTable(String channelId, TriggerHistory hist, TriggerRouter triggerRouter, Set<Node> nodes, String externalData) {
        IDataService dataService = getEngine().getDataService();
        String sql = getEngine().getParameterService().getString(ParameterConstants.INITIAL_LOAD_DELETE_FIRST_SQL);
        if (StringUtils.isBlank(sql)) {
            sql = "delete from %s";
        }
        IDatabasePlatform platform = getEngine().getDatabasePlatform();
        DatabaseInfo dbInfo = platform.getDatabaseInfo();
        String tableName = SchemaObject.getFullyQualifiedName(triggerRouter.getTargetCatalog(null, hist),
                triggerRouter.getTargetSchema(null, hist),
                hist.getSourceTableName(), null, dbInfo.getCatalogSeparator(), dbInfo.getSchemaSeparator());
        sql = String.format(sql, tableName);
        ISqlTransaction transaction = null;
        try {
            transaction = getEngine().getSqlTemplate().startSqlTransaction();
            for (Node node : nodes) {
                dataService.insertSqlEvent(transaction, hist, channelId, node, sql, false, 0, externalData, getClass().getSimpleName());
            }
            transaction.commit();
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
    }

    protected void insertIntoData(List<String> dataRows, String channelId, TriggerHistory hist, String nodeList, String externalData,
            String transactionId) {
        for (String row : dataRows) {
            Data data = new Data();
            data.setChannelId(channelId);
            data.setDataEventType(DataEventType.INSERT);
            data.setRowData(row);
            data.setTableName(hist.getSourceTableName());
            data.setNodeList(nodeList);
            data.setTriggerHistory(hist);
            data.setExternalData(externalData);
            data.setDataId(getEngine().getDataService().insertData(data));
            if (transactionId != null) {
                data.setTransactionId(transactionId);
            }
        }
    }

    protected TriggerHistory getTriggerHistory(String tableName, String columnNames) {
        List<TriggerHistory> triggerHistories = getEngine().getTriggerRouterService().getActiveTriggerHistories(tableName);
        for (TriggerHistory history : triggerHistories) {
            if (history.getTriggerId().equals(TRIGGER_ID_FILE_PARSER)) {
                return history;
            }
        }
        TriggerHistory newTriggerHist = new TriggerHistory(tableName, "", columnNames);
        newTriggerHist.setTriggerId(TRIGGER_ID_FILE_PARSER);
        newTriggerHist.setTableHash(0);
        newTriggerHist.setTriggerRowHash(0);
        newTriggerHist.setTriggerTemplateHash(0);
        newTriggerHist.setLastTriggerBuildReason(TriggerReBuildReason.NEW_TRIGGERS);
        newTriggerHist.setColumnNames(columnNames);
        newTriggerHist.setPkColumnNames(columnNames);
        getEngine().getTriggerRouterService().insert(newTriggerHist);
        return newTriggerHist;
    }

    protected InputStream getInputStream(IFileSourceTracker tracker, File file) throws IOException {
        InputStream in = null;
        if (tracker != null) {
            in = tracker.getInputStream(file);
        } else {
            in = new FileInputStream(file);
        }
        return in;
    }

    protected IFileSourceTracker getFileSourceTracker(SimpleRouterContext context, String baseDir) {
        IFileSourceTracker tracker = null;
        for (IFileSourceTracker fileTracker : getFileSourceTrackers(context)) {
            if (fileTracker.handlesDir(baseDir)) {
                tracker = fileTracker;
                break;
            }
        }
        return tracker;
    }

    @SuppressWarnings("unchecked")
    protected List<IFileSourceTracker> getFileSourceTrackers(SimpleRouterContext context) {
        List<IFileSourceTracker> trackers = (List<IFileSourceTracker>) context.get(CONTEXT_FILE_SOURCE_TRACKERS);
        if (trackers == null) {
            trackers = getEngine().getExtensionService().getExtensionPointList(IFileSourceTracker.class);
            context.put(CONTEXT_FILE_SOURCE_TRACKERS, trackers);
        }
        return trackers;
    }

    public static String getRouterIdFromExternalData(String externalData) {
        return parseExternalData(externalData).get(EXTERNAL_DATA_ROUTER_KEY);
    }

    public static Map<String, String> parseExternalData(String externalData) {
        Map<String, String> result = new HashMap<String, String>();
        if (externalData != null) {
            String[] keyValues = externalData.split(",");
            if (keyValues.length > 0) {
                for (int i = 0; i < keyValues.length; i++) {
                    String[] keyValue = keyValues[i].split("=");
                    if (keyValue.length > 1) {
                        for (int j = 0; j < keyValue.length; j++) {
                            result.put(keyValue[0], keyValue[1]);
                        }
                    }
                }
            }
        }
        return result;
    }

    public void deleteFileIfNecessary(DataMetaData dataMetaData) {
        Data data = dataMetaData.getData();
        Table snapshotTable = (Table) dataMetaData.getRelation();
        if (data.getDataEventType() == DataEventType.INSERT || data.getDataEventType() == DataEventType.UPDATE) {
            List<File> filesToDelete = new ArrayList<File>();
            Map<String, String> columnData = data.toColumnNameValuePairs(
                    snapshotTable.getColumnNames(), CsvData.ROW_DATA);
            FileSnapshot fileSnapshot = new FileSnapshot();
            fileSnapshot.setTriggerId(columnData.get("TRIGGER_ID"));
            fileSnapshot.setRouterId(columnData.get("ROUTER_ID"));
            fileSnapshot.setFileModifiedTime(Long.parseLong(columnData
                    .get("FILE_MODIFIED_TIME")));
            fileSnapshot.setFileName(columnData.get("FILE_NAME"));
            fileSnapshot.setRelativeDir(columnData.get("RELATIVE_DIR"));
            fileSnapshot.setLastEventType(LastEventType.fromCode(columnData
                    .get("LAST_EVENT_TYPE")));
            FileTriggerRouter triggerRouter = getEngine().getFileSyncService().getFileTriggerRouter(
                    fileSnapshot.getTriggerId(), fileSnapshot.getRouterId(), true);
            if (triggerRouter != null) {
                FileTrigger fileTrigger = triggerRouter.getFileTrigger();
                if (fileTrigger.isDeleteAfterSync()) {
                    File file = fileTrigger.createSourceFile(fileSnapshot);
                    if (!file.isDirectory()) {
                        filesToDelete.add(file);
                        if (fileTrigger.isSyncOnCtlFile()) {
                            File ctlFile = getEngine().getFileSyncService().getControleFile(file);
                            filesToDelete.add(ctlFile);
                        }
                    }
                } else if (getEngine().getParameterService().is(ParameterConstants.FILE_SYNC_DELETE_CTL_FILE_AFTER_SYNC, false)) {
                    File file = fileTrigger.createSourceFile(fileSnapshot);
                    if (!file.isDirectory()) {
                        if (fileTrigger.isSyncOnCtlFile()) {
                            File ctlFile = getEngine().getFileSyncService().getControleFile(file);
                            filesToDelete.add(ctlFile);
                        }
                    }
                }
            }
            if (filesToDelete != null && filesToDelete.size() > 0) {
                for (File file : filesToDelete) {
                    if (file != null && file.exists()) {
                        log.debug("Deleting the '{}' file", file.getAbsolutePath());
                        boolean deleted = FileUtils.deleteQuietly(file);
                        if (!deleted) {
                            log.warn("Failed to 'delete on sync' the {} file", file.getAbsolutePath());
                        }
                    }
                    file = null;
                }
                filesToDelete = null;
            }
        }
    }
}
