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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Transaction;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.ProtocolException;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.CollectionUtils;
import org.jumpmind.util.FormatUtils;
import org.jumpmind.util.LinkedCaseInsensitiveMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bsh.EvalError;
import bsh.Interpreter;

final public class SymmetricUtils {
    private static final Logger log = LoggerFactory.getLogger(SymmetricUtils.class);
    protected static final String OLD_ = "OLD_";
    protected static boolean isNoticeLogged;

    private SymmetricUtils() {
    }

    public static String quote(ISymmetricDialect symmetricDialect, String name) {
        String quote = symmetricDialect.getPlatform().getDatabaseInfo().getDelimiterToken();
        if (StringUtils.isNotBlank(quote)) {
            return quote + name + quote;
        } else {
            return name;
        }
    }

    public static final void replaceSystemAndEnvironmentVariables(Properties properties) {
        Set<Object> keys = new HashSet<Object>(properties.keySet());
        Map<String, String> env = System.getenv();
        Map<String, String> systemProperties = CollectionUtils.toMap(System.getProperties());
        for (Object object : keys) {
            String value = properties.getProperty((String) object);
            if (isNotBlank(value)) {
                value = FormatUtils.replaceTokens(value, env, true);
                value = FormatUtils.replaceTokens(value, systemProperties, true);
                if (value.contains("hostName")) {
                    value = FormatUtils.replace("hostName", AppUtils.getHostName(), value);
                }
                if (value.contains("portNumber")) {
                    value = FormatUtils.replace("portNumber", AppUtils.getPortNumber(), value);
                }
                if (value.contains("protocol")) {
                    value = FormatUtils.replace("protocol", AppUtils.getProtocol(), value);
                }
                if (value.contains("ipAddress")) {
                    value = FormatUtils.replace("ipAddress", AppUtils.getIpAddress(), value);
                }
                if (value.contains("engineName")) {
                    value = FormatUtils.replace("engineName", properties.getProperty(ParameterConstants.ENGINE_NAME, ""), value);
                }
                if (value.contains("nodeGroupId")) {
                    value = FormatUtils.replace("nodeGroupId", properties.getProperty(ParameterConstants.NODE_GROUP_ID, ""), value);
                }
                if (value.contains("externalId")) {
                    value = FormatUtils.replace("externalId", properties.getProperty(ParameterConstants.EXTERNAL_ID, ""), value);
                }
                if (value.contains("syncUrl")) {
                    value = FormatUtils.replace("syncUrl", properties.getProperty(ParameterConstants.SYNC_URL, ""), value);
                }
                if (value.contains("registrationUrl")) {
                    value = FormatUtils.replace("registrationUrl", properties.getProperty(ParameterConstants.REGISTRATION_URL, ""), value);
                }
                properties.put(object, value);
            }
        }
    }

    public static String replaceNodeVariables(Node sourceNode, Node targetNode, String str) {
        if (sourceNode != null) {
            str = FormatUtils.replace("sourceNodeId", sourceNode.getNodeId(), str);
            str = FormatUtils.replace("sourceExternalId", sourceNode.getExternalId(), str);
            str = FormatUtils.replace("sourceNodeGroupId", sourceNode.getNodeGroupId(), str);
        }
        if (targetNode != null) {
            str = FormatUtils.replace("targetNodeId", targetNode.getNodeGroupId(), str);
            str = FormatUtils.replace("targetExternalId", targetNode.getExternalId(), str);
            str = FormatUtils.replace("targetNodeGroupId", targetNode.getNodeGroupId(), str);
        }
        return str;
    }

    public static String replaceCatalogSchemaVariables(String catalogName, String defaultCatalogName,
            String schemaName, String defaultSchemaName, String str) {
        if (catalogName == null) {
            catalogName = defaultCatalogName;
        }
        if (schemaName == null) {
            schemaName = defaultSchemaName;
        }
        if (catalogName != null) {
            str = FormatUtils.replace("sourceCatalogName", catalogName, str);
        }
        if (schemaName != null) {
            str = FormatUtils.replace("sourceSchemaName", schemaName, str);
        }
        return str;
    }

