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
package org.jumpmind.db.platform.ase;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Types;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.platform.AbstractJdbcDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.PermissionResult;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.platform.PermissionType;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.db.util.BinaryEncoding;

/*
 * The platform implementation for Sybase.
 */
public class AseDatabasePlatform extends AbstractJdbcDatabasePlatform {
    /* The standard Sybase jdbc driver. */
    public static final String JDBC_DRIVER = "com.sybase.jdbc4.jdbc.SybDriver";
    /* The old Sybase jdbc driver. */
    public static final String JDBC_DRIVER_OLD = "com.sybase.jdbc4.jdbc.SybDriver";
    /* The subprotocol used by the standard Sybase driver. */
    public static final String JDBC_SUBPROTOCOL = "sybase:Tds";
    /* The maximum size that text and binary columns can have. */
    public static final long MAX_TEXT_SIZE = 2147483647;
    private Map<String, String> sqlScriptReplacementTokens;
    private boolean usingJtds;

    public AseDatabasePlatform(DataSource dataSource, SqlTemplateSettings settings) {
        super(dataSource, settings);
        sqlScriptReplacementTokens = super.getSqlScriptReplacementTokens();
        if (sqlScriptReplacementTokens == null) {
            sqlScriptReplacementTokens = new HashMap<String, String>();
        }
        sqlScriptReplacementTokens.put("current_timestamp", "getdate()");
        this.useMultiThreadSyncTriggers = false;
        if (getSqlTemplate().getDatabaseMajorVersion() <= 12) {
            ddlBuilder.getDatabaseInfo().setMaxIdentifierLength(30);
        }
    }

    @Override
    protected AseDdlBuilder createDdlBuilder() {
        String dbUrl = settings.getProperties().get(DataSourceProperties.DB_POOL_URL);
        usingJtds = dbUrl != null && dbUrl.startsWith("jdbc:jtds");
        AseDdlBuilder ddlBuilder = new AseDdlBuilder();
        ddlBuilder.setUsingJtds(usingJtds);
        return ddlBuilder;
    }

    @Override
    protected AseDdlReader createDdlReader() {
        return new AseDdlReader(this);
    }

    @Override
    protected AseJdbcSqlTemplate createSqlTemplate() {
        return new AseJdbcSqlTemplate(dataSource, settings, null, getDatabaseInfo());
    }

    @Override
    public String getName() {
        return DatabaseNamesConstants.ASE;
    }

    @Override
    public String getClassName() {
        return AseDatabasePlatform.class.getName();
    }

    @Override
    public String getDefaultCatalog() {
        if (StringUtils.isBlank(defaultCatalog)) {
            defaultCatalog = getSqlTemplate().queryForObject("select DB_NAME()", String.class);
        }
        return defaultCatalog;
    }

    @Override
    public String getDefaultSchema() {
        if (StringUtils.isBlank(defaultSchema)) {
            defaultSchema = getSqlTemplate().queryForObject("select USER_NAME()", String.class);
        }
        return defaultSchema;
    }

    @Override
    public Map<String, String> getSqlScriptReplacementTokens() {
        return sqlScriptReplacementTokens;
    }

    @Override
    public boolean canColumnBeUsedInWhereClause(Column column) {
        return !isLob(column) && super.canColumnBeUsedInWhereClause(column);
    }

    @Override
    public PermissionResult getCreateSymTriggerPermission() {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        String triggerSql = "create trigger TEST_TRIGGER on " + delimiter + PERMISSION_TEST_TABLE_NAME + delimiter
                + " for insert as begin select 1 end";
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TRIGGER, triggerSql);
        try {
            getSqlTemplate().update(triggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE TRIGGER permission or TRIGGER permission");
        }
        return result;
    }

    @Override
    protected Object getObjectValue(String value, Column column, BinaryEncoding encoding, boolean useVariableDates, boolean fitToColumn)
            throws DecoderException {
        Object objectValue = value;
        String typeName = column.getJdbcTypeName();
        if (typeName.equalsIgnoreCase("unichar") || typeName.equalsIgnoreCase("unitext") || typeName.equalsIgnoreCase("univarchar")) {
            String stringValue = cleanTextForTextBasedColumns((String) objectValue);
            int size = column.getSizeAsInt();
            if (settings.isRightTrimCharValues()) {
                stringValue = StringUtils.stripEnd(stringValue, null);
            }
            if (fitToColumn && size > 0 && stringValue != null && stringValue.length() > size) {
                stringValue = stringValue.substring(0, size);
            }
            objectValue = stringValue;
            if (stringValue != null && typeName.equalsIgnoreCase("unitext")) {
                objectValue = stringValue.getBytes(StandardCharsets.UTF_16LE);
            }
            return objectValue;
        } else {
            return super.getObjectValue(value, column, encoding, useVariableDates, fitToColumn);
        }
    }

