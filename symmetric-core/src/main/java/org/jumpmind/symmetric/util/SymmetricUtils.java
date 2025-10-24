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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Transaction;
import org.jumpmind.db.sql.SqlScriptReader;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataReader;
import org.jumpmind.symmetric.io.data.reader.ProtocolDataReader;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.transport.http.HttpConnection;
import org.jumpmind.symmetric.transport.internal.InternalIncomingTransport;
import org.jumpmind.util.AppUtils;
import org.jumpmind.util.CollectionUtils;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bsh.EvalError;
import bsh.Interpreter;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;

final public class SymmetricUtils {
    private static final Logger log = LoggerFactory.getLogger(SymmetricUtils.class);
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

    public static boolean importContainsCurrentGroup(ISymmetricEngine engine, String importedContent, boolean isCsv) {
        return importContainsCurrentGroup(engine, new StringReader(importedContent), isCsv);
    }

    public static boolean importContainsCurrentGroup(ISymmetricEngine engine, URL importedContentUrl, boolean isCsv) {
        try {
            return importContainsCurrentGroup(engine, new InputStreamReader(importedContentUrl.openStream(), StandardCharsets.UTF_8.name()), isCsv);
        } catch (IOException e) {
            log.warn("Unable to read imported configuration, so assuming it includes the {} group", engine.getParameterService().getNodeGroupId());
            log.debug("Exception: ", e);
            return true;
        }
    }

    public static boolean importContainsCurrentGroup(ISymmetricEngine engine, Reader importedContentReader, boolean isCsv) {
        boolean foundGroup = false;
        String groupId = engine.getParameterService().getNodeGroupId();
        String groupTableName = TableConstants.getTableName(engine.getTablePrefix(), TableConstants.SYM_NODE_GROUP);
        if (isCsv) {
            IDataReader dataReader = null;
            try {
                dataReader = new ProtocolDataReader(BatchType.LOAD, engine.getNodeId(),
                        new InternalIncomingTransport(new BufferedReader(importedContentReader)).openReader(), false);
                dataReader.open(new DataContext());
                dataReader.nextBatch();
                Table table = dataReader.nextTable();
                tableLoop: while (table != null) {
                    if (Strings.CI.equals(table.getName(), groupTableName)) {
                        CsvData data = dataReader.nextData();
                        while (data != null) {
                            if (DataEventType.INSERT.equals(data.getDataEventType())) {
                                if (Strings.CS.equals(groupId, data.toKeyColumnValuePairs(table).get("node_group_id"))) {
                                    foundGroup = true;
                                    break tableLoop;
                                }
                            }
                            data = dataReader.nextData();
                        }
                    }
                    table = dataReader.nextTable();
                }
            } catch (IOException e) {
                log.warn("Unable to read imported CSV data, so assuming it includes the {} group", groupId);
                log.debug("Exception: ", e);
                foundGroup = true;
            } finally {
                if (dataReader != null) {
                    dataReader.close();
                }
            }
        } else {
            SqlScriptReader sqlReader = new SqlScriptReader(importedContentReader, false);
            String sql = sqlReader.readSqlStatement();
            sqlLoop: while (sql != null) {
                try {
                    Statement statement = CCJSqlParserUtil.parse(sql);
                    if (statement instanceof Insert insert
                            && Strings.CI.equalsAny(insert.getTable().getName(), groupTableName, "\"" + groupTableName + "\"")) {
                        ExpressionList<?> valueList = insert.getValues().getExpressions();
                        if (valueList != null && !valueList.isEmpty()) {
                            List<Column> columnList = insert.getColumns();
                            if (columnList != null && !columnList.isEmpty()) {
                                for (int i = 0; i < columnList.size() && i < valueList.size(); i++) {
                                    if (Strings.CI.equalsAny(columnList.get(i).getColumnName(), "node_group_id", "\"node_group_id\"")
                                            && Strings.CS.equals(valueList.get(i).getASTNode().jjtGetValue().toString(), "'" + groupId + "'")) {
                                        foundGroup = true;
                                        break sqlLoop;
                                    }
                                }
                            } else if (Strings.CS.equals(valueList.get(0).getASTNode().jjtGetValue().toString(), "'" + groupId + "'")) {
                                foundGroup = true;
                                break;
                            }
                        }
                    }
                } catch (JSQLParserException e) {
                    if (Strings.CI.contains(sql, "insert") && Strings.CI.contains(sql, groupTableName) && sql.contains(groupId)) {
                        log.warn("Unable to parse the following imported SQL, so assuming it's an insert for the {} node group: {}", groupId, sql);
                        log.debug("Parse exception: ", e);
                        foundGroup = true;
                        break;
                    }
                }
                sql = sqlReader.readSqlStatement();
            }
            try {
                sqlReader.close();
            } catch (IOException e) {
                log.warn("Failed to close reader after reading imported SQL", e);
            }
        }
        return foundGroup;
    }

    public static String removeInvalidTablesFromImportedSql(String tablePrefix, String sql) throws IOException {
        return removeInvalidTablesFromImportedSql(tablePrefix, new SqlScriptReader(new StringReader(sql)));
    }

    public static String removeInvalidTablesFromImportedSql(String tablePrefix, SqlScriptReader sqlReader) throws IOException {
        try {
            StringBuilder sqlBuilder = new StringBuilder();
            String[] invalidTableNames = TableConstants.getRemovedConfigTables(tablePrefix).toArray(String[]::new);
            String sqlStatement;
            while ((sqlStatement = sqlReader.readSqlStatement()) != null) {
                if (!Strings.CI.containsAny(sqlStatement, invalidTableNames)) {
                    sqlBuilder.append(sqlStatement).append(";").append(System.lineSeparator());
                }
            }
            return sqlBuilder.toString();
        } finally {
            sqlReader.close();
        }
    }

    public static Certificate[] getCertificates(String urlString)
            throws MalformedURLException, IOException, NoSuchAlgorithmException, KeyManagementException {
        URL url = new URL(urlString);
        if (!"https".equals(url.getProtocol())) {
            return null;
        }
        HttpConnection connection = null;
        try {
            connection = new HttpConnection(url);
            connection.setHostnameVerifier((string, session) -> true);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            X509TrustManager trustManager = new X509TrustManager() {
                private X509Certificate[] accepted = {};

                @Override
                public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                    accepted = xcs;
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return accepted;
                }
            };
            sslContext.init(null, new TrustManager[] { trustManager }, null);
            connection.setSslSocketFactory(sslContext.getSocketFactory());
            return connection.getServerCertificates();
        } finally {
            if (connection != null) {
                connection.disconnect();
                connection.close();
            }
        }
    }
}
