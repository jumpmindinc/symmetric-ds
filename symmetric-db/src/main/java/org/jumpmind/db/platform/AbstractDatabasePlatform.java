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
package org.jumpmind.db.platform;

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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.sql.Array;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.io.DatabaseXmlUtil;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Transaction;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.PermissionResult.Status;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.DmlStatementOptions;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.SqlScript;
import org.jumpmind.db.sql.SqlScriptReader;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.IoException;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Base class for platform implementations.
 */
public abstract class AbstractDatabasePlatform implements IDatabasePlatform {
    /* The log for this platform. */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    public static final String REQUIRED_FIELD_NULL_SUBSTITUTE = " ";
    public static final String ZERO_DATE_STRING = "0000-00-00 00:00:00";
    /*
     * The default name for models read from the database, if no name as given.
     */
    protected static final String MODEL_DEFAULT_NAME = "default";
    protected static final String PERMISSION_TEST_TABLE_NAME = "SYM_PERMISSION_TEST";
    /* The model reader for this platform. */
    protected IDdlReader ddlReader;
    protected IDdlBuilder ddlBuilder;
    protected Map<String, Table> tableCache = Collections.synchronizedMap(new HashMap<String, Table>());
    private long lastTimeCachedModelClearedInMs = System.currentTimeMillis();
    protected long clearCacheModelTimeoutInMs = DateUtils.MILLIS_PER_HOUR;
    protected String defaultSchema;
    protected String defaultCatalog;
    protected Boolean storesUpperCaseIdentifiers;
    protected Boolean storesLowerCaseIdentifiers;
    protected Boolean storesMixedCaseIdentifiers;
    protected boolean metadataIgnoreCase = true;
    protected boolean useMultiThreadSyncTriggers = true;
    protected SqlTemplateSettings settings;
    protected Boolean supportsTransactions;
    protected Boolean supportsMultiThreadedTransactions;
    protected boolean supportsTruncate = true;
    protected String sourceNodeId;
    protected DatabaseVersion databaseVersion;

    public AbstractDatabasePlatform(SqlTemplateSettings settings) {
        this.settings = settings;
    }

    @Override
    public void shutdown() {
        // Default implementation does nothing. Override at the specific platforms as needed.
    }

    @Override
    public DatabaseInfo getDatabaseInfo() {
        return getDdlBuilder().getDatabaseInfo();
    }

    @Override
    abstract public ISqlTemplate getSqlTemplate();

    @Override
    abstract public ISqlTemplate getSqlTemplateDirty();

    @Override
    public DmlStatement createDmlStatement(DmlType dmlType, Table table, String textColumnExpression) {
        return createDmlStatement(dmlType, table.getCatalog(), table.getSchema(), table.getName(), table.getPrimaryKeyColumns(),
                table.getColumns(), null, textColumnExpression);
    }

    @Override
    public DmlStatement createDmlStatement(DmlType dmlType, String catalogName, String schemaName, String tableName, Column[] keys,
            Column[] columns, boolean[] nullKeyValues, String textColumnExpression) {
        return createDmlStatement(dmlType, catalogName, schemaName, tableName, keys, columns, nullKeyValues, textColumnExpression, false);
    }

    @Override
    public DmlStatement createDmlStatement(DmlType dmlType, String catalogName, String schemaName, String tableName, Column[] keys,
            Column[] columns, boolean[] nullKeyValues, String textColumnExpression, boolean namedParameters) {
        DmlStatementOptions options = new DmlStatementOptions(dmlType, tableName).databaseInfo(getDatabaseInfo()).catalogName(catalogName).schemaName(
                schemaName).columns(columns).keys(keys).nullKeyValues(nullKeyValues).quotedIdentifiers(getDdlBuilder().isDelimitedIdentifierModeOn())
                .textColumnExpression(textColumnExpression).namedParameters(namedParameters);
        return createDmlStatement(options);
    }

    @Override
    public DmlStatement createDmlStatement(DmlStatementOptions options) {
        return DmlStatementFactory.getInstance().create(getName(), options);
    }

    @Override
    public IDdlReader getDdlReader() {
        return ddlReader;
    }

    @Override
    public IDdlBuilder getDdlBuilder() {
        return ddlBuilder;
    }

    @Override
    public void setClearCacheModelTimeoutInMs(long clearCacheModelTimeoutInMs) {
        this.clearCacheModelTimeoutInMs = clearCacheModelTimeoutInMs;
    }

    @Override
    public long getClearCacheModelTimeoutInMs() {
        return clearCacheModelTimeoutInMs;
    }

    @Override
    public void dropTables(boolean continueOnError, Table... tables) {
        Database db = new Database();
        for (Table table : tables) {
            db.addTable(table);
        }
        dropDatabase(db, continueOnError);
    }

    @Override
    public void dropDatabase(Database database, boolean continueOnError) {
        String sql = ddlBuilder.dropTables(database);
        new SqlScript(sql, getSqlTemplate(), !continueOnError, null).execute(getDatabaseInfo().isRequiresAutoCommitForDdl());
    }

    @Override
    public void createTables(boolean dropTablesFirst, boolean continueOnError, Table... tables) {
        Database database = new Database();
        database.addTables(tables);
        createDatabase(database, dropTablesFirst, continueOnError);
    }

    @Override
    public void createDatabase(Database targetDatabase, boolean dropTablesFirst, boolean continueOnError) {
        if (dropTablesFirst) {
            dropDatabase(targetDatabase, true);
        }
        String createSql = ddlBuilder.createTables(targetDatabase, false);
        if (log.isDebugEnabled()) {
            log.debug("Generated create sql: \n{}", createSql);
        }
        String delimiter = getDdlBuilder().getDatabaseInfo().getSqlCommandDelimiter();
        new SqlScript(createSql, getSqlTemplate(), !continueOnError, false, false,
                getDatabaseInfo().isTriggersContainJava(), delimiter, null)
                .execute(getDatabaseInfo().isRequiresAutoCommitForDdl());
    }

    @Override
    public void alterDatabase(Database desiredDatabase, String triggerPrefix, boolean continueOnError, IAlterDatabaseInterceptor[] interceptors) {
        alterTables(continueOnError, false, triggerPrefix, interceptors, desiredDatabase.getTables());
    }

