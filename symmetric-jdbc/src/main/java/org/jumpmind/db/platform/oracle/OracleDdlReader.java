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
package org.jumpmind.db.platform.oracle;

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
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;

/*
 * Reads a database model from an Oracle 8 database.
 */
public class OracleDdlReader extends AbstractJdbcDdlReader {
    /* The regular expression pattern for the Oracle conversion of ISO dates. */
    private Pattern oracleIsoDatePattern;
    /* The regular expression pattern for the Oracle conversion of ISO times. */
    private Pattern oracleIsoTimePattern;
    /*
     * The regular expression pattern for the Oracle conversion of ISO timestamps.
     */
    private Pattern oracleIsoTimestampPattern;
    private String REGEX_FRACTIONAL_SECOND_PRECISION = "\\([0-9]\\)";

    public OracleDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern("%");
        oracleIsoDatePattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD'\\)");
        oracleIsoTimePattern = Pattern.compile("TO_DATE\\('([^']*)'\\, 'HH24:MI:SS'\\)");
        oracleIsoTimestampPattern = Pattern
                .compile("TO_DATE\\('([^']*)'\\, 'YYYY\\-MM\\-DD HH24:MI:SS'\\)");
    }

    @Override
    protected Table readTable(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        /*
         * Oracle 10 added the recycle bin which contains dropped database objects not yet purged Since we don't want entries from the recycle bin, we filter
         * them out
         */
        boolean tableHasBeenDeleted = isTableInRecycleBin(connection, values);
        /*
         * System tables are in the system schema
         */
        String schema = (String) values.get(getResultSetSchemaName());
        if (!tableHasBeenDeleted && !"SYSTEM".equals(schema)) {
            Table table = super.readTable(connection, metaData, values);
            if (table != null) {
                determineAutoIncrementColumns(connection, table);
            }
            return table;
        } else {
            return null;
        }
    }

    protected boolean isTableInRecycleBin(Connection connection, Map<String, Object> values)
            throws SQLException {
        String tablename = (String) values.get("TABLE_NAME");
        return StringUtils.isNotBlank(tablename) && tablename.toLowerCase().startsWith("bin$");
    }

    @Override
    protected Integer mapUnknownJdbcTypeForColumn(Map<String, Object> values) {
        String typeName = (String) values.get("TYPE_NAME");
        if (typeName != null && typeName.startsWith("DATE")) {
            return Types.DATE;
        } else if (typeName != null && typeName.startsWith("TIMESTAMP")
                && !typeName.endsWith("TIME ZONE")) {
            // This is for Oracle's TIMESTAMP(9)
            return Types.TIMESTAMP;
        } else if (typeName != null && typeName.startsWith("TIMESTAMP")
                && typeName.endsWith("WITH TIME ZONE")) {
            return ColumnTypes.ORACLE_TIMESTAMPTZ;
        } else if (typeName != null && typeName.startsWith("TIMESTAMP")
                && typeName.endsWith("WITH LOCAL TIME ZONE")) {
            return ColumnTypes.ORACLE_TIMESTAMPLTZ;
        } else if (typeName != null && typeName.startsWith("NVARCHAR")) {
            // This is for Oracle's NVARCHAR type
            return Types.NVARCHAR;
        } else if (typeName != null && typeName.startsWith("LONGNVARCHAR")) {
            return Types.LONGVARCHAR;
        } else if (typeName != null && typeName.startsWith("NCHAR")) {
            return Types.NCHAR;
        } else if (typeName != null && typeName.startsWith("XML")) {
            return Types.SQLXML;
        } else if (typeName != null && typeName.startsWith("NCLOB")) {
            return Types.NCLOB;
        } else if (typeName != null && typeName.endsWith("CLOB")) {
            return Types.LONGVARCHAR;
        } else if (typeName != null && typeName.startsWith("BINARY_FLOAT")) {
            return Types.FLOAT;
        } else if (typeName != null && typeName.startsWith("BINARY_DOUBLE")) {
            return Types.DOUBLE;
        } else if (typeName != null && typeName.startsWith("BFILE")) {
            return Types.VARCHAR;
        } else if (typeName != null && typeName.startsWith("INTERVAL")) {
            return Types.VARCHAR;
        } else if (typeName != null && typeName.startsWith("ROWID")) {
            return Types.VARCHAR;
        } else if (typeName != null && typeName.startsWith("BOOL")) {
            return Types.BOOLEAN;
        } else {
            return super.mapUnknownJdbcTypeForColumn(values);
        }
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        Column column = super.readColumn(metaData, values);
        if (column.getMappedTypeCode() == Types.DECIMAL || column.getMappedTypeCode() == Types.NUMERIC) {
            // We're back-mapping the NUMBER columns returned by Oracle
            // Note that the JDBC driver returns DECIMAL for these NUMBER
            // columns for driver version 11 and before, but returns
            // NUMERIC for driver version 12 and later.
            if (column.getScale() <= -127 || column.getScale() >= 127) {
                if (column.getSizeAsInt() == 0) {
                    /*
                     * Latest oracle jdbc drivers for 11g return (0,-127) for types defined as integer resulting in bad mappings. NUMBER without scale or
                     * precision looks the same as INTEGER in the jdbc driver. We must check the Oracle meta data to see if type is an INTEGER.
                     */
                    if (isColumnInteger((String) values.get("TABLE_NAME"),
                            (String) values.get("COLUMN_NAME"))) {
                        column.setMappedTypeCode(Types.BIGINT);
                    }
                } else if (column.getSizeAsInt() <= 63) {
                    column.setMappedTypeCode(Types.REAL);
                } else {
                    column.setMappedTypeCode(Types.DOUBLE);
                }
            } else {
                // Let's map DECIMAL to NUMERIC since DECIMAL doesn't really exist in Oracle
                column.setMappedTypeCode(Types.NUMERIC);
            }
        } else if (column.getMappedTypeCode() == Types.FLOAT) {
            // Same for REAL, FLOAT, DOUBLE PRECISION, which all back-map to
            // FLOAT but with
            // different sizes (63 for REAL, 126 for FLOAT/DOUBLE PRECISION)
            switch (column.getSizeAsInt()) {
                case 63:
                    column.setMappedTypeCode(Types.REAL);
                    break;
                case 126:
                    column.setMappedTypeCode(Types.DOUBLE);
                    break;
            }
        } else if ((column.getMappedTypeCode() == Types.DATE)
                || (column.getMappedTypeCode() == Types.TIMESTAMP)
                || column.getMappedTypeCode() == ColumnTypes.ORACLE_TIMESTAMPTZ
                || column.getMappedTypeCode() == ColumnTypes.ORACLE_TIMESTAMPLTZ) {
            // we also reverse the ISO-format adaptation, and adjust the default
            // value to timestamp
            if (column.getDefaultValue() != null) {
                Timestamp timestamp = null;
                Matcher matcher = oracleIsoTimestampPattern.matcher(column.getDefaultValue());
                if (matcher.matches()) {
                    String timestampVal = matcher.group(1);
                    timestamp = Timestamp.valueOf(timestampVal);
                } else {
                    matcher = oracleIsoDatePattern.matcher(column.getDefaultValue());
                    if (matcher.matches()) {
                        String dateVal = matcher.group(1);
                        timestamp = new Timestamp(Date.valueOf(dateVal).getTime());
                    } else {
                        matcher = oracleIsoTimePattern.matcher(column.getDefaultValue());
                        if (matcher.matches()) {
                            String timeVal = matcher.group(1);
                            timestamp = new Timestamp(Time.valueOf(timeVal).getTime());
                        }
                    }
                }
                if (timestamp != null) {
                    column.setDefaultValue(timestamp.toString());
                }
            }
            if (column.getMappedTypeCode() == Types.DATE) {
                column.setSize("0");
                removePlatformColumnSize(column);
            } else {
                column.setJdbcTypeName(column.getJdbcTypeName().replaceAll(REGEX_FRACTIONAL_SECOND_PRECISION, ""));
                column.setSize(String.valueOf(column.getScale()));
                PlatformColumn platformColumn = column.findPlatformColumn(platform.getName());
                if (platformColumn != null) {
                    platformColumn.setType(platformColumn.getType().replaceAll(REGEX_FRACTIONAL_SECOND_PRECISION, ""));
                    platformColumn.setSize(column.getSizeAsInt());
                    platformColumn.setDecimalDigits(0);
                }
            }
        } else if (column.getMappedTypeCode() == Types.BINARY || column.getMappedTypeCode() == Types.VARBINARY) {
            String defaultValue = column.getDefaultValue();
            if (isNotBlank(defaultValue) && defaultValue.startsWith("'") && defaultValue.endsWith("'")) {
                defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
                column.setDefaultValue(defaultValue);
            }
        } else if (TypeMap.isTextType(column.getMappedTypeCode())) {
            String defaultValue = column.getDefaultValue();
            if (isNotBlank(defaultValue) && defaultValue.startsWith("('") && defaultValue.endsWith("')")) {
                defaultValue = defaultValue.substring(2, defaultValue.length() - 2);
            }
            column.setDefaultValue(unescape(defaultValue, "'", "''"));
        }
        return column;
    }

    private boolean isColumnInteger(String tableName, String columnName) {
        return (platform.getSqlTemplate().queryForInt(
                "select case when data_precision is null and data_scale=0 then 1 else 0 end " +
                        "from all_tab_columns where table_name=? and column_name=?", tableName, columnName) == 1);
    }

    /*
     * Helper method that determines the auto increment status using Firebird's system tables.
     *
     * @param table The table
     */
    protected void determineAutoIncrementColumns(Connection connection, Table table)
            throws SQLException {
        Column[] columns = table.getColumns();
        for (int idx = 0; idx < columns.length; idx++) {
            columns[idx].setAutoIncrement(isAutoIncrement(connection, table, columns[idx]));
        }
    }

    /*
     * Tries to determine whether the given column is an identity column.
     *
     * @param table The table
     *
     * @param column The column
     *
     * @return <code>true</code> if the column is an identity column
     */
    protected boolean isAutoIncrement(Connection connection, Table table, Column column)
            throws SQLException {
        // TODO: For now, we only check whether there is a sequence & trigger as
        // generated by DdlUtils
        // But once sequence/trigger support is in place, it might be possible
        // to 'parse' the
        // trigger body (via SELECT trigger_name, trigger_body FROM
        // user_triggers) in order to
        // determine whether it fits our auto-increment definition
        PreparedStatement prepStmt = null;
        IDdlBuilder builder = getPlatform().getDdlBuilder();
        String triggerName = builder.getConstraintName(OracleDdlBuilder.PREFIX_TRIGGER, table,
                column.getName(), null);
        String seqName = builder.getConstraintName(OracleDdlBuilder.PREFIX_SEQUENCE, table,
                column.getName(), null);
        if (!getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn()) {
            triggerName = triggerName.toUpperCase();
            seqName = seqName.toUpperCase();
        }
        try {
            prepStmt = connection
                    .prepareStatement("SELECT * FROM user_triggers WHERE trigger_name = ?");
            prepStmt.setString(1, triggerName);
            ResultSet resultSet = prepStmt.executeQuery();
            if (!resultSet.next()) {
                resultSet.close();
                return false;
            }
            // we have a trigger, so lets check the sequence
            prepStmt.close();
            prepStmt = connection
                    .prepareStatement("SELECT * FROM user_sequences WHERE sequence_name = ?");
            prepStmt.setString(1, seqName);
            resultSet = prepStmt.executeQuery();
            boolean resultFound = resultSet.next();
            resultSet.close();
            return resultFound;
        } finally {
            if (prepStmt != null) {
                prepStmt.close();
            }
        }
    }

    @Override
    protected Collection<IIndex> readIndices(Connection connection,
            DatabaseMetaDataWrapper metaData, String tableName) throws SQLException {
        // Oracle bug 4999817 causes a table analyze to execute in response to a
        // call to
        // DatabaseMetaData#getIndexInfo.
        // The bug is fixed in driver version 10.2.0.4. The bug is present in at
        // least
        // driver versions 10.2.0.1.0, 10.1.0.2.0, and 9.2.0.5.
        // To avoid this bug, we will access user_indexes view.
        // This also allows us to filter system-generated indices which are
        // identified by either
        // having GENERATED='Y' in the query result, or by their index names
        // being equal to the
        // name of the primary key of the table
        StringBuilder query = new StringBuilder();
        query.append("SELECT a.INDEX_NAME, a.INDEX_TYPE, a.UNIQUENESS, b.COLUMN_NAME, b.COLUMN_POSITION, c.COLUMN_EXPRESSION FROM ALL_INDEXES a ");
        query.append("JOIN ALL_IND_COLUMNS b ON a.table_name = b.table_name AND a.INDEX_NAME=b.INDEX_NAME AND a.TABLE_OWNER = b.TABLE_OWNER ");
        query.append("LEFT JOIN ALL_IND_EXPRESSIONS c ON a.table_name = c.table_name AND a.INDEX_NAME=c.INDEX_NAME AND a.TABLE_OWNER = c.TABLE_OWNER ");
        query.append("WHERE ");
        query.append("a.TABLE_NAME = ? ");
        query.append("AND a.GENERATED='N' ");
        query.append("AND a.TABLE_TYPE='TABLE' ");
        if (metaData.getSchemaPattern() != null) {
            query.append("AND a.TABLE_OWNER = ?");
        }
        Map<String, IIndex> indices = new LinkedHashMap<String, IIndex>();
        PreparedStatement stmt = null;
        try {
            Set<String> pkIndecies = readPkIndecies(connection, metaData.getSchemaPattern(), tableName);
            stmt = connection.prepareStatement(query.toString());
            stmt.setString(
                    1,
                    getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn() ? tableName
                            : tableName
                                    .toUpperCase());
            if (metaData.getSchemaPattern() != null) {
                stmt.setString(2, metaData.getSchemaPattern().toUpperCase());
            }
            ResultSet rs = stmt.executeQuery();
            Map<String, Object> values = new HashMap<String, Object>();
            while (rs.next()) {
                String name = rs.getString(1);
                if (pkIndecies.contains(name)) { // Filter PK indexes from these results.
                    continue;
                }
                String type = rs.getString(2);
                // Only read in normal oracle indexes (including function-based indexes)
                if (type.startsWith("NORMAL") || type.startsWith("FUNCTION-BASED NORMAL")) {
                    values.put("INDEX_TYPE", Short.valueOf(DatabaseMetaData.tableIndexOther));
                    values.put("INDEX_NAME", name);
                    values.put("NON_UNIQUE",
                            "UNIQUE".equalsIgnoreCase(rs.getString(3)) ? Boolean.FALSE
                                    : Boolean.TRUE);
                    if (type.startsWith("NORMAL")) {
                        values.put("COLUMN_NAME", rs.getString(4));
                    } else {
                        String columnName = rs.getString(6);
                        if (columnName != null) {
                            if (!columnName.contains("(")) {
                                columnName = "(" + columnName + ")";
                            } else if (columnName.startsWith("\"") && columnName.endsWith("\"")) {
                                columnName = "\"" + columnName + "\"";
                            }
                        }
                        values.put("COLUMN_NAME", columnName);
                    }
                    values.put("ORDINAL_POSITION", Short.valueOf(rs.getShort(5)));
                    readIndex(metaData, values, indices);
                } else if (log.isDebugEnabled()) {
                    log.debug("Skipping index " + name + " of type " + type);
                }
            }
            rs.close();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
        return indices.values();
    }

    @Override
    protected String getTableNamePattern(String tableName) {
        /*
         * When looking up a table definition, Oracle treats underscore (_) in the table name as a wildcard, so it needs to be escaped, or you'll get back
         * column names for more than one table. Example: DatabaseMetaData.metaData.getColumns(null, null, "SYM\\_NODE", null)
         */
        return String.format("%s", tableName).replaceAll("\\_", "/_");
    }

    public List<String> getTableNames(final String catalog, final String schema,
            final String[] tableTypes) {
        List<String> tableNames = new ArrayList<String>();
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplate();
        StringBuilder sql = new StringBuilder("select TABLE_NAME from ALL_TABLES");
        Object[] params = null;
        if (isNotBlank(schema)) {
            sql.append(" where OWNER=?");
            params = new Object[] { schema };
        }
        tableNames = sqlTemplate.query(sql.toString(), new ISqlRowMapper<String>() {
            public String mapRow(Row row) {
                return row.getString("TABLE_NAME");
            }
        }, params);
        return tableNames;
    }

    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) throws SqlException {
        List<Trigger> triggers = new ArrayList<Trigger>();
        log.debug("Reading triggers for: {}", tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplate();
        String sql = "SELECT TRIGGER_NAME, OWNER, TABLE_NAME, STATUS, TRIGGERING_EVENT FROM ALL_TRIGGERS WHERE TABLE_NAME=? and OWNER=?";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("TRIGGER_NAME"));
                trigger.setSchemaName(row.getString("OWNER"));
                trigger.setTableName(row.getString("TABLE_NAME"));
                trigger.setEnabled(Boolean.valueOf(row.getString("STATUS")));
                trigger.setSource("create ");
                String triggerType = row.getString("TRIGGERING_EVENT");
                if (triggerType.equals("DELETE")
                        || triggerType.equals("INSERT")
                        || triggerType.equals("UPDATE")) {
                    trigger.setTriggerType(TriggerType.valueOf(triggerType));
                }
                trigger.setMetaData(row);
                return trigger;
            }
        }, tableName, schema);
        for (final Trigger trigger : triggers) {
            String name = trigger.getName();
            String sourceSql = "select TEXT from all_source where OWNER=? AND NAME=? order by LINE";
            final StringBuilder buff = new StringBuilder();
            buff.append(trigger.getSource());
            sqlTemplate.query(sourceSql, new ISqlRowMapper<Trigger>() {
                public Trigger mapRow(Row row) {
                    String line = row.getString("TEXT");
                    buff.append(line);
                    if (!line.endsWith("\n")) {
                        buff.append("\n");
                    }
                    return trigger;
                }
            }, schema, name);
            trigger.setSource(buff.toString());
        }
        return triggers;
    }

    protected Set<String> readPkIndecies(Connection connection, String schema, String tableName) throws SQLException {
        String QUERY = "SELECT CONSTRAINT_NAME FROM ALL_CONSTRAINTS c WHERE c.TABLE_NAME = ? AND CONSTRAINT_TYPE = 'P'";
        if (schema != null) {
            QUERY += " AND c.OWNER = ?";
        }
        ResultSet rs = null;
        PreparedStatement stmt = null;
        Set<String> values = new LinkedHashSet<String>();
        try {
            stmt = connection.prepareStatement(QUERY);
            stmt.setString(
                    1,
                    getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn() ? tableName
                            : tableName
                                    .toUpperCase());
            if (schema != null) {
                stmt.setString(2, schema.toUpperCase());
            }
            rs = stmt.executeQuery();
            while (rs.next()) {
                String pkIndexName = rs.getString(1);
                values.add(pkIndexName);
            }
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
        }
        return values;
    }

    // Oracle does not support on update actions
    @Override
    protected void readForeignKeyUpdateRule(Map<String, Object> values, ForeignKey fk) {
        fk.setOnUpdateAction(ForeignKeyAction.NOACTION);
    }
}
