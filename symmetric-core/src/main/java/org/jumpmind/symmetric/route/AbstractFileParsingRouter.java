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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.stage.IStagedResource;
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

public abstract class AbstractFileParsingRouter extends AbstractDataRouter {
    public abstract List<String> parse(File file, int lineNumber, int tableId);

    public abstract String getColumnNames();

    public abstract ISymmetricEngine getEngine();

    public final static String TRIGGER_ID_FILE_PARSER = "SYM_VIRTUAL_FILE_PARSE_TRIGGER";
    public final static String EXTERNAL_DATA_ROUTER_KEY = "R";
    public final static String EXTERNAL_DATA_TRIGGER_KEY = "T";
    public final static String EXTERNAL_DATA_FILE_DATA_ID = "D";
    public final static String ROUTER_EXPRESSION_CHANNEL_KEY = "CHANNEL";
    public final static String ROUTER_EXPRESSION_INCLUDE_TRANSACTION_ID = "INCLUDE_TRANSACTION_ID";

    @Override
    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData, Set<Node> nodes,
            boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        Map<String, String> newData = getNewDataAsString(null, dataMetaData,
                getEngine().getSymmetricDialect());
        String targetTableName = dataMetaData.getRouter().getTargetTableName();
        String fileName = newData.get("FILE_NAME");
        String relativeDir = newData.get("RELATIVE_DIR");
        String triggerId = newData.get("TRIGGER_ID");
        String lastEventType = newData.get("LAST_EVENT_TYPE");
        String routerExpression = dataMetaData.getRouter().getRouterExpression();
        String channelId = "default";
        boolean includeTransactionId = false;
        String filePath = relativeDir + "/" + fileName;
        IContextService contextService = getEngine().getContextService();
        if (lastEventType.equals(DataEventType.DELETE.toString())) {
            log.debug("File deleted (" + filePath + "), cleaning up context value.");
            contextService.delete(filePath);
        } else {
            if (routerExpression != null) {
                String[] keyValues = routerExpression.split(",");
                for (int i = 0; i < keyValues.length; i++) {
                    String[] keyValue = keyValues[i].split("=");
                    if (keyValue.length > 1) {
                        if (ROUTER_EXPRESSION_CHANNEL_KEY.equals(keyValue[0])) {
                            channelId = keyValue[1];
                        } else if (ROUTER_EXPRESSION_INCLUDE_TRANSACTION_ID.equalsIgnoreCase(keyValue[0])) {
                            includeTransactionId = Boolean.valueOf(keyValue[1]);
                        }
                    }
                }
            }
            if (triggerId != null) {
                try {
                    String baseDir = getEngine().getFileSyncService().getFileTrigger(triggerId).getBaseDir();
                    File file = createSourceFile(baseDir, relativeDir, fileName);
                    String nodeList = buildNodeList(nodes);
                    String externalData = new StringBuilder(EXTERNAL_DATA_TRIGGER_KEY)
                            .append("=")
                            .append(triggerId)
                            .append(",")
                            .append(EXTERNAL_DATA_ROUTER_KEY)
                            .append("=")
                            .append(dataMetaData.getRouter().getRouterId())
                            .append(",")
                            .append(EXTERNAL_DATA_FILE_DATA_ID)
                            .append("=")
                            .append(dataMetaData.getData().getDataId()).toString();
                    Map<Integer, String> tableNames = getTableNames(getTargetTableName(targetTableName, fileName), file);
                    int tableIndex = 0;
                    String transactionId = null;
                    if (includeTransactionId) {
                        transactionId = String.valueOf(System.currentTimeMillis());
                    }
                    for (Map.Entry<Integer, String> tableEntry : tableNames.entrySet()) {
                        String contextId = filePath + "[" + tableEntry.getValue() + "]";
                        Integer lineNumber = contextService.getString(contextId) == null ? 0 : Integer.valueOf(contextService.getString(contextId));
                        List<String> dataRows = parse(file, lineNumber, tableEntry.getKey());
                        String columnNames = getColumnNames();
                        for (String row : dataRows) {
                            Data data = new Data();
                            data.setChannelId(channelId);
                            data.setDataEventType(DataEventType.INSERT);
                            data.setRowData(row);
                            data.setTableName(tableEntry.getValue());
                            data.setNodeList(nodeList);
                            data.setTriggerHistory(getTriggerHistory(tableEntry.getValue(), columnNames));
                            data.setExternalData(externalData);
                            data.setDataId(getEngine().getDataService().insertData(data));
                            if (includeTransactionId) {
                                data.setTransactionId(transactionId);
                            }
                            lineNumber++;
                        }
                        if (!dataRows.isEmpty()) {
                            try {
                                contextService.save(contextId, lineNumber.toString());
                                if ((tableNames.size() - 1) == tableIndex) {
                                    deleteFileIfNecessary(dataMetaData);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
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

    public Map<Integer, String> getTableNames(String tableName, File file) throws IOException {
        Map<Integer, String> tableNames = new HashMap<Integer, String>();
        tableNames.put(1, (String) tableName);
        return tableNames;
    }

    public String getTargetTableName(String targetTableName, String fileName) {
        if (targetTableName == null) {
            targetTableName = fileName.substring(0, fileName.indexOf("."));
        }
        return targetTableName;
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

    public Map<String, Integer> readStagingFile(IStagedResource resource) {
        Map<String, Integer> bookmarkMap = new HashMap<String, Integer>();
        try {
            String thisLine = null;
            if (resource.exists()) {
                while ((thisLine = resource.getReader().readLine()) != null) {
                    String[] split = thisLine.split("=");
                    if (split.length == 2) {
                        bookmarkMap.put(split[0].trim(), Integer.valueOf(split[1].trim()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bookmarkMap;
    }

    public File createSourceFile(String baseDir, String relativeDir, String fileName) {
        File sourceBaseDir = new File(baseDir);
        if (!relativeDir.equals(".")) {
            String sourcePath = relativeDir + "/";
            sourceBaseDir = new File(sourceBaseDir, sourcePath);
        }
        return new File(sourceBaseDir, fileName);
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
        Table snapshotTable = dataMetaData.getTable();
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