    @Override
    public void alterDatabase(Database desiredDatabase, String triggerPrefix, boolean continueOnError) {
        alterDatabase(desiredDatabase, triggerPrefix, continueOnError, null);
    }

    @Override
    public void alterTables(boolean continueOnError, Table... desiredTables) {
        alterTables(continueOnError, false, null, null, desiredTables);
    }

    @Override
    public void alterTables(boolean continueOnError, boolean createTableIncludeApplicationTriggers, String triggerPrefix,
            IAlterDatabaseInterceptor[] interceptors, Table... desiredTables) {
        Database currentDatabase = new Database();
        currentDatabase.setName(getName());
        Database desiredDatabase = new Database();
        desiredDatabase.setName(getName());
        StringBuilder tablesProcessed = new StringBuilder();
        for (Table table : desiredTables) {
            tablesProcessed.append(table.getFullyQualifiedTableName());
            tablesProcessed.append(", ");
            desiredDatabase.addTable(table);
            Table currentTable = ddlReader.readTable(table.getCatalog(), table.getSchema(), table.getName());
            if (currentTable != null) {
                if (createTableIncludeApplicationTriggers) {
                    List<Trigger> triggers = ddlReader.getApplicationTriggersForModel(table.getCatalog(), table.getSchema(), table.getName(), triggerPrefix);
                    if (triggers != null && triggers.size() > 0) {
                        currentTable.addTriggers(triggers);
                    }
                }
                currentDatabase.addTable(currentTable);
            }
        }
        if (tablesProcessed.length() > 1) {
            tablesProcessed.replace(tablesProcessed.length() - 2, tablesProcessed.length(), "");
        }
        String alterSql = ddlBuilder.alterDatabase(currentDatabase, desiredDatabase, interceptors);
        if (StringUtils.isNotBlank(alterSql.trim())) {
            String delimiter = getDdlBuilder().getDatabaseInfo().getSqlCommandDelimiter();
            Map<String, String> replacementTokens = new HashMap<String, String>();
            replacementTokens.put(ddlBuilder.getTriggerDelimiterReplacementCharacters(), getDdlBuilder().getDatabaseInfo().getSqlCommandDelimiter());
            log.info("Running alter sql:\n{}", getAlterSql(alterSql, createTableIncludeApplicationTriggers, replacementTokens, delimiter));
            new SqlScript(alterSql, getSqlTemplate(), !continueOnError, false, false, delimiter, replacementTokens)
                    .execute(getDatabaseInfo().isRequiresAutoCommitForDdl());
        } else {
            log.info("Tables up to date.  No alters found for {}", tablesProcessed);
        }
    }

    private String getAlterSql(String alterSql, boolean replaceTokens, Map<String, String> replacementTokens, String delimiter) {
        if (replaceTokens) {
            StringBuilder ddl = new StringBuilder();
            SqlScriptReader reader = new SqlScriptReader(new StringReader(alterSql));
            reader.setDelimiter(getDdlBuilder().getDatabaseInfo().getSqlCommandDelimiter());
            reader.setReplacementTokens(replacementTokens);
            for (String statement = reader.readSqlStatement(); statement != null; statement = reader.readSqlStatement()) {
                ddl.append(statement).append(delimiter).append(System.lineSeparator());
            }
            return ddl.toString();
        } else {
            return alterSql;
        }
    }

    @Override
    public Database readDatabase(String catalog, String schema, String[] tableTypes) {
        Database model = ddlReader.readTables(catalog, schema, tableTypes);
        if ((model.getName() == null) || (model.getName().length() == 0)) {
            model.setName(MODEL_DEFAULT_NAME);
        }
        return model;
    }

    @Override
    public Database readFromDatabase(Table... tables) {
        Database fromDb = new Database();
        for (Table tableFromXml : tables) {
            Table tableFromDatabase = getTableFromCache(tableFromXml.getCatalog(), tableFromXml.getSchema(), tableFromXml.getName(), true);
            if (tableFromDatabase != null) {
                fromDb.addTable(tableFromDatabase);
            }
        }
        fromDb.initialize();
        return fromDb;
    }

    @Override
    public Table readTableFromDatabase(String catalogName, String schemaName, String tableName) {
        try {
            return readTableFromDatabaseAllowException(catalogName, schemaName, tableName);
        } catch (Exception e) {
            if (getSqlTemplate().isDeadlock(e)) {
                log.warn("Deadlock occurred while reading {}, so retrying", tableName);
                return readTableFromDatabaseAllowException(catalogName, schemaName, tableName);
            }
            throw e;
        }
    }

    protected Table readTableFromDatabaseAllowException(String catalogName, String schemaName, String tableName) {
        String originalFullyQualifiedName = Table.getFullyQualifiedTableName(catalogName, schemaName, tableName);
        String defaultedCatalogName = catalogName == null ? getDefaultCatalog() : catalogName;
        String defaultedSchemaName = schemaName == null ? getDefaultSchema() : schemaName;
        Table table = ddlReader.readTable(defaultedCatalogName, defaultedSchemaName, tableName);
        if (table == null && metadataIgnoreCase) {
            IDdlReader reader = getDdlReader();
            if (isNotBlank(catalogName)) {
                List<String> catalogNames = reader.getCatalogNames();
                if (catalogNames != null) {
                    for (String name : catalogNames) {
                        if (name != null && name.equalsIgnoreCase(catalogName)) {
                            defaultedCatalogName = name;
                            break;
                        }
                    }
                }
            }
            if (isNotBlank(schemaName)) {
                List<String> schemaNames = reader.getSchemaNames(catalogName);
                if (schemaNames != null) {
                    for (String name : schemaNames) {
                        if (name != null && name.equalsIgnoreCase(schemaName)) {
                            defaultedSchemaName = name;
                            break;
                        }
                    }
                }
            }
            List<String> tableNames = reader.getTableNames(defaultedCatalogName, defaultedSchemaName, null);
            if (tableNames != null) {
                for (String name : tableNames) {
                    if (name != null && name.equalsIgnoreCase(tableName)) {
                        tableName = name;
                        break;
                    }
                }
            }
            if (!originalFullyQualifiedName.equals(Table.getFullyQualifiedTableName(defaultedCatalogName, defaultedSchemaName, tableName))) {
                table = ddlReader.readTable(defaultedCatalogName, defaultedSchemaName, tableName);
            }
        }
        if (table != null && log.isDebugEnabled()) {
            log.debug("Just read table: \n{}", table.toVerboseString());
        }
        return table;
    }

