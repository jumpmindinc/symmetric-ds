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
package org.jumpmind.db.platform.postgresql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Function;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.PlatformFunction;
import org.jumpmind.db.model.PlatformTrigger;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.DdlException;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;

/*
 * Reads a database model from a PostgreSql database.
 */
public class PostgreSqlDdlReader extends AbstractJdbcDdlReader {
    private String includeColumnsQuery = "SELECT\n"
            + "    i.relname AS INDEX_NAME,\n"
            + "    ix.indnkeyatts AS NUM_OF_INDEX_COLUMNS,\n"
            + "    ix.indkey AS ALL_INDEX_COLUMNS,\n"
            + "    array_to_string(\n"
            + "        array(\n"
            + "            SELECT a.attname\n"
            + "            FROM pg_attribute a\n"
            + "            WHERE a.attrelid = t.oid\n"
            + "            AND a.attnum > 0\n"
            + "            ORDER BY a.attnum\n"
            + "        ), ', ') AS COLUMN_NAMES\n"
            + "FROM\n"
            + "    pg_class t\n"
            + "JOIN\n"
            + "    pg_index ix ON t.oid = ix.indrelid\n"
            + "JOIN\n"
            + "    pg_class i ON i.oid = ix.indexrelid\n"
            + "LEFT JOIN\n"
            + "    pg_constraint c ON (ix.indrelid = c.conrelid\n"
            + "                        AND ix.indexrelid = c.conindid\n"
            + "                        AND c.contype IN ('p', 'u', 'x'))\n"
            + "WHERE\n"
            + "    t.relkind = 'r'\n"
            + "    AND t.relname = ?\n"
            + "    AND ix.indnkeyatts > 0\n"
            + "    AND array_length(ix.indkey, 1) > ix.indnkeyatts\n"
            + "ORDER BY\n"
            + "    i.relname";

