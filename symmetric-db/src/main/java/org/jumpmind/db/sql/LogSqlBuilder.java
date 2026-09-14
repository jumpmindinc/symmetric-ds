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
package org.jumpmind.db.sql;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class LogSqlBuilder {
    private final static Logger log = LoggerFactory.getLogger(LogSqlBuilder.class);
    public static final int MAX_FIELD_SIZE_TO_PRINT_TO_LOG = 8000;
    protected BinaryEncoding encoding = BinaryEncoding.HEX;
    protected boolean useJdbcTimestampFormat = true;
    protected int logSlowSqlThresholdMillis = 20000;
    protected int consoleLogSlowSqlThresholdMillis = 5000;
    protected boolean logSqlParametersInline = true;

    public void logSql(Logger loggerArg, String sql, Object[] args, int[] types, long executionTimeMillis) {
        logSql(loggerArg, null, sql, args, types, executionTimeMillis);
    }

    public void logSql(Logger loggerArg, String message, String sql, Object[] args, int[] types, long executionTimeMillis) {
        boolean longRunning = false;
        if ("gui".equals(MDC.get("engineName"))) {
            longRunning = executionTimeMillis >= consoleLogSlowSqlThresholdMillis;
        } else {
            longRunning = executionTimeMillis >= logSlowSqlThresholdMillis;
        }
        if (loggerArg.isDebugEnabled() || longRunning) {
            StringBuilder logEntry = new StringBuilder();
            if (longRunning) {
                logEntry.append("Long Running: ");
            }
            logEntry.append("(" + executionTimeMillis + "ms.) ");
            if (message != null) {
                logEntry.append(message).append(" ");
            }
            if (logSqlParametersInline) {
                logEntry.append(buildDynamicSqlForLog(sql, args, types));
            } else {
                logEntry.append(sql);
            }
            // Development support: Allow a logger like "org.jumpmind.db.sql.JdbcSqlTemplate=TRACE"
            if (loggerArg.isTraceEnabled()) {
                logEntry.append("\r\n");
                // logEntry.append(AppUtils.formatStackTrace(Thread.currentThread().getStackTrace(), 8, true));
            }
            if (longRunning) {
                loggerArg.info(logEntry.toString());
            } else {
                loggerArg.debug(logEntry.toString());
            }
            if (!logSqlParametersInline && args != null && args.length > 0) {
                if (longRunning) {
                    loggerArg.info("sql args: {}", Arrays.toString(args));
                } else {
                    loggerArg.debug("sql args: {}", Arrays.toString(args));
                }
            }
        }
    }

    public SQLException logSqlAfterException(Logger loggerArg, String sql, Object[] args, SQLException e) {
        String msg = "SQL caused exception: [" + sql + "] ";
        if (args != null && args.length > 0) {
            msg += "sql args: " + Arrays.toString(args) + " ";
        }
        loggerArg.debug(msg + e);
        return e;
    }

    public String buildDynamicSqlForLog(String sql, Object[] args, int[] types) {
        boolean endsWithPlacholder = sql.endsWith("?");
        String[] parts = sql.split("\\?");
        if (endsWithPlacholder) {
            parts = (String[]) ArrayUtils.addAll(parts, new String[] { "" });
        }
        if (parts.length == 1 || args == null || args.length == 0) {
            return sql;
        }
        StringBuilder dynamicSql = new StringBuilder(256);
        for (int i = 0; i < parts.length; i++) {
            dynamicSql.append(parts[i]);
            if (args.length > i) {
                int type = (types != null && types.length > i) ? types[i] : Types.OTHER;
                dynamicSql.append(formatValue(args[i], type));
            }
        }
        return dynamicSql.toString();
    }

    public String formatValue(Object object, int type) {
        if (object == null) {
            return "null";
        }
        if (TypeMap.isTextType(type)) {
            return StringUtils.abbreviate(formatStringValue(object), MAX_FIELD_SIZE_TO_PRINT_TO_LOG);
        } else if (TypeMap.isDateTimeType(type)) {
            return formatDateTimeValue(object, type);
        } else if (TypeMap.isBinaryType(type) || object instanceof byte[] || object instanceof Blob) {
            return StringUtils.abbreviate(formatBinaryValue(object), MAX_FIELD_SIZE_TO_PRINT_TO_LOG);
        } else {
            return StringUtils.abbreviate(formatUnknownType(object), MAX_FIELD_SIZE_TO_PRINT_TO_LOG);
        }
    }

    protected String formatUnknownType(Object object) {
        if (object instanceof Integer || object instanceof Long || object instanceof BigDecimal
                || object instanceof Boolean || object instanceof Short || object instanceof Float ||
                object instanceof Double) {
            return object.toString();
        } else if (object instanceof Date) {
            return formatDateTimeValue(object, Types.TIMESTAMP);
        } else {
            return formatStringValue(object);
        }
    }

    protected String formatStringValue(Object object) {
        String value = object.toString();
        value = value.replace("'", "''");
        return "'" + value + "'";
    }

    protected String formatDateTimeValue(Object object, int type) {
        Date date = null;
        if (object instanceof Date) {
            date = (Date) object;
        } else {
            try {
                date = FormatUtils.parseDate(object.toString(),
                        (String[]) ArrayUtils.addAll(FormatUtils.TIMESTAMP_PATTERNS, FormatUtils.TIME_PATTERNS));
            } catch (Exception ex) {
                try {
                    // Try Timestamp with time zone
                    date = FormatUtils.parseTimestampWithTimezone(object == null ? "" : object.toString());
                } catch (Exception e) {
                    log.debug("Failed to parse argument as a date " + object + " " + ex);
                    return "'" + object + "'";
                }
            }
        }
        if (object instanceof Timestamp) {
            if (type == Types.TIME) {
                return (useJdbcTimestampFormat ? "{t " : "")
                        + "'" + FormatUtils.TIME9_FORMATTER.format(((Timestamp) object).toInstant()) + "'"
                        + (useJdbcTimestampFormat ? "}" : "");
            }
            return (useJdbcTimestampFormat ? "{ts " : "")
                    + "'" + FormatUtils.TIMESTAMP9_FORMATTER.format(((Timestamp) object).toInstant()) + "'"
                    + (useJdbcTimestampFormat ? "}" : "");
        } else if (object instanceof Time) {
            return (useJdbcTimestampFormat ? "{t " : "")
                    + "'" + FormatUtils.TIME9_FORMATTER.format(new Date(((Time) object).getTime()).toInstant()) + "'"
                    + (useJdbcTimestampFormat ? "}" : "");
        } else {
            return (useJdbcTimestampFormat ? "{ts " : "")
                    + "'" + FormatUtils.TIMESTAMP_FORMATTER.format(date) + "'"
                    + (useJdbcTimestampFormat ? "}" : "");
        }
    }

    protected String formatBinaryValue(Object object) {
        byte[] bytes = null;
        if (object instanceof byte[]) {
            bytes = (byte[]) object;
        } else if (object instanceof Blob) {
            try {
                bytes = IOUtils.toByteArray(((Blob) object).getBinaryStream());
            } catch (Exception ex) {
                log.debug("Failed to convert to byte array " + object + " " + ex);
                return "'" + object + "'";
            }
        } else {
            return "'" + object + "'";
        }
        if (encoding == BinaryEncoding.BASE64) {
            return "'" + new String(Base64.encodeBase64(bytes), Charset.defaultCharset()) + "'";
        } else if (encoding == BinaryEncoding.HEX) {
            return "'" + new String(Hex.encodeHex(bytes)) + "'";
        }
        return "'" + object + "'";
    }

    public boolean isUseJdbcTimestampFormat() {
        return useJdbcTimestampFormat;
    }

    public void setUseJdbcTimestampFormat(boolean useJdbcTimestampFormat) {
        this.useJdbcTimestampFormat = useJdbcTimestampFormat;
    }

    public int getLogSlowSqlThresholdMillis() {
        return logSlowSqlThresholdMillis;
    }

    public void setLogSlowSqlThresholdMillis(int logSlowSqlThresholdMillis) {
        this.logSlowSqlThresholdMillis = logSlowSqlThresholdMillis;
    }

    public int getConsoleLogSlowSqlThresholdMillis() {
        return consoleLogSlowSqlThresholdMillis;
    }

    public void setConsoleLogSlowSqlThresholdMillis(int consoleLogSlowSqlThresholdMillis) {
        this.consoleLogSlowSqlThresholdMillis = consoleLogSlowSqlThresholdMillis;
    }

    public boolean isLogSqlParametersInline() {
        return logSqlParametersInline;
    }

    public void setLogSqlParametersInline(boolean logSqlParametersInline) {
        this.logSqlParametersInline = logSqlParametersInline;
    }
}
