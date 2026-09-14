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
package org.jumpmind.symmetric.db.derby;

import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Hashtable;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.derby.iapi.db.Factory;
import org.apache.derby.iapi.db.TriggerExecutionContext;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.impl.jdbc.EmbedConnection;
import org.jumpmind.db.sql.SqlUtils;

public class DerbyFunctions {
    private static final String CURRENT_CONNECTION_URL = "jdbc:default:connection";
    private static final int MAX_STRING_LENGTH = 32672;
    // Base64 will output roughly 1.37% size of input
    private static final int MAX_BINARY_LENGTH = 23700;
    private static Hashtable<String, Boolean> syncDisabledTable = new Hashtable<String, Boolean>();
    private static Hashtable<String, String> syncNodeDisabledTable = new Hashtable<String, String>();

    public static String getTransactionId() throws SQLException {
        return getLanguageConnection().getTransactionExecute().getTransactionIdString();
    }

    public static String getSessionId() throws SQLException {
        return getLanguageConnection().getDbname() + "-"
                + getLanguageConnection().getInstanceNumber();
    }

    public static int isSyncDisabled() throws SQLException {
        return syncDisabledTable.get(getSessionId()) != null ? 1 : 0;
    }

    public static String getSyncNodeDisabled() throws SQLException {
        return syncNodeDisabledTable.get(getSessionId());
    }

    public static String setSyncNodeDisabled(String nodeId) throws SQLException {
        if (nodeId == null) {
            return syncNodeDisabledTable.remove(getSessionId());
        } else {
            return syncNodeDisabledTable.put(getSessionId(), nodeId);
        }
    }

    public static int setSyncDisabled(int disabledIndicator) throws SQLException {
        if (disabledIndicator == 0) {
            syncDisabledTable.remove(getSessionId());
            return 0;
        } else {
            syncDisabledTable.put(getSessionId(), Boolean.TRUE);
            return 1;
        }
    }