    public PostgreSqlDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern(null);
    }

    @Override
    protected Table readTable(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        Table table = super.readTable(connection, metaData, values);
        if (table == null) {
            return null;
        }
        detectAutoIncrementColumnsInUniqueIndices(table);
        readMetaDataAndPrimaryKeyConstraint(connection, table);
        return table;
    }

    /**
     * Detect and filter out PostgreSQL-specific unique indices for non-pk auto-increment columns which are of the form "[table]_[column]_key"
     */
    protected void detectAutoIncrementColumnsInUniqueIndices(Table table) {
        HashMap<String, IIndex> uniquesByName = new HashMap<String, IIndex>();
        for (int indexIdx = 0; indexIdx < table.getIndexCount(); indexIdx++) {
            IIndex index = table.getIndex(indexIdx);
            if (index.isUnique() && (index.getName() != null)) {
                uniquesByName.put(index.getName(), index);
            }
        }
        for (int columnIdx = 0; columnIdx < table.getColumnCount(); columnIdx++) {
            Column column = table.getColumn(columnIdx);
            if (column.isAutoIncrement() && !column.isPrimaryKey()) {
                String indexName = table.getName() + "_" + column.getName() + "_key";
                if (uniquesByName.containsKey(indexName)) {
                    table.removeIndex(uniquesByName.get(indexName));
                    uniquesByName.remove(indexName);
                }
            }
        }
    }

    /**
     * Reads additional information about the table (meta data) in one round-trip to the database.
     * <ul>
     * <li>Name of the Primary key constraint</li>
     * <li>LOGGED mode (true/false)</li>
     * </ul>
     */
    protected void readMetaDataAndPrimaryKeyConstraint(Connection connection, Table table) throws SQLException {
        long startTime = System.currentTimeMillis();
        final String TRAIT_LOGGING_MODE = "LOGGING_MODE";
        final String TRAIT_PRIMARY_KEY_NAME = "PRIMARY_KEY_NAME";
        StringBuilder sqlBuilder = new StringBuilder(1000);
        sqlBuilder.append("WITH table_object AS ( ")
                .append("    select n.nspname as nsp_name, n.oid as nsp_oid ")
                .append("    , t.relname as table_name, t.oid as table_oid ")
                .append("    , t.relpersistence ")
                .append(" FROM pg_class t ")
                .append(" JOIN pg_namespace n")
                .append(" on n.oid=t.relnamespace and n.nspname = ? ")
                .append(" WHERE t.relname = ? ")
                .append(" ) \n")
                .append("SELECT '")
                .append(TRAIT_LOGGING_MODE)
                .append("' as trait")
                .append(" ,CASE relpersistence WHEN 'p' THEN 'true' else 'false' end as value")
                .append(" FROM table_object")
                .append("\nUNION ALL\n")
                .append("SELECT '")
                .append(TRAIT_PRIMARY_KEY_NAME)
                .append("' as trait")
                .append(" ,conname as value")
                .append(" FROM pg_constraint")
                .append(" WHERE contype='p'")
                .append(" and conrelid in (select table_oid from table_object)")
                .append(";");
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = connection.prepareStatement(sqlBuilder.toString());
            ps.setString(1, table.getSchema());
            ps.setString(2, table.getName());
            rs = ps.executeQuery();
            while (rs.next()) {
                String traitName = rs.getString(1).trim();
                String traitValue = rs.getString(2).trim();
                switch (traitName) {
                    case TRAIT_LOGGING_MODE:
                        table.setLogging("true".equals(traitValue));
                        break;
                    case TRAIT_PRIMARY_KEY_NAME:
                        table.setPrimaryKeyConstraintName(traitValue);
                        break;
                    default:
                        log.warn(String.format("readMetaDataAndPrimaryKeyConstraint - Ignored an unrecognized trait=%s; Table=%s", traitName, table.getName()));
                        break;
                }
            }
        } finally {
            JdbcSqlTemplate.close(rs);
            JdbcSqlTemplate.close(ps);
        }
        long durationInMillis = System.currentTimeMillis() - startTime;
        log.debug(String.format("readMetaDataAndPrimaryKeyConstraint - Done. Table=%s; Logging=%b; Duration=%d ms", table.getName(), table.getLogging(),
                durationInMillis));
    }

    @Override
    protected Integer mapUnknownJdbcTypeForColumn(Map<String, Object> values) {
        String typeName = (String) values.get("TYPE_NAME");
        Integer type = (Integer) values.get("DATA_TYPE");
        if (typeName != null && typeName.equalsIgnoreCase("ABSTIME")) {
            return Types.TIMESTAMP;
        } else if (typeName != null && typeName.equalsIgnoreCase("TIMESTAMPTZ")) {
            return ColumnTypes.TIMESTAMPTZ;
        } else if (typeName != null && typeName.equalsIgnoreCase("TIMETZ")) {
            return ColumnTypes.TIMETZ;
        } else if (PostgreSqlDatabasePlatform.isBlobStoredByReference(typeName)) {
            return Types.BLOB;
        } else if (type != null && (type == Types.STRUCT || type == Types.OTHER)) {
            return Types.LONGVARCHAR;
        } else if (typeName != null && typeName.equalsIgnoreCase("BIT")) {
            return Types.VARCHAR;
        } else if (typeName != null && typeName.toUpperCase().contains("BOOL")) {
            return Types.BOOLEAN;
        } else {
            return super.mapUnknownJdbcTypeForColumn(values);
        }
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values) throws SQLException {
        Column column = super.readColumn(metaData, values);
        PlatformColumn platformColumn = column.findPlatformColumn(platform.getName());
        if (platformColumn != null) {
            if ("serial".equals(platformColumn.getType()) || "serial4".equals(platformColumn.getType())) {
                platformColumn.setType("int4");
            } else if ("bigserial".equals(platformColumn.getType()) || "serial8".equals(platformColumn.getType())) {
                platformColumn.setType("int8");
            }
        }
        if (column.getSize() != null) {
            if (column.getSizeAsInt() <= 0) {
                column.setSize(null);
                // PostgreSQL reports BYTEA and TEXT as BINARY(-1) and
                // VARCHAR(-1) respectively
                // Since we cannot currently use the Blob/Clob interface with
                // BYTEA, we instead
                // map them to LONGVARBINARY/LONGVARCHAR
                if (column.getMappedTypeCode() == Types.BINARY) {
                    column.setMappedTypeCode(Types.LONGVARBINARY);
                } else if (column.getMappedTypeCode() == Types.VARCHAR) {
                    column.setMappedTypeCode(Types.LONGVARCHAR);
                }
            }
            // fix issue DDLUTILS-165 as postgresql-8.2-504-jdbc3.jar seems to
            // return Integer.MAX_VALUE
            // on columns defined as TEXT.
            else if (column.getSizeAsInt() == Integer.MAX_VALUE) {
                if (column.getMappedTypeCode() == Types.VARCHAR) {
                    if (column.getJdbcTypeName().equalsIgnoreCase("TEXT")) {
                        column.setMappedTypeCode(Types.LONGVARCHAR);
                        column.setSize(null);
                    }
                    if (platformColumn != null) {
                        platformColumn.setSize(-1);
                    }
                } else if (column.getMappedTypeCode() == Types.BINARY) {
                    column.setMappedTypeCode(Types.LONGVARBINARY);
                    column.setSize(null);
                }
            } else if (column.getSizeAsInt() == 131089 && column.getJdbcTypeCode() == Types.NUMERIC) {
                column.setSizeAndScale(0, 0);
                column.setMappedTypeCode(Types.DECIMAL);
                if (platformColumn != null) {
                    platformColumn.setSize(-1);
                    platformColumn.setDecimalDigits(-1);
                }
            }
        }
        if (column.getJdbcTypeCode() == Types.TIMESTAMP || column.getJdbcTypeCode() == Types.TIME || column.getJdbcTypeCode() == ColumnTypes.TIMESTAMPTZ
                || column.getJdbcTypeCode() == ColumnTypes.TIMETZ) {
            resetColumnSize(column, String.valueOf(column.getScale()));
        }
        if (column.getJdbcTypeCode() == Types.DATE) {
            removeColumnSize(column);
        }
        String defaultValue = column.getDefaultValue();
        if ((defaultValue != null) && (defaultValue.length() > 0)) {
            // If the default value looks like
            // "nextval('ROUNDTRIP_VALUE_seq'::text)"
            // then it is an auto-increment column
            if (defaultValue.startsWith("nextval(") ||
                    (PostgreSqlDdlBuilder.isUsePseudoSequence() && defaultValue.endsWith("seq()"))) {
                column.setAutoIncrement(true);
                defaultValue = null;
            } else {
                // PostgreSQL returns default values in the forms
                // "-9000000000000000000::bigint" or
                // "'some value'::character varying" or "'2000-01-01'::date"
                switch (column.getMappedTypeCode()) {
                    case Types.INTEGER:
                    case Types.BIGINT:
                    case Types.DECIMAL:
                    case Types.NUMERIC:
                        defaultValue = extractUndelimitedDefaultValue(defaultValue);
                        break;
                    case Types.CHAR:
                    case Types.VARCHAR:
                    case Types.LONGVARCHAR:
                    case Types.DATE:
                    case Types.TIME:
                    case Types.TIMESTAMP:
                        defaultValue = extractDelimitedDefaultValue(defaultValue);
                        break;
                }
                if (TypeMap.isTextType(column.getMappedTypeCode())) {
                    // We assume escaping via double quote (see also the
                    // backslash_quote setting:
                    // http://www.postgresql.org/docs/7.4/interactive/runtime-config.html#RUNTIME-CONFIG-COMPATIBLE)
                    defaultValue = unescape(defaultValue, "'", "''");
                }
            }
            column.setDefaultValue(defaultValue);
        }
        return column;
    }

    /*
     * Extractes the default value from a default value spec of the form "'some value'::character varying" or "'2000-01-01'::date".
     * 
     * @param defaultValue The default value spec
     * 
     * @return The default value
     */
    private String extractDelimitedDefaultValue(String defaultValue) {
        if (defaultValue.startsWith("'")) {
            int valueEnd = defaultValue.indexOf("'::");
            if (valueEnd > 0) {
                return defaultValue.substring("'".length(), valueEnd);
            }
        }
        return defaultValue;
    }

    /*
     * Extractes the default value from a default value spec of the form "-9000000000000000000::bigint".
     * 
     * @param defaultValue The default value spec
     * 
     * @return The default value
     */
    private String extractUndelimitedDefaultValue(String defaultValue) {
        int valueEnd = defaultValue.indexOf("::");
        if (valueEnd > 0) {
            defaultValue = defaultValue.substring(0, valueEnd);
        } else {
            if (defaultValue.startsWith("(") && defaultValue.endsWith(")")) {
                defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
            }
        }
        return defaultValue;
    }

    @Override
    protected void readForeignKey(DatabaseMetaDataWrapper metaData, Map<String, Object> values,
            Map<String, ForeignKey> knownFks) throws SQLException {
        String fkName = (String) values.get(getName("FK_NAME"));
        ForeignKey fk = knownFks.get(fkName);
        if (fk == null) {
            fk = new ForeignKey(fkName);
            fk.setForeignTableName((String) values.get(getName("PKTABLE_NAME")));
            fk.setForeignTableCatalog((String) values.get(getName("PKTABLE_CAT")));
            String fkSchema = (String) values.get(getName("PKTABLE_SCHEM"));
            if (StringUtils.isBlank(fkSchema)) {
                fkSchema = (String) values.get(getName("pktable_schem"));
            }
            fk.setForeignTableSchema(fkSchema);
            readForeignKeyUpdateRule(values, fk);
            readForeignKeyDeleteRule(values, fk);
            knownFks.put(fkName, fk);
        }
        Reference ref = new Reference();
        ref.setForeignColumnName((String) values.get(getName("PKCOLUMN_NAME")));
        ref.setLocalColumnName((String) values.get(getName("FKCOLUMN_NAME")));
        if (values.containsKey(getName("KEY_SEQ"))) {
            ref.setSequenceValue(((Short) values.get(getName("KEY_SEQ"))).intValue());
        }
        fk.addReference(ref);
    }

    @Override
    protected boolean isInternalForeignKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, ForeignKey fk, IIndex index) {
        // PostgreSQL does not return an index for a foreign key
        return false;
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) {
        return table.doesIndexContainOnlyPrimaryKeyColumns(index);
    }

    @Override
    public List<String> getCatalogNames() {
        ArrayList<String> list = new ArrayList<String>();
        list.add(platform.getSqlTemplateDirty().queryForObject("select current_database()", String.class));
        return list;
    }

    @Override
    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) {
        List<Trigger> triggers = new ArrayList<Trigger>();
        log.debug("Reading triggers for: " + tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform
                .getSqlTemplate();
        String sql = "SELECT "
                + "trigger_name, "
                + "trigger_schema, "
                + "trigger_catalog, "
                + "event_manipulation AS trigger_type, "
                + "event_object_table AS table_name,"
                + "trig.*, "
                + "pgproc.prosrc, "
                + "pg_get_functiondef(pgproc.proname::regproc) AS functiontext, "
                + "pg_get_triggerdef(pgtrig.oid, true) as triggertext, "
                + "nmspace.nspname as funcschema, "
                + "pgproc.proname as proname "
                + "FROM INFORMATION_SCHEMA.TRIGGERS AS trig "
                + "INNER JOIN pg_catalog.pg_trigger AS pgtrig "
                + "ON pgtrig.tgname=trig.trigger_name "
                + "INNER JOIN pg_catalog.pg_proc AS pgproc "
                + "ON pgproc.oid=pgtrig.tgfoid "
                + "INNER JOIN pg_catalog.pg_namespace as nmspace "
                + "ON pgproc.pronamespace = nmspace.oid "
                + "WHERE event_object_table=? AND event_object_schema=?;";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            @Override
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("trigger_name"));
                trigger.setCatalogName(row.getString("trigger_catalog"));
                trigger.setSchemaName(row.getString("trigger_schema"));
                trigger.setTableName(row.getString("table_name"));
                trigger.setEnabled(true);
                trigger.setSource(row.getString("prosrc"));
                row.remove("prosrc");
                String triggerType = row.getString("trigger_type");
                if (triggerType.equals("DELETE")
                        || triggerType.equals("INSERT")
                        || triggerType.equals("UPDATE")) {
                    trigger.setTriggerType(TriggerType.valueOf(triggerType));
                }
                trigger.setMetaData(row);
                return trigger;
            }
        }, tableName, schema);
        return triggers;
    }

    @Override
    public PlatformTrigger getPlatformTrigger(IDatabasePlatform platform, Trigger trigger) {
        int majorVersion = getPlatform().getDatabaseVersion().getVersion();
        int minorVersion = getPlatform().getDatabaseVersion().getMinorVersion();
        PlatformTrigger platformTrigger = null;
        if (majorVersion > 8 || (majorVersion == 8 && minorVersion >= 4)) {
            platformTrigger = new PlatformTrigger(platform.getName(), trigger.getSource());
            if (trigger.getMetaData() instanceof Row) {
                Row triggerMetaData = (Row) trigger.getMetaData();
                String triggertext = triggerMetaData.getString("triggertext");
                platformTrigger.setTriggerText(triggertext);
                Function function = new Function();
                function.setCatalogName(triggerMetaData.getString("trigger_catalog"));
                function.setSchemaName(triggerMetaData.getString("funcschema"));
                function.setFunctionName(triggerMetaData.getString("proname"));
                function.setTableName(triggerMetaData.getString("table_name"));
                function.setTriggerName(triggerMetaData.getString("trigger_name"));
                PlatformFunction platformFunction = new PlatformFunction();
                platformFunction.setName(platform.getName());
                platformFunction.setFunctionText(triggerMetaData.getString("functiontext"));
                function.addPlatformFunction(platformFunction);
                platformTrigger.setFunction(function);
            }
        } else {
            // TODO Log something here once in a while
        }
        return platformTrigger;
    }

    @Override
    protected Collection<IIndex> readIndices(Connection connection, DatabaseMetaDataWrapper metaData,
            String tableName) throws SQLException {
        Collection<IIndex> indices = super.readIndices(connection, metaData, tableName);
        if (metaData.getMetaData().getDatabaseMajorVersion() < 13
                || indices == null || indices.isEmpty()) {
            return indices;
        }
        try (PreparedStatement ps = connection.prepareStatement(includeColumnsQuery)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    readIndex(indices, rs);
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to get index metadata for table {} due to {}.", tableName, ex.getMessage());
            throw new DdlException(ex);
        }
        return indices;
    }

    protected void readIndex(Collection<IIndex> indices, ResultSet rs) throws SQLException {
        IIndex index = findIndex(rs.getString("INDEX_NAME"), indices);
        int numOfIndexColumns = rs.getInt("NUM_OF_INDEX_COLUMNS"); // Does not contain INCLUDE columns
        String[] allIndexColumns = rs.getString("ALL_INDEX_COLUMNS").trim().split(" ");
        String[] columnNames = rs.getString("COLUMN_NAMES").split(", ");
        List<IndexColumn> indexColumns = getIndexColumnsFromSql(numOfIndexColumns, allIndexColumns, columnNames);
        List<IndexColumn> includeIndexColumns = getIncludeIndexColumnsFromSql(numOfIndexColumns, allIndexColumns, columnNames);
        removeExistingIndexColumns(index, indexColumns, includeIndexColumns);
        includeIndexColumns.forEach(includeIndexColumn -> {
            index.addIncludedColumn(includeIndexColumn);
        });
    }

    protected void removeExistingIndexColumns(IIndex index, List<IndexColumn> indexColumns, List<IndexColumn> includeIndexColumns) {
        List<IndexColumn> filterExistingColumns = includeIndexColumns.stream()
                .filter(includeColumn -> {
                    return !indexColumns.contains(includeColumn);
                })
                .collect(Collectors.toList());
        filterExistingColumns.forEach(filterColumn -> {
            index.removeColumn(
                    findIndexColumn(filterColumn.getName(), index.getColumns()));
        });
    }

    protected List<IndexColumn> getIncludeIndexColumnsFromSql(int numOfIndexColumns, String[] allIndexColumns, String[] columnNames) {
        return IntStream.range(numOfIndexColumns, allIndexColumns.length)
                .mapToObj(j -> {
                    int columnPosition = Integer.parseInt(allIndexColumns[j]);
                    return new IndexColumn(columnNames[columnPosition - 1]);
                })
                .collect(Collectors.toList());
    }

    protected List<IndexColumn> getIndexColumnsFromSql(int numOfIndexColumns, String[] allIndexColumns, String[] columnNames) {
        return IntStream.range(0, numOfIndexColumns)
                .mapToObj(i -> {
                    int columnPosition = Integer.parseInt(allIndexColumns[i]);
                    return new IndexColumn(columnNames[columnPosition - 1]);
                })
                .collect(Collectors.toList());
    }

    protected IndexColumn findIndexColumn(String columnName, IndexColumn[] indexColumns) {
        return Arrays.stream(indexColumns)
                .filter(indexColumn -> StringUtils.equals(indexColumn.getName(), columnName))
                .findFirst()
                .orElse(null);
    }

    protected IIndex findIndex(String indexName, Collection<IIndex> indices) {
        return indices.stream()
                .filter(index -> StringUtils.equals(index.getName(), indexName))
                .findFirst()
                .orElse(null);
    }
}