    public static String substituteScripts(String value, Map<String, String> replacementValues) {
        if (log.isDebugEnabled()) {
            log.debug("substituteScripts starting value is: {}", value);
        }
        if (replacementValues == null) {
            replacementValues = new HashMap<String, String>();
        }
        int startTick = StringUtils.indexOf(value, '`');
        if (startTick != -1) {
            int endTick = StringUtils.lastIndexOf(value, '`');
            if (endTick != -1 && startTick != endTick) {
                // there's a bean shell script present in this case
                String script = StringUtils.substring(value, startTick + 1, endTick);
                if (log.isDebugEnabled()) {
                    log.debug("Script found.  Script is is: {}", script);
                }
                Interpreter interpreter = new Interpreter();
                try {
                    interpreter.set("hostName", AppUtils.getHostName());
                    interpreter.set("log", log);
                    interpreter.set("nodeGroupId", replacementValues.get("nodeGroupId"));
                    interpreter.set("syncUrl", replacementValues.get("syncUrl"));
                    interpreter.set("registrationUrl", replacementValues.get("registrationUrl"));
                    interpreter.set("externalId", replacementValues.get("externalId"));
                    interpreter.set("engineName", replacementValues.get("engineName"));
                    Object scriptResult = interpreter.eval(script);
                    if (scriptResult == null) {
                        scriptResult = "";
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Script output is: {}", scriptResult);
                    }
                    value = StringUtils.substring(value, 0, startTick) + scriptResult.toString() +
                            StringUtils.substring(value, endTick + 1);
                } catch (EvalError e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                if (log.isDebugEnabled()) {
                    log.debug("substituteScripts return value is {}", value);
                }
            }
        }
        return value;
    }

    public static void logNotices() {
        synchronized (SymmetricUtils.class) {
            if (isNoticeLogged) {
                return;
            }
            isNoticeLogged = true;
        }
        String notices = null;
        try {
            notices = String.format("%n%s%n", IOUtils.toString(Thread.currentThread().getContextClassLoader().getResource("symmetricds.asciiart"), Charset
                    .defaultCharset()));
            notices = notices.replaceAll("\n", String.format("%n"));
        } catch (Exception ex) {
            notices = String.format("SymmetricDS Start%n");
        }
        String buildTime = Long.toString(Version.getBuildTime());
        String year = null;
        if (buildTime.length() >= 4) {
            year = buildTime.substring(0, 4);
        } else {
            year = new SimpleDateFormat("yyyy").format(new Date());
        }
        int pad = 65;
        notices += String.format(
                "+" + StringUtils.repeat("-", pad) + "+%n" +
                        "|" + StringUtils.rightPad(" Copyright (C) 2007-" + year + " JumpMind, Inc.", pad) + "|%n" +
                        "|" + StringUtils.repeat(" ", pad) + "|%n");
        InputStream in = null;
        try {
            in = AppUtils.class.getResourceAsStream("/symmetric-console-default.properties");
            if (in != null) {
                in.close();
            }
        } catch (Exception e) {
        }
        if (in != null) {
            notices += String.format(
                    "|" + StringUtils.rightPad(" Licensed under one or more agreements from JumpMind, Inc.", pad) + "|%n" +
                            "|" + StringUtils.rightPad(" See doc/license.html", pad) + "|%n");
        } else {
            notices += String.format(
                    "|" + StringUtils.rightPad(" Licensed under the GNU General Public License version 3.", pad) + "|%n" +
                            "|" + StringUtils.rightPad(" This software comes with ABSOLUTELY NO WARRANTY.", pad) + "|%n" +
                            "|" + StringUtils.rightPad(" See http://www.gnu.org/licenses/gpl.html", pad) + "|%n");
        }
        notices += "+" + StringUtils.repeat("-", pad) + "+";
        log.info(notices);
    }

    public static boolean filterTransactions(Transaction transaction, Map<String, Transaction> transactionMap,
            List<Transaction> filteredTransactions, String dbUser, boolean isBlockingUser, boolean isBlocking) {
        return SymmetricUtils.filterTransactions(transaction, transactionMap, filteredTransactions, dbUser, isBlockingUser, isBlocking, 0);
    }

    public static boolean filterTransactions(Transaction transaction, Map<String, Transaction> transactionMap,
            List<Transaction> filteredTransactions, String dbUser, boolean isBlockingUser, boolean isBlocking, int level) {
        if (level > 500) {
            return false;
        }
        Transaction blockingTransaction = transactionMap.get(transaction.getBlockingId());
        if (!isBlocking && blockingTransaction == null) {
            return false;
        }
        if (filteredTransactions.contains(transaction)) {
            return true;
        }
        if (isBlockingUser || (dbUser != null && dbUser.equalsIgnoreCase(transaction.getUsername()))) {
            filteredTransactions.add(transaction);
            if (blockingTransaction != null) {
                filterTransactions(blockingTransaction, transactionMap, filteredTransactions, dbUser, true, true, level + 1);
            }
            return true;
        }
        if (blockingTransaction != null
                && filterTransactions(blockingTransaction, transactionMap, filteredTransactions, dbUser, false, true, level + 1)) {
            filteredTransactions.add(transaction);
            return true;
        }
        return false;
    }

    public static String getDeploymentSubType(Properties properties) {
        if (properties != null) {
            boolean isLoadOnly = Boolean.valueOf(properties.getProperty(ParameterConstants.NODE_LOAD_ONLY, "false"));
            boolean isLogBased = Boolean.valueOf(properties.getProperty(ParameterConstants.START_LOG_MINER_JOB, "false"));
            boolean isTimeBased = Boolean.valueOf(properties.getProperty(ParameterConstants.CAPTURE_TYPE_TIME_BASED, "false"));
            if (isLoadOnly) {
                if (isLogBased) {
                    if (isTimeBased) {
                        return Constants.DEPLOYMENT_SUB_TYPE_TIME_BASED;
                    }
                    return Constants.DEPLOYMENT_SUB_TYPE_LOG_BASED;
                }
                String dbUrl = properties.getProperty("db.url");
                if (dbUrl != null && dbUrl.startsWith("jdbc:h2:file:") && dbUrl.contains("extract-only")) {
                    return Constants.DEPLOYMENT_SUB_TYPE_EXTRACT_ONLY;
                } else {
                    return Constants.DEPLOYMENT_SUB_TYPE_LOAD_ONLY;
                }
            }
        }
        return Constants.DEPLOYMENT_SUB_TYPE_TRIGGER_BASED;
    }

    public static Map<String, String> getDataMap(DataMetaData dataMetaData) {
        Map<String, String> data = null;
        DataEventType dml = dataMetaData.getData().getDataEventType();
        if (DataEventType.UPDATE.equals(dml) || DataEventType.INSERT.equals(dml) || DataEventType.DELETE.equals(dml)) {
            Table table = dataMetaData.getTable();
            if (table != null) {
                data = new LinkedCaseInsensitiveMap<String>(dataMetaData.getTable().getColumnCount() * 4);
            } else {
                data = new LinkedCaseInsensitiveMap<String>();
            }
        } else {
            data = new LinkedCaseInsensitiveMap<String>(1);
        }
        switch (dml) {
            case UPDATE:
                data.putAll(getNewDataAsString(null, dataMetaData));
                data.putAll(getOldDataAsString(OLD_, dataMetaData));
                break;
            case INSERT:
                data.putAll(getNewDataAsString(null, dataMetaData));
                Map<String, String> map = getNullData(OLD_, dataMetaData);
                data.putAll(map);
                break;
            case DELETE:
                data.putAll(getOldDataAsString(null, dataMetaData));
                data.putAll(getOldDataAsString(OLD_, dataMetaData));
                break;
            default:
        }
        if (data != null) {
            if (data.size() == 0) {
                data.putAll(getPkDataAsString(dataMetaData));
            }
            data.put("EXTERNAL_DATA", dataMetaData.getData().getExternalData());
        }
        return data;
    }

    public static Map<String, String> getNewDataAsString(String prefix, DataMetaData dataMetaData) {
        String[] rowData = dataMetaData.getData().toParsedRowData();
        return getDataAsString(prefix, dataMetaData, rowData);
    }

    public static Map<String, String> getOldDataAsString(String prefix, DataMetaData dataMetaData) {
        String[] rowData = dataMetaData.getData().toParsedOldData();
        return getDataAsString(prefix, dataMetaData, rowData);
    }

    public static Map<String, String> getDataAsString(String prefix, DataMetaData dataMetaData, String[] rowData) {
        String[] columns = dataMetaData.getTriggerHistory().getParsedColumnNames();
        Map<String, String> map = new LinkedCaseInsensitiveMap<String>(columns.length * 2);
        if (rowData != null) {
            testColumnNamesMatchValues(dataMetaData, columns, rowData);
            for (int i = 0; i < columns.length; i++) {
                String columnName = columns[i];
                columnName = prefix != null ? prefix + columnName : columnName;
                map.put(columnName, rowData[i]);
                map.put(columnName.toUpperCase(), rowData[i]);
            }
        }
        return map;
    }

    public static Map<String, String> getPkDataAsString(DataMetaData dataMetaData) {
        String[] columns = dataMetaData.getTriggerHistory().getParsedPkColumnNames();
        String[] rowData = dataMetaData.getData().toParsedPkData();
        Map<String, String> map = new LinkedCaseInsensitiveMap<String>(columns.length);
        if (rowData != null) {
            testColumnNamesMatchValues(dataMetaData, columns, rowData);
            for (int i = 0; i < columns.length; i++) {
                String columnName = columns[i].toUpperCase();
                map.put(columnName, rowData[i]);
            }
        }
        return map;
    }

    public static Map<String, Object> getDataObjectMap(DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, boolean upperCase) {
        Map<String, Object> data = null;
        DataEventType dml = dataMetaData.getData().getDataEventType();
        switch (dml) {
            case UPDATE:
                data = new LinkedCaseInsensitiveMap<Object>(dataMetaData.getTable().getColumnCount() * 2);
                data.putAll(getNewDataAsObject(null, dataMetaData, symmetricDialect, upperCase));
                data.putAll(getOldDataAsObject(OLD_, dataMetaData, symmetricDialect, upperCase));
                break;
            case INSERT:
                data = new LinkedCaseInsensitiveMap<Object>(dataMetaData.getTable().getColumnCount() * 2);
                data.putAll(getNewDataAsObject(null, dataMetaData, symmetricDialect, upperCase));
                Map<String, Object> map = getNullData(OLD_, dataMetaData);
                data.putAll(map);
                break;
            case DELETE:
                data = new LinkedCaseInsensitiveMap<Object>(dataMetaData.getTable().getColumnCount() * 2);
                data.putAll(getOldDataAsObject(null, dataMetaData, symmetricDialect, upperCase));
                data.putAll(getOldDataAsObject(OLD_, dataMetaData, symmetricDialect, upperCase));
                if (data.size() == 0) {
                    data.putAll(getPkDataAsObject(dataMetaData, symmetricDialect));
                }
                break;
            default:
                data = new LinkedCaseInsensitiveMap<Object>(1);
                break;
        }
        if (data.size() == 0) {
            data.putAll(getPkDataAsString(dataMetaData));
        }
        if (StringUtils.isNotBlank(dataMetaData.getData().getExternalData())) {
            data.put("EXTERNAL_DATA", dataMetaData.getData().getExternalData());
        } else {
            data.put("EXTERNAL_DATA", null);
        }
        return data;
    }

    public static Map<String, Object> getNewDataAsObject(String prefix, DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, boolean upperCase) {
        return getDataAsObject(prefix, dataMetaData, symmetricDialect, dataMetaData.getData()
                .toParsedRowData(), upperCase);
    }

    public static Map<String, Object> getOldDataAsObject(String prefix, DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, boolean upperCase) {
        return getDataAsObject(prefix, dataMetaData, symmetricDialect, dataMetaData.getData()
                .toParsedOldData(), upperCase);
    }

    public static <T> Map<String, T> getNullData(String prefix, DataMetaData dataMetaData) {
        String[] columnNames = dataMetaData.getTriggerHistory().getParsedColumnNames();
        Map<String, T> data = new LinkedCaseInsensitiveMap<T>(columnNames.length * 2);
        for (String columnName : columnNames) {
            columnName = prefix != null ? prefix + columnName : columnName;
            data.put(columnName, null);
            data.put(columnName.toUpperCase(), null);
        }
        return data;
    }

    public static Map<String, Object> getDataAsObject(String prefix, DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, String[] rowData, boolean upperCase) {
        if (rowData != null) {
            Map<String, Object> data = new LinkedCaseInsensitiveMap<Object>(rowData.length);
            String[] columnNames = dataMetaData.getTriggerHistory().getParsedColumnNames();
            Object[] objects = symmetricDialect.getPlatform().getObjectValues(
                    symmetricDialect.getBinaryEncoding(), dataMetaData.getTable(), columnNames,
                    rowData);
            testColumnNamesMatchValues(dataMetaData, columnNames, objects);
            for (int i = 0; i < columnNames.length; i++) {
                String colName = upperCase ? columnNames[i].toUpperCase() : columnNames[i];
                data.put(prefix != null ? (prefix + colName) : colName, objects[i]);
            }
            return data;
        } else {
            return Collections.emptyMap();
        }
    }

    public static void testColumnNamesMatchValues(DataMetaData dataMetaData, String[] columnNames, Object[] values) {
        if (columnNames.length != values.length) {
            String message = String.format(
                    "The router row for table %s had %d columns but expected %d.",
                    dataMetaData.getData().getTableName(), values.length, columnNames.length);
            throw new ProtocolException(message);
        }
    }

    public static Map<String, Object> getPkDataAsObject(DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect) {
        String[] rowData = dataMetaData.getData().toParsedPkData();
        if (rowData != null) {
            Map<String, Object> data = new LinkedCaseInsensitiveMap<Object>(rowData.length);
            String[] columnNames = dataMetaData.getTriggerHistory().getParsedPkColumnNames();
            Object[] objects = symmetricDialect.getPlatform().getObjectValues(
                    symmetricDialect.getBinaryEncoding(), dataMetaData.getTable(), columnNames,
                    rowData);
            testColumnNamesMatchValues(dataMetaData, columnNames, objects);
            for (int i = 0; i < columnNames.length; i++) {
                data.put(columnNames[i].toUpperCase(), objects[i]);
            }
            return data;
        } else {
            return Collections.emptyMap();
        }
    }
}