    @Override
    public String getCsvStringValue(BinaryEncoding encoding, Column[] metaData, Row row, boolean[] isColumnPositionUsingTemplate) {
        StringBuilder concatenatedRow = new StringBuilder();
        Set<String> names = row.keySet();
        int i = 0;
        for (String name : names) {
            Column column = metaData[i];
            int type = column.getJdbcTypeCode();
            if (i > 0) {
                concatenatedRow.append(",");
            }
            if (row.get(name) != null) {
                if (column.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                    concatenatedRow.append("\"").append(row.getString(name).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                } else if (isColumnPositionUsingTemplate[i]) {
                    concatenatedRow.append(row.getString(name));
                } else if (type == Types.BOOLEAN || type == Types.BIT) {
                    concatenatedRow.append(row.getBoolean(name) ? "1" : "0");
                } else if (column.isOfNumericType()) {
                    concatenatedRow.append(row.getString(name));
                } else if (column.isTimestampWithTimezone()) {
                    appendString(concatenatedRow, getTimestampTzStringValue(name, type, row, false));
                } else if (type == Types.DATE || type == Types.TIME) {
                    appendString(concatenatedRow, getDateTimeStringValue(name, type, row, false));
                } else if (type == Types.TIMESTAMP) {
                    appendString(concatenatedRow, getTimestampStringValue(name, type, row, false));
                } else if (column.isOfBinaryType()) {
                    byte[] bytes = row.getBytes(name);
                    if (bytes.length == 0) {
                        concatenatedRow.append("\"\"");
                    } else if (encoding == BinaryEncoding.NONE) {
                        concatenatedRow.append(row.getString(name));
                    } else if (encoding == BinaryEncoding.BASE64) {
                        concatenatedRow.append(new String(Base64.encodeBase64(bytes), Charset.defaultCharset()));
                    } else if (encoding == BinaryEncoding.HEX) {
                        concatenatedRow.append(new String(Hex.encodeHex(bytes)));
                    }
                } else {
                    concatenatedRow.append("\"").append(row.getString(name).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                }
            }
            i++;
        }
        return concatenatedRow.toString();
    }

    @Override
    public String[] getStringValues(BinaryEncoding encoding, Column[] metaData, Row row, boolean useVariableDates, boolean indexByPosition) {
        String[] values = new String[metaData.length];
        Set<String> keys = row.keySet();
        int i = 0;
        for (String key : keys) {
            Column column = metaData[i];
            String name = indexByPosition ? key : column.getName();
            int type = column.getJdbcTypeCode();
            if (row.get(name) != null) {
                if (column.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                    values[i] = row.getString(name);
                } else if (type == Types.BOOLEAN || type == Types.BIT) {
                    values[i] = row.getBoolean(name) ? "1" : "0";
                } else if (column.isOfNumericType()) {
                    values[i] = row.getString(name);
                } else if (!column.isTimestampWithTimezone() && (type == Types.DATE || type == Types.TIME)) {
                    values[i] = getDateTimeStringValue(name, type, row, useVariableDates);
                } else if (!column.isTimestampWithTimezone() && type == Types.TIMESTAMP) {
                    values[i] = getTimestampStringValue(name, type, row, useVariableDates);
                } else if (column.isOfBinaryType()) {
                    byte[] bytes = row.getBytes(name);
                    if (encoding == BinaryEncoding.NONE) {
                        values[i] = row.getString(name);
                    } else if (encoding == BinaryEncoding.BASE64) {
                        values[i] = new String(Base64.encodeBase64(bytes), Charset.defaultCharset());
                    } else if (encoding == BinaryEncoding.HEX) {
                        values[i] = new String(Hex.encodeHex(bytes));
                    }
                } else {
                    values[i] = row.getString(name);
                }
            }
            i++;
        }
        return values;
    }
}
