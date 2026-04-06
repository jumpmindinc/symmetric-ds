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
package org.jumpmind.symmetric.db;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.time.FastDateFormat;
import org.h2.jdbc.JdbcBlob;
import org.h2.jdbc.JdbcClob;
import org.jumpmind.symmetric.db.h2.H2Trigger;
import org.jumpmind.symmetric.db.hsqldb.HsqlDbTrigger;

/**
 * An implementation of logic that can be used in Java database triggers to capture data for SymmetricDS.
 * 
 * @see H2Trigger
 * @see HsqlDbTrigger
 *
 * 
 */
abstract public class AbstractEmbeddedTrigger {
    protected static final char[] HEX = "0123456789abcdef".toCharArray();
    protected static final FastDateFormat DATE_FORMATTER = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");
    protected static final String KEY_CONDITION_SQL = "CONDITION_SQL";
    protected static final String KEY_INSERT_DATA_SQL = "INSERT_DATA_SQL";
    protected static final String TEMPLATE_TABLE_SUFFIX = "_CONFIG";
    protected String triggerName;
    protected String schemaName;
    protected Map<String, String> templates = null;

    /**
     * This method should be called by the database engine once when initializing the trigger.
     * 
     * @param conn
     *            a connection to the database
     * @param triggerName
     *            the name of the trigger used in the CREATE TRIGGER statement
     * @param schemaName
     *            TODO
     * @param tableName
     *            the name of the table
     */
    protected void init(Connection conn, String triggerName, String schemaName, String tableName) throws SQLException {
        if (this.templates == null) {
            this.schemaName = schemaName;
            this.triggerName = triggerName;
            this.templates = getTemplates(conn);
        }
        if (templates == null || templates.size() == 0) {
            throw new IllegalStateException(String.format(
                    "The '%s' SymmetricDS trigger is in an invalid state.  It needs to be dropped.", triggerName));
        }
    }