    protected static int findColumnIndex(ResultSetMetaData metaData, String columnName)
            throws SQLException {
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if (columnName.equals(metaData.getColumnName(i))) {
                return i;
            }
        }
        return -1;
    }

    protected static void appendCsvString(String tableName, String[] columnNames,
            String[] pkColumnNames, ResultSet rs, StringBuilder builder) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        for (String columnName : columnNames) {
            if (StringUtils.isNotBlank(columnName)) {
                int index = findColumnIndex(metaData, columnName);
                if (index >= 0) {
                    int type = metaData.getColumnType(index);
                    switch (type) {
                        case Types.BLOB:
                            builder.append(blobToString(columnName, tableName,
                                    getPrimaryKeyWhereString(pkColumnNames, rs)));
                            builder.append(",");
                            break;
                        case Types.CLOB:
                            builder.append(clobToString(columnName, tableName,
                                    getPrimaryKeyWhereString(pkColumnNames, rs)));
                            builder.append(",");
                            break;
                        default:
                            builder.append(escape(rs.getString(index)));
                            builder.append(",");
                            break;
                    }
                } else {
                    builder.append(",");
                }
            } else {
                builder.append(",");
            }
        }
    }

    public static void insertData(int enabled, String schemaName, String prefixName,
            String tableName, String channelName, String dmlType, int triggerHistId,
            String transactionId, String externalData, String columnNames, String pkColumnNames)
            throws SQLException {
        if (enabled == 1) {
            TriggerExecutionContext context = Factory.getTriggerExecutionContext();
            String rowData = null;
            String pkData = null;
            String oldData = null;
            String[] parsedColumnNames = StringUtils.splitPreserveAllTokens(columnNames, ',');
            String[] parsedPkColumnNames = StringUtils.splitPreserveAllTokens(pkColumnNames, ',');
            if (dmlType.equals("I") || dmlType.equals("U")) {
                StringBuilder dataBuilder = new StringBuilder();
                appendCsvString(tableName, parsedColumnNames, parsedPkColumnNames,
                        context.getNewRow(), dataBuilder);
                rowData = dataBuilder.substring(0, dataBuilder.length() - 1);
            }
            if (dmlType.equals("U") || dmlType.equals("D")) {
                StringBuilder dataBuilder = new StringBuilder();
                appendCsvString(tableName, parsedColumnNames, parsedPkColumnNames,
                        context.getOldRow(), dataBuilder);
                oldData = dataBuilder.substring(0, dataBuilder.length() - 1);
                dataBuilder = new StringBuilder();
                appendCsvString(tableName, parsedPkColumnNames, parsedPkColumnNames,
                        context.getOldRow(), dataBuilder);
                pkData = dataBuilder.substring(0, dataBuilder.length() - 1);
            }
            Connection conn = DriverManager.getConnection(CURRENT_CONNECTION_URL);
            StringBuilder sql = new StringBuilder("insert into ");
            sql.append(schemaName);
            sql.append(prefixName);
            sql.append(
                    "_data (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) ");
            sql.append(" values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)");
            PreparedStatement ps = conn.prepareStatement(sql.toString());
            ps.setString(1, tableName);
            ps.setString(2, dmlType);
            ps.setLong(3, triggerHistId);
            ps.setString(4, pkData);
            ps.setString(5, rowData);
            ps.setString(6, oldData);
            ps.setString(7, channelName);
            ps.setString(8, transactionId);
            ps.setString(9, getSyncNodeDisabled());
            ps.setString(10, externalData);
            ps.executeUpdate();
            ps.close();
            conn.close();
        }
    }

    @Deprecated
    public static void insertData(String schemaName, String prefixName, String tableName, String channelName,
            String dmlType, int triggerHistId, String transactionId, String externalData,
            String pkData, String rowData, String oldRowData) throws SQLException {
        if (((dmlType.equals("I") || dmlType.equals("U")) && rowData != null)
                || dmlType.equals("D")) {
            Connection conn = DriverManager.getConnection(CURRENT_CONNECTION_URL);
            StringBuilder sql = new StringBuilder("insert into ");
            sql.append(schemaName);
            sql.append(prefixName);
            sql.append(
                    "_data (table_name, event_type, trigger_hist_id, pk_data, row_data, old_data, channel_id, transaction_id, source_node_id, external_data, create_time) ");
            sql.append(" values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)");
            PreparedStatement ps = conn.prepareStatement(sql.toString());
            ps.setString(1, tableName);
            ps.setString(2, dmlType);
            ps.setLong(3, triggerHistId);
            ps.setString(4, pkData);
            ps.setString(5, rowData);
            ps.setString(6, oldRowData);
            ps.setString(7, channelName);
            ps.setString(8, transactionId);
            ps.setString(9, getSyncNodeDisabled());
            ps.setString(10, externalData);
            ps.executeUpdate();
            ps.close();
            conn.close();
        }
    }

    public static String escape(String str) {
        if (str != null) {
            return "\"" + Strings.CS.replace(Strings.CS.replace(str, "\\", "\\\\"), "\"", "\\\"") + "\"";
        }
        return "";
    }

    public static String blobToString(String columnName, String tableName, String whereClause)
            throws SQLException {
        String str = null;
        if (StringUtils.isNotBlank(whereClause)) {
            Connection conn = DriverManager.getConnection(CURRENT_CONNECTION_URL);
            String sql = "select " + SqlUtils.sanitizeIdentifier(columnName) + " from " + SqlUtils.sanitizeIdentifier(tableName)
                    + " where " + whereClause;
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                byte[] bytes = null;
                int type = rs.getMetaData().getColumnType(1);
                if (type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY) {
                    bytes = rs.getBytes(1);
                } else {
                    Blob blob = rs.getBlob(1);
                    if (blob != null) {
                        bytes = blob.getBytes(1, MAX_BINARY_LENGTH);
                    }
                }
                if (bytes != null) {
                    str = new String(Base64.encodeBase64(bytes), Charset.defaultCharset());
                }
            }
            ps.close();
            conn.close();
        }
        return str == null ? "" : "\"" + str + "\"";
    }

    public static String clobToString(String columnName, String tableName, String whereClause)
            throws SQLException {
        String str = null;
        if (StringUtils.isNotBlank(whereClause)) {
            Connection conn = DriverManager.getConnection(CURRENT_CONNECTION_URL);
            String sql = "select " + SqlUtils.sanitizeIdentifier(columnName) + " from " + SqlUtils.sanitizeIdentifier(tableName)
                    + " where " + whereClause;
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Clob clob = rs.getClob(1);
                if (clob != null) {
                    str = clob.getSubString(1, MAX_STRING_LENGTH);
                }
            }
            ps.close();
            conn.close();
        }
        return str == null ? "" : escape(str);
    }

    public static String getPrimaryKeyWhereString(String[] pkColumnNames, ResultSet rs)
            throws SQLException {
        final String AND = " and ";
        ResultSetMetaData metaData = rs.getMetaData();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < pkColumnNames.length; i++) {
            String columnName = SqlUtils.sanitizeIdentifier(pkColumnNames[i]);
            int index = findColumnIndex(metaData, columnName);
            int type = metaData.getColumnType(index);
            if (type != Types.BINARY && type != Types.BLOB && type != Types.LONGVARBINARY
                    && type != Types.VARBINARY) {
                b.append("\"").append(columnName).append("\"=");
                switch (type) {
                    case Types.BIT:
                    case Types.TINYINT:
                    case Types.SMALLINT:
                    case Types.INTEGER:
                    case Types.BIGINT:
                    case Types.FLOAT:
                    case Types.REAL:
                    case Types.DOUBLE:
                    case Types.NUMERIC:
                    case Types.DECIMAL:
                    case Types.BOOLEAN:
                        b.append(rs.getObject(index));
                        break;
                    case Types.CHAR:
                    case Types.VARCHAR:
                    case Types.LONGVARCHAR:
                        b.append("'").append(SqlUtils.escapeString(rs.getString(index))).append("'");
                        break;
                    case Types.DATE:
                        b.append("{d '");
                        b.append(SqlUtils.escapeString(rs.getString(index)));
                        b.append("'}");
                        break;
                    case Types.TIMESTAMP:
                        b.append("{ts '");
                        b.append(SqlUtils.escapeString(rs.getString(index)));
                        b.append("'}");
                        break;
                }
                b.append(AND);
            }
        }
        b.replace(b.length() - AND.length(), b.length(), "");
        return b.toString();
    }

    private static LanguageConnectionContext getLanguageConnection() throws SQLException {
        EmbedConnection conn = (EmbedConnection) DriverManager
                .getConnection(CURRENT_CONNECTION_URL);
        return conn.getLanguageConnection();
    }
}