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
package org.jumpmind.db.platform.mssql;

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

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.CompressionTypes;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.PlatformIndex;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.DdlException;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ChangeCatalogConnectionHandler;
import org.jumpmind.db.sql.IConnectionHandler;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.mapper.StringMapper;

/*
 * Reads a database model from a Microsoft Sql Server database.
 */
public class MsSqlDdlReader extends AbstractJdbcDdlReader {
    /* Known system tables that Sql Server creates (e.g. automatic maintenance). */
    private static final String[] KNOWN_SYSTEM_TABLES = { "dtproperties" };
    /* The regular expression pattern for the ISO dates. */
    private Pattern isoDatePattern = Pattern.compile("'(\\d{4}\\-\\d{2}\\-\\d{2})'");
    /* The regular expression pattern for the ISO times. */
    private Pattern isoTimePattern = Pattern.compile("'(\\d{2}:\\d{2}:\\d{2})'");
    private Set<String> userDefinedDataTypes = new HashSet<String>();
    private boolean compressAndFilterAndIncludecolumns;

    public MsSqlDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern("%");
        ISqlTemplate sqlTemplate = platform.getSqlTemplateDirty();
        if (sqlTemplate.getDatabaseMajorVersion() >= 9) {
            String sql = "select name from sys.types where is_user_defined = 1";
            List<Row> rows = sqlTemplate.query(sql);
            for (Row r : rows) {
                userDefinedDataTypes.add(r.getString("name"));
            }
        }
        compressAndFilterAndIncludecolumns = platform.getProperties()
                .is("mssql.metadata.query.for.compression.filters.includecolumns", true);
    }

    @Override
    protected String getTableNamePattern(String tableName) {
        tableName = tableName.replace("_", "\\_");
        tableName = tableName.replace("%", "\\%");
        return tableName;
    }

    @Override
    protected Table readTable(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        String tableName = (String) values.get("TABLE_NAME");
        for (int idx = 0; idx < KNOWN_SYSTEM_TABLES.length; idx++) {
            if (KNOWN_SYSTEM_TABLES[idx].equals(tableName)) {
                return null;
            }
        }
        Table table = super.readTable(connection, metaData, values);
        if (table != null) {
            if (Strings.CI.equals(table.getSchema(), "sys")) {
                return null;
            }
            // Sql Server does not return the auto-increment status via the
            // database metadata
            determineAutoIncrementFromResultSetMetaData(connection, table, table.getColumns());
            // TODO: Replace this manual filtering using named pks once they are
            // available
            // This is then probably of interest to every platform
            for (int idx = 0; idx < table.getIndexCount();) {
                IIndex index = table.getIndex(idx);
                if (index.isUnique() && existsPKWithName(metaData, table, index.getName())) {
                    table.removeIndex(idx);
                } else {
                    idx++;
                }
            }
            if (table.hasGeneratedColumns()) {
                String sql = "SELECT name, definition FROM sys.computed_columns WHERE OBJECT_NAME(object_id) = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Column column = table.findColumn(rs.getString(1));
                            if (column != null && column.getDefaultValue() == null) {
                                column.setDefaultValue(rs.getString(2));
                                parseDefaultValue(column);
                            }
                        }
                    }
                }
            }
            if (compressAndFilterAndIncludecolumns && platform instanceof MsSql2008DatabasePlatform) {
                String sql = """
                        SELECT [TABLENAME] = t.[Name]
                                ,[INDEXNAME] = i.[Name]
                                ,[IndexType] = i.[type_desc]
                                ,[FILTER] = i.filter_definition
                                ,[HASFILTER] = i.has_filter
                                ,[COMPRESSIONTYPE] = p.data_compression
                                ,[COMPRESSIONDESCRIPTION] = p.data_compression_desc
                        FROM sys.indexes i WITH (NOLOCK)
                        INNER JOIN sys.tables t WITH (NOLOCK) ON t.object_id = i.object_id
                        INNER JOIN sys.partitions p WITH (NOLOCK) ON p.object_id=t.object_id AND p.index_id=i.index_id
                        WHERE t.type_desc = N'USER_TABLE'
                            and t.name=?
                            and p.index_id in (0,1)
                        """;
                List<String> l = new ArrayList<String>();
                l.add(tableName);
                log.debug("Running the following query to get metadata about whether a table has compression\n {}",
                        sql);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int compressionType = rs.getInt("COMPRESSIONTYPE");
                            boolean hasCompression = (compressionType > 0);
                            if (hasCompression) {
                                String compressionTypeLabel = null;
                                if (compressionType == 1) {
                                    compressionTypeLabel = CompressionTypes.ROW.name();
                                    table.setCompressionType(CompressionTypes.ROW);
                                } else if (compressionType == 2) {
                                    compressionTypeLabel = CompressionTypes.PAGE.name();
                                    table.setCompressionType(CompressionTypes.PAGE);
                                } else if (compressionType == 3) {
                                    compressionTypeLabel = CompressionTypes.COLUMNSTORE.name();
                                    table.setCompressionType(CompressionTypes.COLUMNSTORE);
                                } else if (compressionType == 4) {
                                    compressionTypeLabel = CompressionTypes.COLUMNSTORE_ARCHIVE.name();
                                    table.setCompressionType(CompressionTypes.COLUMNSTORE_ARCHIVE);
                                } else {
                                    table.setCompressionType(CompressionTypes.NONE);
                                }
                                if (compressionTypeLabel != null) {
                                    log.debug("table: {} has compression: {}", tableName, compressionTypeLabel);
                                }
                            }
                        }
                    }
                }
            }
        }
        return table;
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) {
        // Sql Server generates an index "PK__[table name]__[hex number]"
        StringBuilder pkIndexName = new StringBuilder();
        pkIndexName.append("PK__");
        pkIndexName.append(table.getName());
        pkIndexName.append("__");
        return index.getName().toUpperCase().startsWith(pkIndexName.toString().toUpperCase());
    }

    /*
     * Determines whether there is a pk for the table with the given name.
     * 
     * @param metaData The database metadata
     * 
     * @param table The table
     * 
     * @param name The pk name
     * 
     * @return <code>true</code> if there is such a pk
     */
    private boolean existsPKWithName(DatabaseMetaDataWrapper metaData, Table table, String name) {
        try {
            ResultSet pks = metaData.getPrimaryKeys(table.getName());
            boolean found = false;
            while (pks.next() && !found) {
                if (name.equals(pks.getString("PK_NAME"))) {
                    found = true;
                }
            }
            pks.close();
            return found;
        } catch (SQLException ex) {
            throw new DdlException(ex);
        }
    }

    @Override
    protected Integer mapUnknownJdbcTypeForColumn(Map<String, Object> values) {
        String typeName = (String) values.get("TYPE_NAME");
        int size = -1;
        String columnSize = (String) values.get("COLUMN_SIZE");
        if (isNotBlank(columnSize)) {
            size = Integer.parseInt(columnSize);
        }
        if (typeName != null) {
            if (typeName.toLowerCase().startsWith("text")) {
                return Types.LONGVARCHAR;
            } else if (typeName.toLowerCase().startsWith("ntext")) {
                return Types.NCLOB;
            } else if (typeName.toLowerCase().equals("float")) {
                return Types.FLOAT;
            } else if (typeName.toUpperCase().contains(TypeMap.GEOMETRY)) {
                return Types.VARCHAR;
            } else if (typeName.toUpperCase().contains(TypeMap.GEOGRAPHY)) {
                return Types.VARCHAR;
            } else if (typeName.toUpperCase().contains("VARCHAR") && size > 8000) {
                return Types.LONGVARCHAR;
            } else if (typeName.toUpperCase().contains("NVARCHAR") && size > 4000) {
                return Types.LONGNVARCHAR;
            } else if (typeName.toUpperCase().equals("SQL_VARIANT")) {
                return ColumnTypes.MSSQL_SQL_VARIANT;
            } else if (typeName.equalsIgnoreCase("DATETIMEOFFSET")) {
                return ColumnTypes.TIMESTAMPTZ;
            } else if (typeName.equalsIgnoreCase("datetime2")) {
                return Types.TIMESTAMP;
            } else if (typeName.equalsIgnoreCase("DATE")) {
                return Types.DATE;
            } else if (typeName.equalsIgnoreCase("TIME")) {
                return Types.TIME;
            } else if (typeName.equalsIgnoreCase("VARBINARY") && size > 8000) {
                return Types.BLOB;
            }
        }
        return super.mapUnknownJdbcTypeForColumn(values);
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        Column column = super.readColumn(metaData, values);
        parseDefaultValue(column);
        if ((column.getMappedTypeCode() == Types.DECIMAL) && (column.getSizeAsInt() == 19)
                && (column.getScale() == 0)) {
            column.setMappedTypeCode(Types.BIGINT);
        }
        // These columns return sizes and/or decimal places with the metat data from MSSql Server however
        // the values are not adjustable through the create table so they are omitted
        if (column.getJdbcTypeName() != null &&
                (column.getJdbcTypeName().equals("smallmoney")
                        || column.getJdbcTypeName().equals("money")
                        || column.getJdbcTypeName().equals("uniqueidentifier")
                        || column.getJdbcTypeName().equals("date"))
                || column.getJdbcTypeName().equals("timestamp")) {
            removePlatformColumnSize(column);
        }
        if (column.getJdbcTypeName() != null) {
            if (column.getJdbcTypeName().equalsIgnoreCase("datetime2")) {
                adjustColumnSize(column, -20);
            } else if (column.getJdbcTypeName().equalsIgnoreCase("datetime")) {
                column.setSize("3");
                removePlatformColumnSize(column);
            } else if (column.getJdbcTypeName().equalsIgnoreCase("time")) {
                adjustColumnSize(column, -9);
            } else if (column.getJdbcTypeName().equalsIgnoreCase("datetimeoffset")) {
                adjustColumnSize(column, -27);
            } else if (column.getJdbcTypeName().equalsIgnoreCase("date") || column.getJdbcTypeName().equalsIgnoreCase("smalldatetime")) {
                removeColumnSize(column);
            }
        }
        if (userDefinedDataTypes.size() > 0) {
            if (userDefinedDataTypes.contains(column.getJdbcTypeName())) {
                removePlatformColumnSize(column);
                for (PlatformColumn pc : column.getPlatformColumns().values()) {
                    pc.setUserDefinedType(true);
                }
            }
        }
        return column;
    }

    protected void parseDefaultValue(Column column) {
        String defaultValue = column.getDefaultValue();
        // Sql Server tends to surround the returned default value with one or
        // two sets of parentheses
        if (defaultValue != null) {
            while (defaultValue.startsWith("(") && defaultValue.endsWith(")")) {
                String substring = defaultValue.substring(1, defaultValue.length() - 1);
                if (substring.indexOf("(") > substring.indexOf(")") || substring.lastIndexOf("(") > substring.lastIndexOf(")")) {
                    break;
                }
                defaultValue = substring;
            }
            if (column.getMappedTypeCode() == Types.TIMESTAMP) {
                // Sql Server maintains the default values for DATE/TIME jdbc
                // types, so we have to
                // migrate the default value to TIMESTAMP
                Matcher matcher = isoDatePattern.matcher(defaultValue);
                Timestamp timestamp = null;
                if (matcher.matches()) {
                    timestamp = new Timestamp(Date.valueOf(matcher.group(1)).getTime());
                } else {
                    matcher = isoTimePattern.matcher(defaultValue);
                    if (matcher.matches()) {
                        timestamp = new Timestamp(Time.valueOf(matcher.group(1)).getTime());
                    }
                }
                if (timestamp != null) {
                    defaultValue = timestamp.toString();
                }
            } else if (column.getMappedTypeCode() == Types.DECIMAL || column.getMappedTypeCode() == Types.BIGINT) {
                // For some reason, Sql Server 2005 always returns DECIMAL
                // default values with a dot
                // even if the scale is 0, so we remove the dot
                if ((column.getScale() == 0) && defaultValue.endsWith(".")) {
                    defaultValue = defaultValue.substring(0, defaultValue.length() - 1);
                }
            } else if (TypeMap.isTextType(column.getMappedTypeCode())) {
                String unescapedValue = unescapeTextValue(defaultValue, column);
                if (log.isTraceEnabled()) {
                    log.trace("Unescaped default value for column={}, Original={}, Result={}", column.getName(), defaultValue, unescapedValue);
                }
                defaultValue = unescapedValue;
            }
            column.setDefaultValue(defaultValue);
        }
    }

    /**
     * Removes outer quotes and un-escapes Transact-SQL text value. Might need additional parsing of each quoted string item in the future.
     */
    protected String unescapeTextValue(String defaultValue, Column column) {
        if (defaultValue.endsWith("'") && (defaultValue.startsWith("N'") || defaultValue.startsWith("'"))) {
            int newStartPos = 0;
            int newEndPos = defaultValue.length() - 1;
            if (defaultValue.startsWith("N'")) {
                newStartPos = 2;
            } else {
                newStartPos = 1;
            }
            return unescape(defaultValue.substring(newStartPos, newEndPos), "'", "''");
        } else {
            return defaultValue;
        }
    }

    @Override
    public List<String> getTableNamesFromDatabase(final String catalog, final String schema,
            final String[] tableTypes) {
        StringBuilder sql = new StringBuilder("select \"TABLE_NAME\" from ");
        if (isNotBlank(catalog)) {
            sql.append("\"").append(catalog).append("\"").append(".");
        }
        sql.append("\"INFORMATION_SCHEMA\".\"TABLES\" where \"TABLE_TYPE\"='BASE TABLE'");
        List<Object> args = new ArrayList<Object>(2);
        if (isNotBlank(catalog)) {
            sql.append(" and \"TABLE_CATALOG\"=?");
            args.add(catalog);
        }
        if (isNotBlank(schema)) {
            sql.append(" and \"TABLE_SCHEMA\"=?");
            args.add(schema);
        }
        return schema == null ? new ArrayList<String>()
                : platform.getSqlTemplateDirty().queryWithHandler(sql.toString(), new StringMapper(),
                        new ChangeCatalogConnectionHandler(catalog), args.toArray(new Object[args.size()]));
    }

    @Override
    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) throws SqlException {
        log.debug("Reading triggers for: " + tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplateDirty();
        String sql = "select "
                + "TRIG.name, "
                + "TAB.name as table_name, "
                + "SC.name as table_schema, "
                + "TRIG.is_disabled, "
                + "TRIG.is_ms_shipped, "
                + "TRIG.is_not_for_replication, "
                + "TRIG.is_instead_of_trigger, "
                + "TRIG.create_date, "
                + "TRIG.modify_date, "
                + "OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsUpdateTrigger') AS isupdate, "
                + "OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsDeleteTrigger') AS isdelete, "
                + "OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsInsertTrigger') AS isinsert, "
                + "OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsAfterTrigger') AS isafter, "
                + "OBJECTPROPERTY(TRIG.OBJECT_ID, 'ExecIsInsteadOfTrigger') AS isinsteadof, "
                + "TRIG.object_id, "
                + "TRIG.parent_id, "
                + "TAB.schema_id, "
                + "OBJECT_DEFINITION(TRIG.OBJECT_ID) as trigger_source "
                + "from sys.triggers as TRIG "
                + "inner join sys.tables as TAB "
                + "on TRIG.parent_id = TAB.object_id "
                + "inner join sys.schemas as SC "
                + "on TAB.schema_id = SC.schema_id "
                + "where TAB.name=? and SC.name=? ";
        return sqlTemplate.queryWithHandler(sql, new ISqlRowMapper<Trigger>() {
            @Override
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("name"));
                trigger.setSchemaName(row.getString("table_schema"));
                trigger.setTableName(row.getString("table_name"));
                trigger.setEnabled(!Boolean.valueOf(row.getString("is_disabled")));
                trigger.setSource(row.getString("trigger_source"));
                row.remove("trigger_source");
                // replace 0 and 1s with true and false
                for (String s : new String[] { "isupdate", "isdelete", "isinsert", "isafter", "isinsteadof" }) {
                    if (row.getString(s).equals("0")) {
                        row.put(s, false);
                    } else {
                        row.put(s, true);
                    }
                }
                if (row.getBoolean("isupdate")) {
                    trigger.setTriggerType(TriggerType.UPDATE);
                } else if (row.getBoolean("isdelete")) {
                    trigger.setTriggerType(TriggerType.DELETE);
                } else if (row.getBoolean("isinsert")) {
                    trigger.setTriggerType(TriggerType.INSERT);
                }
                trigger.setMetaData(row);
                return trigger;
            }
        }, new ChangeCatalogConnectionHandler(catalog), tableName, schema);
    }

    @Override
    protected IConnectionHandler getConnectionHandler(String catalog) {
        return new ChangeCatalogConnectionHandler(catalog == null ? platform.getDefaultCatalog() : catalog);
    }

    private String getIndicesQuery() {
        String sql = null;
        if (platform instanceof MsSql2008DatabasePlatform) {
            sql = """
                    SELECT [TABLENAME] = t.[Name]
                            ,[INDEXNAME] = i.[Name]
                            ,[IndexType] = i.[type_desc]
                            ,[FILTER] = i.filter_definition
                            ,[HASFILTER] = i.has_filter
                            ,[COMPRESSIONTYPE] = p.data_compression
                            ,[COMPRESSIONDESCRIPTION] = p.data_compression_desc
                            ,[COLUMN_NAME] = c.name
                            ,[IS_INCLUDED_COLUMN] = ixc.is_included_column
                    FROM sys.indexes i WITH (NOLOCK)
                    INNER JOIN sys.tables t WITH (NOLOCK) ON t.object_id = i.object_id
                    INNER JOIN sys.partitions p WITH (NOLOCK) ON p.object_id=t.object_id AND p.index_id=i.index_id
                    INNER JOIN sys.index_columns ixc WITH (NOLOCK) ON t.object_id=ixc.object_id and i.index_id=ixc.index_id
                    INNER JOIN sys.columns c WITH (NOLOCK) ON c.object_id=t.object_id and ixc.column_id=c.column_id
                    WHERE t.type_desc = N'USER_TABLE'
                            and t.name=?
                            and i.name in (%s)
                            and p.index_id > 1
                    """;
        } else if (platform instanceof MsSql2005DatabasePlatform) {
            sql = """
                    select [TABLENAME] = t.[Name]
                        ,[INDEXNAME] = i.[Name]
                        ,[COLUMN_NAME] = c.name
                        ,[IS_INCLUDED_COLUMN] = ixc.is_included_column
                    from sys.tables t WITH (NOLOCK)
                    INNER JOIN sys.indexes i WITH (NOLOCK) ON t.object_id=i.object_id
                    INNER JOIN sys.index_columns ixc WITH (NOLOCK) ON t.object_id=ixc.object_id and i.index_id=ixc.index_id
                    INNER JOIN sys.columns c WITH (NOLOCK) ON c.object_id=t.object_id and ixc.column_id=c.column_id
                    WHERE t.type_desc = N'USER_TABLE'
                        and i.type > 1
                        and t.name=?
                        and i.name in (%s)
                    """;
        }
        return sql;
    }

    @Override
    protected Collection<IIndex> readIndices(Connection connection,
            DatabaseMetaDataWrapper metaData, String tableName) throws SQLException {
        Collection<IIndex> indices = super.readIndices(connection, metaData, tableName);
        if (compressAndFilterAndIncludecolumns) {
            String sql = null;
            if (indices != null && indices.size() > 0) {
                sql = getIndicesQuery();
            }
            if (sql != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < indices.size(); i++) {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    sb.append("?");
                }
                sql = String.format(sql, sb.toString());
                log.debug(
                        "Running the following query to get metadata about whether an index has compression or filters\n {}",
                        sql);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    int i = 1;
                    ps.setString(i++, tableName);
                    for (IIndex index : indices) {
                        ps.setString(i++, index.getName());
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        Set<String> columnLabels = getColumnLabels(rs);
                        while (rs.next()) {
                            readIndex(indices, tableName, rs, columnLabels);
                        }
                    }
                }
            }
        }
        return indices;
    }

    private void readIndex(Collection<IIndex> indices, String tableName, ResultSet rs, Set<String> columnLabels) throws SQLException {
        String indexName = rs.getString("INDEXNAME");
        IIndex iIndex = findIndex(indexName, indices);
        if (iIndex != null) {
            if (columnLabels.contains("HASFILTER")) {
                boolean hasFilter = rs.getBoolean("HASFILTER");
                int compressionType = rs.getInt("COMPRESSIONTYPE");
                boolean hasCompression = (compressionType > 0);
                if (hasFilter || hasCompression) {
                    PlatformIndex platformIndex = findPlatformIndex(indexName, iIndex);
                    if (platformIndex == null) {
                        platformIndex = new PlatformIndex();
                        platformIndex.setName(indexName);
                    }
                    if (hasFilter) {
                        log.debug("table: " + tableName + " index: " + indexName + " has filter: " + rs.getString("FILTER"));
                        platformIndex.setFilterCondition("WHERE " + rs.getString("FILTER"));
                    }
                    if (hasCompression) {
                        if (compressionType == 1) {
                            log.debug("table: " + tableName + " index: " + indexName + " has compression: " + CompressionTypes.ROW.name());
                            platformIndex.setCompressionType(CompressionTypes.ROW);
                        } else if (compressionType == 2) {
                            log.debug("table: " + tableName + " index: " + indexName + " has compression: " + CompressionTypes.PAGE.name());
                            platformIndex.setCompressionType(CompressionTypes.PAGE);
                        } else if (compressionType == 3) {
                            log.debug("table: " + tableName + " index: " + indexName + " has compression: " + CompressionTypes.COLUMNSTORE.name());
                            platformIndex.setCompressionType(CompressionTypes.COLUMNSTORE);
                        } else if (compressionType == 4) {
                            log.debug("table: " + tableName + " index: " + indexName + " has compression: " + CompressionTypes.COLUMNSTORE_ARCHIVE
                                    .name());
                            platformIndex.setCompressionType(CompressionTypes.COLUMNSTORE_ARCHIVE);
                        } else {
                            platformIndex.setCompressionType(CompressionTypes.NONE);
                        }
                    }
                    iIndex.addPlatformIndex(platformIndex);
                }
            }
            if (columnLabels.contains("IS_INCLUDED_COLUMN")) {
                int includedColumn = rs.getInt("IS_INCLUDED_COLUMN");
                String columnName = rs.getString("COLUMN_NAME");
                if (includedColumn > 0) {
                    IndexColumn indexColumn = new IndexColumn();
                    indexColumn.setName(columnName);
                    iIndex.addIncludedColumn(indexColumn);
                }
            }
        }
    }

    private IIndex findIndex(String indexName, Collection<IIndex> indices) {
        for (IIndex index : indices) {
            if (Strings.CS.equals(index.getName(), indexName)) {
                return index;
            }
        }
        return null;
    }

    private PlatformIndex findPlatformIndex(String indexName, IIndex iIndex) {
        if (iIndex.getPlatformIndexes() != null && iIndex.getPlatformIndexes().containsKey(indexName)) {
            return iIndex.getPlatformIndexes().get(indexName);
        }
        return null;
    }

    private Set<String> getColumnLabels(ResultSet rs) throws SQLException {
        Set<String> s = new HashSet<String>();
        ResultSetMetaData rsmeta = rs.getMetaData();
        int columnCount = rsmeta.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            s.add(rsmeta.getColumnLabel(i));
        }
        return s;
    }

    @Override
    protected String getWithNoLockHint() {
        return " WITH (NOLOCK) ";
    }
}