    /**
     * This method is called for each triggered action.
     * 
     * @param conn
     *            a connection to the database
     * @param oldRow
     *            the old row, or null if no old row is available (for INSERT)
     * @param newRow
     *            the new row, or null if no new row is available (for DELETE)
     * @throws SQLException
     *             if the operation must be undone
     */
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        String sql = null;
        try (Statement stmt = conn.createStatement()) {
            sql = fillVirtualTableSql(templates.get(KEY_CONDITION_SQL), oldRow, newRow);
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next() && rs.getInt(1) > 0) {
                rs.close();
                sql = fillVirtualTableSql(templates.get(KEY_INSERT_DATA_SQL), oldRow, newRow);
                stmt.executeUpdate(sql);
            }
        } catch (SQLException ex) {
            System.err.println("This sql failed: " + sql);
            Throwable rootException = ex;
            while (rootException.getCause() != null && !rootException.getCause().equals(ex)) {
                rootException = ex.getCause();
            }
            rootException.printStackTrace();
            throw ex;
        }
    }

    protected String fillVirtualTableSql(String sql, Object[] oldRow, Object[] newRow) throws SQLException {
        int columnCount = oldRow != null ? oldRow.length : newRow.length;
        StringBuilder out = new StringBuilder();
        String[] tokens = sql.split("\\?");
        int tokenIndex = 0;
        tokenIndex = forEachColumn(columnCount, newRow, out, tokenIndex, tokens);
        tokenIndex = forEachColumn(columnCount, oldRow, out, tokenIndex, tokens);
        out.append(tokens[tokenIndex]);
        return out.toString();
    }

    private int forEachColumn(int columnCount, Object[] data, StringBuilder out, int tokenIndex, String[] tokens) {
        for (int i = 0; i < columnCount; i++) {
            out.append(tokens[tokenIndex++]);
            if (data != null) {
                data[i] = appendVirtualTableStringValue(data[i], out);
            } else {
                out.append("null");
            }
        }
        return tokenIndex;
    }

    protected Object appendVirtualTableStringValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String || value instanceof Reader || value instanceof JdbcClob) {
            if (value instanceof Reader) {
                try {
                    value = readStringAndClose((Reader) value, -1);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (value instanceof JdbcClob) {
                try {
                    value = readStringAndClose(((JdbcClob) value).getCharacterStream(), -1);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            out.append("'");
            out.append(escapeString(value));
            out.append("'");
        } else if (value instanceof Number) {
            out.append(value);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof InputStream) {
            out.append("'");
            try {
                value = convertBytesToString(readBytesAndClose((InputStream) value, -1));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            out.append(escapeString(value));
            out.append("'");
        } else if (value instanceof JdbcBlob) {
            out.append("'");
            try {
                value = new String(readBytesAndClose(((JdbcBlob) value).getBinaryStream(), -1), StandardCharsets.UTF_8);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            out.append(escapeString(value));
            out.append("'");
        } else if (value instanceof Date) {
            out.append("'");
            out.append(DATE_FORMATTER.format(value));
            out.append("'");
        } else if (value instanceof LocalDate) {
            out.append("DATE '");
            out.append(((LocalDate) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            out.append("'");
        } else if (value instanceof LocalTime) {
            out.append("TIME '");
            out.append(((LocalTime) value).format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
            out.append("'");
        } else if (value instanceof LocalDateTime) {
            out.append("TIMESTAMP '");
            out.append(((LocalDateTime) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")));
            out.append("'");
        } else if (value instanceof byte[]) {
            out.append("'");
            value = convertBytesToString((byte[]) value, ((byte[]) value).length);
            out.append(escapeString(value));
            out.append("'");
        } else {
            throw new IllegalStateException(String.format("Type not supported: %s", value.getClass().getName()));
        }
        return value;
    }

    protected String readStringAndClose(Reader paramReader, int paramInt) throws IOException {
        try {
            if (paramInt <= 0) {
                paramInt = 2147483647;
            }
            int i = Math.min(4096, paramInt);
            StringWriter localStringWriter = new StringWriter((paramInt == 2147483647) ? i : paramInt);
            char[] arrayOfChar = new char[i];
            while (paramInt > 0) {
                int j = Math.min(i, paramInt);
                j = paramReader.read(arrayOfChar, 0, j);
                if (j < 0) {
                    break;
                }
                localStringWriter.write(arrayOfChar, 0, j);
                paramInt -= j;
            }
            String str = localStringWriter.toString();
            return str;
        } finally {
            paramReader.close();
        }
    }

    public static byte[] readBytesAndClose(InputStream paramInputStream, int paramInt) throws IOException {
        try {
            if (paramInt <= 0) {
                paramInt = 2147483647;
            }
            int i = Math.min(4096, paramInt);
            ByteArrayOutputStream localByteArrayOutputStream = new ByteArrayOutputStream(i);
            byte[] arrayOfByte1 = new byte[i];
            while (paramInt > 0) {
                int j = Math.min(i, paramInt);
                j = paramInputStream.read(arrayOfByte1, 0, j);
                if (j < 0) {
                    break;
                }
                localByteArrayOutputStream.write(arrayOfByte1, 0, j);
                paramInt -= j;
            }
            byte[] arrayOfByte2 = localByteArrayOutputStream.toByteArray();
            return arrayOfByte2;
        } finally {
            paramInputStream.close();
        }
    }

    public static String convertBytesToString(byte[] paramArrayOfByte) {
        return convertBytesToString(paramArrayOfByte, paramArrayOfByte.length);
    }

    public static String convertBytesToString(byte[] paramArrayOfByte, int paramInt) {
        char[] arrayOfChar1 = new char[paramInt + paramInt];
        char[] arrayOfChar2 = HEX;
        for (int i = 0; i < paramInt; ++i) {
            int j = paramArrayOfByte[i] & 0xFF;
            arrayOfChar1[(i + i)] = arrayOfChar2[(j >> 4)];
            arrayOfChar1[(i + i + 1)] = arrayOfChar2[(j & 0xF)];
        }
        return new String(arrayOfChar1);
    }

    protected String escapeString(Object val) {
        return val == null ? null : val.toString().replaceAll("'", "''");
    }

    protected Map<String, String> getTemplates(Connection conn) throws SQLException {
        Map<String, String> templates = new HashMap<String, String>();
        try (Statement stmt = conn.createStatement()) {
            String schemaPrefix = schemaName != null && schemaName.length() > 0 ? "\"" + schemaName + "\"." : "";
            ResultSet rs = stmt.executeQuery(String.format("select * from %s%s%s", schemaPrefix, triggerName, TEMPLATE_TABLE_SUFFIX));
            if (rs.next()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    templates.put(metaData.getColumnName(i).toUpperCase(), rs.getString(i));
                }
                return templates;
            } else {
                throw new SQLException(String.format("%s is in an invalid state.  %s%s did not return a row.", triggerName,
                        triggerName, TEMPLATE_TABLE_SUFFIX));
            }
        }
    }
}