    @Override
    public Table readTableFromDatabase(ISqlTransaction transaction, String catalogName, String schemaName, String tableName) {
        String defaultedCatalogName = catalogName == null ? getDefaultCatalog() : catalogName;
        String defaultedSchemaName = schemaName == null ? getDefaultSchema() : schemaName;
        Table table = ddlReader.readTable(transaction, defaultedCatalogName, defaultedSchemaName, tableName);
        if (table == null && metadataIgnoreCase) {
            table = ddlReader.readTable(transaction, StringUtils.toRootUpperCase(defaultedCatalogName), StringUtils.toRootUpperCase(defaultedSchemaName),
                    tableName.toUpperCase());
            if (table == null) {
                table = ddlReader.readTable(transaction, StringUtils.toRootLowerCase(defaultedCatalogName), StringUtils.toRootLowerCase(defaultedSchemaName),
                        tableName.toUpperCase());
            }
        }
        return table;
    }

    @Override
    public void resetCachedTableModel() {
        this.tableCache = Collections.synchronizedMap(new HashMap<String, Table>());
        lastTimeCachedModelClearedInMs = System.currentTimeMillis();
    }

    @Override
    public Table getTableFromCache(String tableName, boolean forceReread) {
        return getTableFromCache(getDefaultCatalog(), getDefaultSchema(), tableName, forceReread);
    }

    @Override
    public Table getTableFromCache(String catalogName, String schemaName, String tableName, boolean forceReread) {
        if (System.currentTimeMillis() - lastTimeCachedModelClearedInMs > clearCacheModelTimeoutInMs) {
            resetCachedTableModel();
        }
        Map<String, Table> model = tableCache;
        String key = Table.getFullyQualifiedTableName(catalogName, schemaName, tableName);
        Table retTable = model != null ? model.get(key) : null;
        if (retTable == null || forceReread) {
            try {
                Table table = readTableFromDatabase(catalogName, schemaName, tableName);
                tableCache.put(key, table);
                retTable = table;
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return retTable;
    }

    @Override
    public Object[] getObjectValues(BinaryEncoding encoding, Table table, String[] columnNames, String[] values) {
        Column[] metaData = Table.orderColumns(columnNames, table, false);
        return getObjectValues(encoding, values, metaData);
    }

    @Override
    public Object[] getObjectValues(BinaryEncoding encoding, Table table, String[] columnNames, String[] values, boolean useVariableDates,
            boolean fitToColumn) {
        Column[] metaData = Table.orderColumns(columnNames, table, false);
        return getObjectValues(encoding, values, metaData, useVariableDates, fitToColumn);
    }

    @Override
    public Object[] getObjectValues(BinaryEncoding encoding, String[] values, Column[] orderedMetaData) {
        return getObjectValues(encoding, values, orderedMetaData, false, false);
    }

    @Override
    public Object[] getObjectValues(BinaryEncoding encoding, String[] values, Column[] orderedMetaData, boolean useVariableDates,
            boolean fitToColumn) {
        if (values != null) {
            List<Object> list = new ArrayList<Object>(values.length);
            for (int i = 0; i < values.length; i++) {
                String value = values[i];
                Column column = orderedMetaData.length > i ? orderedMetaData[i] : null;
                try {
                    if (column != null) {
                        list.add(getObjectValue(value, column, encoding, useVariableDates, fitToColumn));
                    }
                } catch (Exception ex) {
                    String valueTrimmed = FormatUtils.abbreviateForLogging(value);
                    throw new RuntimeException("Could not convert a value of " + valueTrimmed + " for column " + column.getName()
                            + " of mapped type " + column.getMappedType() + " jdbc type "
                            + column.getJdbcTypeName() + " (" + column.getJdbcTypeCode() + ")", ex);
                }
            }
            return list.toArray();
        } else {
            return null;
        }
    }

    protected Object getObjectValue(String value, Column column, BinaryEncoding encoding, boolean useVariableDates, boolean fitToColumn)
            throws DecoderException {
        Object objectValue = value;
        int type = column.getMappedTypeCode();
        if ((value == null || (getDdlBuilder().getDatabaseInfo().isEmptyStringNulled() && value.equals(""))) && column.isRequired()
                && column.isOfTextType()) {
            objectValue = REQUIRED_FIELD_NULL_SUBSTITUTE;
        }
        if (value != null) {
            if (type == Types.DATE || type == Types.TIMESTAMP || type == Types.TIME) {
                objectValue = parseDate(type, value, useVariableDates);
            } else if (type == Types.CHAR) {
                if ((StringUtils.isBlank(value) && getDdlBuilder().getDatabaseInfo().isBlankCharColumnSpacePadded())
                        || (StringUtils.isNotBlank(value) && getDdlBuilder().getDatabaseInfo().isNonBlankCharColumnSpacePadded())) {
                    if (column.getSizeAsInt() != column.getCharOctetLength()) {
                        // using multiple-byte character set, the size is maximum number of characters
                        objectValue = StringUtils.rightPad(value, column.getSizeAsInt(), ' ');
                    } else {
                        // single-byte character set or field defined as number of bytes
                        objectValue = value + StringUtils.repeat(" ", column.getCharOctetLength() - value.getBytes(Charset.defaultCharset()).length);
                    }
                } else if (getDdlBuilder().getDatabaseInfo().isCharColumnSpaceTrimmed()) {
                    objectValue = StringUtils.stripEnd(value, " ");
                }
            } else if (type == Types.BIGINT) {
                objectValue = parseBigInteger(value);
            } else if (type == Types.INTEGER || type == Types.SMALLINT || type == Types.BIT || type == Types.TINYINT) {
                objectValue = parseInteger(value);
            } else if (type == Types.FLOAT) {
                objectValue = parseFloat(value);
            } else if (type == Types.NUMERIC || type == Types.DECIMAL || type == Types.DOUBLE || type == Types.REAL) {
                objectValue = parseBigDecimal(value);
            } else if (type == Types.BOOLEAN) {
                objectValue = parseBoolean(value);
            } else if (!(column.getJdbcTypeName() != null && FormatUtils.upper(column.getJdbcTypeName()).contains(TypeMap.GEOMETRY))
                    && !(column.getJdbcTypeName() != null && FormatUtils.upper(column.getJdbcTypeName()).contains(TypeMap.GEOGRAPHY))
                    && (type == Types.BLOB || type == Types.LONGVARBINARY || type == Types.BINARY || type == Types.VARBINARY ||
                    // SQLServer ntext type
                            type == -10)) {
                if (encoding == BinaryEncoding.NONE) {
                    objectValue = value.getBytes(Charset.defaultCharset());
                } else if (encoding == BinaryEncoding.BASE64) {
                    objectValue = Base64.decodeBase64(value.getBytes(Charset.defaultCharset()));
                } else if (encoding == BinaryEncoding.HEX) {
                    objectValue = Hex.decodeHex(value.toCharArray());
                }
            } else if (type == Types.ARRAY) {
                objectValue = createArray(column, value);
            }
        }
        if (objectValue instanceof String) {
            String stringValue = cleanTextForTextBasedColumns((String) objectValue);
            int size = column.getSizeAsInt();
            if (settings.isRightTrimCharValues()) {
                stringValue = StringUtils.stripEnd(stringValue, null);
            }
            if (fitToColumn && size > 0 && stringValue.length() > size) {
                stringValue = stringValue.substring(0, size);
            }
            objectValue = stringValue;
        }
        return objectValue;
    }

    protected Object parseFloat(String value) {
        return parseBigDecimal(value);
    }

    protected Object parseBigDecimal(String value) {
        value = cleanNumber(value);
        /*
         * In the case of a 'NaN' value, return a String
         */
        if (value != null && value.equals("NaN")) {
            return value;
        }
        /*
         * The number will have either one period or one comma for the decimal point, but we need a period
         */
        return new BigDecimal(value.replace(',', '.'));
    }

    protected Object parseBigInteger(String value) {
        try {
            value = cleanNumber(value);
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return new BigDecimal(value.replace(',', '.')).toBigInteger();
        }
    }

    protected Object parseInteger(String value) {
        try {
            value = cleanNumber(value);
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return new BigInteger(value);
        }
    }

    protected Object parseBoolean(String value) {
        value = cleanNumber(value);
        return value.equals("1") ? Boolean.TRUE : Boolean.FALSE;
    }

    protected String cleanNumber(String value) {
        value = value.trim();
        if (value.equalsIgnoreCase("true")) {
            return "1";
        } else if (value.equalsIgnoreCase("false")) {
            return "0";
        } else {
            return value;
        }
    }

    // TODO: this should be AbstractDdlBuilder.getInsertSql(Table table,
    // Map<String, Object> columnValues, boolean genPlaceholders)
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
                if (type == Types.BOOLEAN || type == Types.BIT) {
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
                if (isColumnPositionUsingTemplate[i]) {
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

    protected void appendString(StringBuilder sb, String value) {
        if (value != null) {
            sb.append("\"").append(value).append("\"");
        }
    }

    protected String getDateTimeStringValue(String name, int type, Row row, boolean useVariableDates) {
        Object dateObj = row.get(name);
        if (dateObj instanceof String) {
            return (String) dateObj;
        } else {
            Date date = row.getDateTime(name);
            if (useVariableDates) {
                long diff = date.getTime() - System.currentTimeMillis();
                return "${curdate" + diff + "}";
            } else {
                return FormatUtils.TIMESTAMP_FORMATTER.format(date);
            }
        }
    }

    protected String getTimestampStringValue(String name, int type, Row row, boolean useVariableDates) {
        Object tsObj = row.get(name);
        if (tsObj instanceof String) {
            return (String) tsObj;
        } else {
            Timestamp ts = row.getTimestamp(name);
            if (useVariableDates) {
                long diff = ts.getTime() - System.currentTimeMillis();
                return "${ts" + diff + "}";
            } else {
                String formattedDate = StringUtils.stripEnd(String.format("%tF %tT.%09d", ts, ts, ts.getNanos()), "0");
                if (formattedDate.endsWith(".")) {
                    formattedDate = formattedDate + "0";
                }
                return formattedDate;
            }
        }
    }

    protected String getTimestampTzStringValue(String name, int type, Row row, boolean useVariableDates) {
        return row.getString(name);
    }

    @Override
    public Map<String, String> getSqlScriptReplacementTokens() {
        return null;
    }

    @Override
    public String scrubSql(String sql) {
        Map<String, String> replacementTokens = getSqlScriptReplacementTokens();
        if (replacementTokens != null) {
            return FormatUtils.replaceTokens(sql, replacementTokens, false);
        } else {
            return sql;
        }
    }

    protected Array createArray(Column column, final String value) {
        return null;
    }

    protected String cleanTextForTextBasedColumns(String text) {
        return text;
    }

    @Override
    public java.util.Date parseDate(int type, String value, boolean useVariableDates) {
        if (StringUtils.isNotBlank(value)) {
            try {
                boolean useTimestamp = (type == Types.TIMESTAMP)
                        || (type == Types.DATE && getDdlBuilder().getDatabaseInfo().isDateOverridesToTimestamp());
                if (useVariableDates && value.startsWith("${curdate")) {
                    long time = Long.parseLong(value.substring(10, value.length() - 1));
                    if (value.substring(9, 10).equals("-")) {
                        time *= -1L;
                    }
                    time += System.currentTimeMillis();
                    if (useTimestamp) {
                        return new Timestamp(time);
                    }
                    return new Date(time);
                } else {
                    if (useTimestamp) {
                        return parseTimestamp(type, value);
                    } else if (type == Types.TIME) {
                        if (value.indexOf(".") == 8 || value.length() <= 8) {
                            return Timestamp.valueOf("1970-01-01 " + value);
                        } else {
                            return Timestamp.valueOf(value);
                        }
                    } else {
                        return FormatUtils.parseDate(value, FormatUtils.TIMESTAMP_PATTERNS);
                    }
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    @Override
    public Map<String, String> parseQualifiedTableName(String tableName) {
        Map<String, String> tableNameParts = new LinkedHashMap<String, String>();
        if (StringUtils.isEmpty(tableName)) {
            return tableNameParts;
        }
        String[] initialSplit = tableName.split(Pattern.quote(getDatabaseInfo().getCatalogSeparator()));
        if (initialSplit.length == 0) {
            initialSplit = new String[] { tableName };
        }
        List<String> nameComponents = new ArrayList<String>();
        for (String part : initialSplit) {
            String[] subParts = part.split(Pattern.quote(getDatabaseInfo().getSchemaSeparator()));
            if (subParts.length == 0) {
                subParts = new String[] { part };
            }
            for (String subPart : subParts) {
                if (!StringUtils.isEmpty(subPart)) {
                    nameComponents.add(subPart);
                }
            }
        }
        if (nameComponents.size() >= 3) {
            tableNameParts.put("catalog", nameComponents.get(0));
            tableNameParts.put("schema", nameComponents.get(1));
            tableNameParts.put("table", nameComponents.get(2));
        } else if (nameComponents.size() == 2) {
            tableNameParts.put("schema", nameComponents.get(0));
            tableNameParts.put("table", nameComponents.get(1));
        } else {
            tableNameParts.put("table", nameComponents.get(0));
        }
        return tableNameParts;
    }

    @Override
    public Table makeAllColumnsPrimaryKeys(Table table) {
        Table result = table.copy();
        IIndex[] indices = result.getUniqueIndices();
        if (indices != null && indices.length > 0) {
            for (IndexColumn indexColumn : indices[0].getColumns()) {
                Column column = result.getColumnWithName(indexColumn.getName());
                if (column != null) {
                    boolean required = column.isRequired();
                    column.setPrimaryKey(true);
                    column.setRequired(required);
                }
            }
        } else {
            for (Column column : result.getColumns()) {
                if (!isLob(column) && canColumnBeUsedInWhereClause(column)) {
                    column.setPrimaryKey(true);
                }
            }
            result.setMadeAllColumnsPrimaryKey(true);
        }
        return result;
    }

    @Override
    public boolean isLob(Column column) {
        return isClob(column) || isBlob(column);
    }

    @Override
    public boolean isClob(Column column) {
        int type = column.getMappedTypeCode();
        return type == Types.CLOB || type == Types.NCLOB || type == Types.LONGVARCHAR || type == ColumnTypes.LONGNVARCHAR;
    }

    @Override
    public boolean isBlob(Column column) {
        int type = column.getMappedTypeCode();
        if (settings.isTreatBinaryAsLob()) {
            return type == Types.BLOB || type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY || type == -10;
        }
        return type == Types.BLOB || type == Types.LONGVARBINARY || type == -10;
    }

    @Override
    public List<Column> getLobColumns(Table table) {
        List<Column> lobColumns = new ArrayList<Column>(1);
        Column[] allColumns = table.getColumns();
        for (Column column : allColumns) {
            if (isLob(column)) {
                lobColumns.add(column);
            }
        }
        return lobColumns;
    }

    @Override
    public void setMetadataIgnoreCase(boolean metadataIgnoreCase) {
        this.metadataIgnoreCase = metadataIgnoreCase;
    }

    @Override
    public boolean isMetadataIgnoreCase() {
        return metadataIgnoreCase;
    }

    @Override
    public boolean isStoresLowerCaseIdentifiers() {
        if (storesLowerCaseIdentifiers == null) {
            storesLowerCaseIdentifiers = getSqlTemplate().isStoresLowerCaseIdentifiers();
        }
        return storesLowerCaseIdentifiers;
    }

    @Override
    public boolean isStoresMixedCaseQuotedIdentifiers() {
        if (storesMixedCaseIdentifiers == null) {
            storesMixedCaseIdentifiers = getSqlTemplate().isStoresMixedCaseQuotedIdentifiers();
        }
        return storesMixedCaseIdentifiers;
    }

    @Override
    public boolean isStoresUpperCaseIdentifiers() {
        if (storesUpperCaseIdentifiers == null) {
            storesUpperCaseIdentifiers = getSqlTemplate().isStoresUpperCaseIdentifiers();
        }
        return storesUpperCaseIdentifiers;
    }

    @Override
    public Database readDatabaseFromXml(String filePath, boolean alterCaseToMatchDatabaseDefaultCase) {
        InputStream is = null;
        try {
            File file = new File(filePath);
            if (file.exists()) {
                try {
                    is = new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    throw new IoException(e);
                }
            } else {
                is = AbstractDatabasePlatform.class.getResourceAsStream(filePath);
            }
            if (is != null) {
                return readDatabaseFromXml(is, alterCaseToMatchDatabaseDefaultCase);
            } else {
                throw new IoException("Could not find the file: %s", filePath);
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @Override
    public void prefixDatabase(String prefix, Database targetTables) {
        try {
            if (StringUtils.isNotBlank(prefix) && !prefix.endsWith("_")) {
                prefix = prefix + "_";
            }
            Table[] tables = targetTables.getTables();
            boolean storesUpperCaseIdentifiers = isStoresUpperCaseIdentifiers();
            for (Table table : tables) {
                String name = String.format("%s%s", prefix, table.getName());
                table.setName(storesUpperCaseIdentifiers ? FormatUtils.upper(name) : FormatUtils.lower(name));
                prefixForeignKeys(table, prefix, storesUpperCaseIdentifiers);
                prefixIndexes(table, prefix, storesUpperCaseIdentifiers);
                prefixColumnNames(table, storesUpperCaseIdentifiers);
            }
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    protected void prefixColumnNames(Table table, boolean storesUpperCaseIdentifiers) {
        Column[] columns = table.getColumns();
        for (Column column : columns) {
            column.setName(storesUpperCaseIdentifiers ? FormatUtils.upper(column.getName()) : FormatUtils.lower(column.getName()));
        }
    }

    protected void prefixForeignKeys(Table table, String tablePrefix, boolean storesUpperCaseIdentifiers) throws CloneNotSupportedException {
        ForeignKey[] keys = table.getForeignKeys();
        for (ForeignKey key : keys) {
            String prefixedName = tablePrefix + key.getForeignTableName();
            prefixedName = storesUpperCaseIdentifiers ? FormatUtils.upper(prefixedName) : FormatUtils.lower(prefixedName);
            key.setForeignTableName(prefixedName);
            String keyName = tablePrefix + key.getName();
            keyName = storesUpperCaseIdentifiers ? FormatUtils.upper(keyName) : FormatUtils.lower(keyName);
            key.setName(keyName);
            Reference[] refs = key.getReferences();
            for (Reference reference : refs) {
                reference.setForeignColumnName(storesUpperCaseIdentifiers ? FormatUtils.upper(reference.getForeignColumnName())
                        : FormatUtils.lower(reference.getForeignColumnName()));
                reference.setLocalColumnName(storesUpperCaseIdentifiers ? FormatUtils.upper(reference.getLocalColumnName())
                        : FormatUtils.lower(reference.getLocalColumnName()));
            }
        }
    }

    protected void prefixIndexes(Table table, String tablePrefix, boolean storesUpperCaseIdentifiers) throws CloneNotSupportedException {
        IIndex[] indexes = table.getIndices();
        if (indexes != null) {
            for (IIndex index : indexes) {
                String prefixedName = tablePrefix + index.getName();
                prefixedName = storesUpperCaseIdentifiers ? FormatUtils.upper(prefixedName) : FormatUtils.lower(prefixedName);
                index.setName(prefixedName);
            }
        }
    }

    @Override
    public void alterCaseToMatchDatabaseDefaultCase(Database database) {
        Table[] tables = database.getTables();
        for (Table table : tables) {
            alterCaseToMatchDatabaseDefaultCase(table);
        }
    }

    @Override
    public String[] alterCaseToMatchDatabaseDefaultCase(String[] values) {
        String[] newValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            newValues[i] = alterCaseToMatchDatabaseDefaultCase(values[i]);
        }
        return newValues;
    }

    @Override
    public String alterCaseToMatchDatabaseDefaultCase(String value) {
        if (StringUtils.isNotBlank(value)) {
            boolean storesUpperCase = isStoresUpperCaseIdentifiers();
            if (!FormatUtils.isMixedCase(value)) {
                value = storesUpperCase ? FormatUtils.upper(value) : FormatUtils.lower(value);
            }
        }
        return value;
    }

    @Override
    public void alterCaseToMatchDatabaseDefaultCase(Table... tables) {
        for (Table table : tables) {
            alterCaseToMatchDatabaseDefaultCase(table);
        }
    }

    @Override
    public void alterCaseToMatchDatabaseDefaultCase(Table table) {
        table.setName(alterCaseToMatchDatabaseDefaultCase(table.getName()));
        Column[] columns = table.getColumns();
        for (Column column : columns) {
            column.setName(alterCaseToMatchDatabaseDefaultCase(column.getName()));
        }
        IIndex[] indexes = table.getIndices();
        for (IIndex index : indexes) {
            index.setName(alterCaseToMatchDatabaseDefaultCase(index.getName()));
            IndexColumn[] indexColumns = index.getColumns();
            for (IndexColumn indexColumn : indexColumns) {
                indexColumn.setName(alterCaseToMatchDatabaseDefaultCase(indexColumn.getName()));
            }
        }
        ForeignKey[] fks = table.getForeignKeys();
        for (ForeignKey foreignKey : fks) {
            foreignKey.setName(alterCaseToMatchDatabaseDefaultCase(foreignKey.getName()));
            foreignKey.setForeignTableName(alterCaseToMatchDatabaseDefaultCase(foreignKey.getForeignTableName()));
            Reference[] references = foreignKey.getReferences();
            for (Reference reference : references) {
                reference.setForeignColumnName(alterCaseToMatchDatabaseDefaultCase(reference.getForeignColumnName()));
                reference.setLocalColumnName(alterCaseToMatchDatabaseDefaultCase(reference.getLocalColumnName()));
            }
        }
    }

    @Override
    public Database readDatabaseFromXml(InputStream is, boolean alterCaseToMatchDatabaseDefaultCase) {
        InputStreamReader reader = new InputStreamReader(is);
        Database database = DatabaseXmlUtil.read(reader);
        if (alterCaseToMatchDatabaseDefaultCase) {
            alterCaseToMatchDatabaseDefaultCase(database);
        }
        return database;
    }

    @Override
    public boolean canColumnBeUsedInWhereClause(Column column) {
        return column.getJdbcTypeCode() != Types.FLOAT &&
                column.getJdbcTypeCode() != Types.DOUBLE &&
                column.getJdbcTypeCode() != Types.REAL;
    }

    public java.util.Date parseTimestamp(int type, String value) {
        try {
            if (value.indexOf(".") == 8 || value.length() <= 8) {
                value = "1970-01-01 " + value;
            }
            return Timestamp.valueOf(value);
        } catch (IllegalArgumentException ex) {
            if (!getDatabaseInfo().isZeroDateAllowed() && value != null && value.startsWith(ZERO_DATE_STRING)) {
                return null;
            }
            try {
                return new Timestamp(FormatUtils.parseDate(value, FormatUtils.TIMESTAMP_PATTERNS).getTime());
            } catch (Exception e) {
                int split = value.lastIndexOf(" ");
                String datetime = value.substring(0, split).trim();
                String timezone = value.substring(split).trim();
                try {
                    return Timestamp.valueOf(datetime); // Try it again without
                                                        // the timezone
                                                        // component.
                } catch (IllegalArgumentException ex2) {
                    return new Timestamp(FormatUtils.parseDate(datetime, FormatUtils.TIMESTAMP_PATTERNS, getTimeZone(timezone)).getTime());
                }
            }
        }
    }

    public TimeZone getTimeZone(String value) {
        TimeZone tz = TimeZone.getTimeZone("GMT" + value); // try as an offset.
                                                           // ("-05:00")
        if (tz.getRawOffset() == 0) {
            tz = TimeZone.getTimeZone(value); // try as a raw code. e.g. "EST"
        }
        return tz;
    }

    @Override
    public void makePlatformSpecific(Database database) {
        Table[] tables = database.getTables();
        for (Table table : tables) {
            for (Column autoIncrementColumn : table.getAutoIncrementColumns()) {
                if (!autoIncrementColumn.isPrimaryKey() && !getDatabaseInfo().isNonPKIdentityColumnsSupported()) {
                    log.info("Removing auto increment from table " + table.getName() + " for column " + autoIncrementColumn.getName()
                            + " since it was not part of primary key and not supported on this database based on nonPKIdentityColumnsSupported.");
                    autoIncrementColumn.setAutoIncrement(false);
                }
            }
            if (table.getCatalog() == null) {
                table.setCatalog(getDefaultCatalog());
            }
            if (table.getSchema() == null) {
                table.setSchema(getDefaultSchema());
            }
            for (Column column : table.getColumns()) {
                PlatformColumn platformColumn = column.findPlatformColumn(getName());
                if (platformColumn == null) {
                    platformColumn = new PlatformColumn(getName(), ddlBuilder.getSqlType(column), column.getDefaultValue());
                    column.addPlatformColumn(platformColumn);
                }
            }
            if (!getDatabaseInfo().isFunctionalIndicesSupported()) {
                List<IIndex> indicesToRemove = new ArrayList<IIndex>();
                for (IIndex index : table.getIndices()) {
                    if (isFunctionalIndex(index)) {
                        indicesToRemove.add(index);
                    }
                }
                for (IIndex indexToRemove : indicesToRemove) {
                    log.info("Removing index " + indexToRemove.getName()
                            + " from table " + table.getName() + " because functional indexes are not supported on this platform.");
                    table.removeIndex(indexToRemove);
                }
            }
        }
    }

    private boolean isFunctionalIndex(IIndex index) {
        boolean ret = false;
        for (IndexColumn indexColumn : index.getColumns()) {
            if (indexColumn.getName().contains("(") || indexColumn.getName().contains(")")) {
                ret = true;
                break;
            }
        }
        return ret;
    }

    @Override
    public boolean hasMatchingPlatform(Database db) {
        boolean matches = true;
        for (Table table : db.getTables()) {
            for (Column column : table.getColumns()) {
                matches &= column.getPlatformColumns() != null && column.getPlatformColumns().get(getName()) != null;
                break;
            }
        }
        return matches;
    }

    @Override
    public List<PermissionResult> checkSymTablePermissions(PermissionType... permissionTypes) {
        List<PermissionResult> results = new ArrayList<PermissionResult>();
        Database database = new Database();
        PermissionResult createResult = null;
        PermissionResult createTriggerResult = null;
        PermissionResult dropTriggerResult = null;
        boolean drop = false;
        if (ArrayUtils.contains(permissionTypes, PermissionType.CREATE_TABLE)) {
            createResult = getCreateSymTablePermission(database);
        }
        if (ArrayUtils.contains(permissionTypes, PermissionType.CREATE_TRIGGER)) {
            createTriggerResult = getCreateSymTriggerPermission();
            dropTriggerResult = getDropSymTriggerPermission();
        }
        for (PermissionType permissionType : permissionTypes) {
            switch (permissionType) {
                case CREATE_TABLE:
                    results.add(createResult);
                    break;
                case ALTER_TABLE:
                    results.add(getAlterSymTablePermission(database));
                    break;
                case CREATE_TRIGGER:
                    results.add(createTriggerResult);
                    break;
                case DROP_TRIGGER:
                    results.add(dropTriggerResult);
                    break;
                case EXECUTE:
                    results.add(getExecuteSymPermission());
                    break;
                case DROP_TABLE:
                    drop = true;
                    break;
                case CREATE_FUNCTION:
                    results.add(getCreateSymFunctionPermission());
                    break;
                case CREATE_ROUTINE:
                    results.add(getCreateSymRoutinePermission());
                    break;
                case LOG_MINE:
                    results.add(getLogMinePermission());
                    break;
                default:
                    break;
            }
        }
        if (drop) {
            results.add(getDropSymTablePermission());
        }
        logFailedResults(results);
        return results;
    }

    protected void logFailedResults(List<PermissionResult> results) {
        for (PermissionResult result : results) {
            if (Status.FAIL == result.getStatus()) {
                log.info(String.format("Database permission check failed. Category: %s Permission Type: %s Details:\r\n%s", result.getCategory(), result
                        .getPermissionType(),
                        result.getTestDetails()), result.getException());
            }
        }
    }

    protected Table getPermissionTableDefinition() {
        Column idColumn = new Column("TEST_ID");
        idColumn.setMappedType("INTEGER");
        idColumn.setRequired(true);
        idColumn.setPrimaryKey(true);
        Column valueColumn = new Column("TEST_VALUE");
        valueColumn.setMappedType("INTEGER");
        return new Table(PERMISSION_TEST_TABLE_NAME, idColumn, valueColumn);
    }

    protected PermissionResult getCreateSymTablePermission(Database database) {
        Table table = getPermissionTableDefinition();
        String createSql = ddlBuilder.createTables(database, false);
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TABLE, createSql);
        getDropSymTablePermission();
        try {
            database.addTable(table);
            createDatabase(database, false, false);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant CREATE permission");
        }
        return result;
    }

    protected PermissionResult getDropSymTablePermission() {
        Table table = getPermissionTableDefinition();
        PermissionResult result = new PermissionResult(PermissionType.DROP_TABLE, "dropping table " + table.getName() + "...");
        try {
            if (getTableFromCache(table.getName(), true) != null) {
                dropTables(false, table);
                result.setStatus(Status.PASS);
            } else {
                result.setStatus(Status.NOT_APPLICABLE);
            }
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant DROP permission");
        }
        return result;
    }

    protected PermissionResult getAlterSymTablePermission(Database database) {
        String delimiter = getDatabaseInfo().getDelimiterToken();
        delimiter = delimiter != null ? delimiter : "";
        Column idColumn = new Column("TEST_ID");
        idColumn.setMappedType("INTEGER");
        Column valueColumn = new Column("TEST_VALUE");
        valueColumn.setMappedType("INTEGER");
        Column alterColumn = new Column("TEST_ALTER");
        alterColumn.setMappedType("INTEGER");
        Table table = new Table(PERMISSION_TEST_TABLE_NAME, idColumn, valueColumn);
        Table alterTable = new Table(PERMISSION_TEST_TABLE_NAME, idColumn, valueColumn, alterColumn);
        PermissionResult result = new PermissionResult(PermissionType.ALTER_TABLE, "altering table " + PERMISSION_TEST_TABLE_NAME + "...");
        try {
            database.removeAllTablesExcept();
            database.addTable(alterTable);
            alterDatabase(database, "sym", false);
            database.removeAllTablesExcept();
            database.addTable(table);
            alterDatabase(database, "sym", false);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant ALTER permission");
        }
        return result;
    }

    protected PermissionResult getDropSymTriggerPermission() {
        String dropTriggerSql = "DROP TRIGGER TEST_TRIGGER";
        PermissionResult result = new PermissionResult(PermissionType.DROP_TRIGGER, dropTriggerSql);
        try {
            getSqlTemplate().update(dropTriggerSql);
            result.setStatus(Status.PASS);
        } catch (SqlException e) {
            result.setException(e);
            result.setSolution("Grant DROP TRIGGER permission or TRIGGER permission");
        }
        return result;
    }

    protected PermissionResult getCreateSymTriggerPermission() {
        PermissionResult result = new PermissionResult(PermissionType.CREATE_TRIGGER, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    protected PermissionResult getExecuteSymPermission() {
        PermissionResult result = new PermissionResult(PermissionType.EXECUTE, "NOT_APPLICABLE");
        result.setStatus(Status.NOT_APPLICABLE);
        return result;
    }

    protected PermissionResult getCreateSymRoutinePermission() {
        PermissionResult result = new PermissionResult(PermissionType.CREATE_ROUTINE, "NOT_APPLICABLE");
        result.setStatus(Status.NOT_APPLICABLE);
        return result;
    }

    protected PermissionResult getCreateSymFunctionPermission() {
        PermissionResult result = new PermissionResult(PermissionType.CREATE_FUNCTION, "NOT_APPLICABLE");
        result.setStatus(Status.NOT_APPLICABLE);
        return result;
    }

    @Override
    public PermissionResult getLogMinePermission() {
        PermissionResult result = new PermissionResult(PermissionType.LOG_MINE, "UNIMPLEMENTED");
        result.setStatus(Status.UNIMPLEMENTED);
        return result;
    }

    @Override
    public boolean isUseMultiThreadSyncTriggers() {
        return useMultiThreadSyncTriggers;
    }

    @Override
    public boolean supportsTransactions() {
        if (supportsTransactions == null) {
            if (this.getDataSource() instanceof DataSource) {
                try {
                    supportsTransactions = ((DataSource) this.getDataSource()).getConnection().getMetaData().supportsTransactions();
                } catch (Exception e) {
                    log.warn("Unable to determine if transactions are supported from connection meta data ", e);
                }
            } else {
                supportsTransactions = true;
            }
        }
        return supportsTransactions;
    }

    @Override
    public boolean supportsMultiThreadedTransactions() {
        return true;
    }

    @Override
    public long getEstimatedRowCount(Table table) {
        DatabaseInfo dbInfo = getDatabaseInfo();
        String quote = dbInfo.getDelimiterToken();
        String catalogSeparator = dbInfo.getCatalogSeparator();
        String schemaSeparator = dbInfo.getSchemaSeparator();
        String sql = String.format("select count(*) from %s", table.getQualifiedTableName(quote, catalogSeparator, schemaSeparator));
        return getSqlTemplateDirty().queryForLong(sql);
    }

    @Override
    public String getTruncateSql(Table table) {
        String sql = null;
        if (supportsTruncate) {
            sql = "truncate table ";
            String quote = getDdlBuilder().isDelimitedIdentifierModeOn() ? getDatabaseInfo().getDelimiterToken() : "";
            sql += table.getQualifiedTableName(quote, getDatabaseInfo().getCatalogSeparator(), getDatabaseInfo().getSchemaSeparator());
        } else {
            log.info("Truncate is not supported on " + getName() + ". Changing to equivalent delete statement");
            sql = getDeleteSql(table);
        }
        return sql;
    }

    @Override
    public String getDeleteSql(Table table) {
        String sql = "delete from ";
        String quote = getDdlBuilder().isDelimitedIdentifierModeOn() ? getDatabaseInfo().getDelimiterToken() : "";
        sql += table.getQualifiedTableName(quote, getDatabaseInfo().getCatalogSeparator(), getDatabaseInfo().getSchemaSeparator());
        return sql;
    }

    @Override
    public List<Transaction> getTransactions() {
        return new ArrayList<Transaction>();
    }

    @Override
    public boolean supportsLimitOffset() {
        return false;
    }

    @Override
    public String massageForLimitOffset(String sql, int limit, int offset) {
        return sql;
    }

    @Override
    public String massageForObjectAlreadyExists(String sql) {
        return sql;
    }

    @Override
    public String massageForObjectDoesNotExist(String sql) {
        return sql;
    }

    @Override
    public boolean supportsSliceTables() {
        return false;
    }

    @Override
    public String getSliceTableSql(String columnName, int sliceNum, int totalSlices) {
        return "";
    }

    @Override
    public String getCharSetName() {
        return "";
    }

    @Override
    public boolean supportsParametersInSelect() {
        return true;
    }

    @Override
    public boolean allowsUniqueIndexDuplicatesWithNulls() {
        return true;
    }

    @Override
    public DatabaseVersion getDatabaseVersion() {
        return databaseVersion;
    }

    @Override
    public void setDatabaseVersion(DatabaseVersion databaseVersion) {
        this.databaseVersion = databaseVersion;
    }
}
