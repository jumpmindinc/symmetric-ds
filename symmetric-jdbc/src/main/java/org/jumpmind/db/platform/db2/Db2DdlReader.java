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
package org.jumpmind.db.platform.db2;

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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;

/*
 * Reads a database model from a Db2 UDB database.
 */
public class Db2DdlReader extends AbstractJdbcDdlReader {
    /* Known system tables that Db2 creates (e.g. automatic maintenance). */
    private static final String[] KNOWN_SYSTEM_TABLES = { "STMG_DBSIZE_INFO", "HMON_ATM_INFO",
            "HMON_COLLECTION", "POLICY" };
    /* The regular expression pattern for the time values that Db2 returns. */
    private Pattern db2TimePattern = Pattern.compile("'(\\d{2}).(\\d{2}).(\\d{2})'");
    /* The regular expression pattern for the timestamp values that Db2 returns. */
    private Pattern db2TimestampPattern = Pattern
            .compile("'(\\d{4}\\-\\d{2}\\-\\d{2})\\-(\\d{2}).(\\d{2}).(\\d{2})(\\.\\d{1,8})?'");

    public Db2DdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        String tableName = (String) values.get("TABLE_NAME");
        for (int idx = 0; idx < KNOWN_SYSTEM_TABLES.length; idx++) {
            if (KNOWN_SYSTEM_TABLES[idx].equals(tableName)) {
                return null;
            }
        }
        Relation relation = super.readRelation(connection, metaData, values);
        if (relation instanceof Table table) {
            enhanceTableMetaData(connection, metaData, table);
        }
        return relation;
    }

    protected void enhanceTableMetaData(Connection connection, DatabaseMetaDataWrapper metaData, Table table) throws SQLException {
        log.debug("about to read additional column data");
        /*
         * DB2 does not return the auto-increment status via the database metadata
         */
        String sql = "SELECT NAME, IDENTITY FROM SYSIBM.SYSCOLUMNS WHERE TBNAME=?";
        if (StringUtils.isNotBlank(metaData.getSchemaPattern())) {
            sql = sql + " AND TBCREATOR=?";
        }
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, table.getName());
            if (StringUtils.isNotBlank(metaData.getSchemaPattern())) {
                pstmt.setString(2, metaData.getSchemaPattern());
            }
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String columnName = rs.getString(1);
                Column column = table.getColumnWithName(columnName);
                if (column != null) {
                    String isIdentity = rs.getString(2);
                    if (isIdentity != null && isIdentity.startsWith("Y")) {
                        column.setAutoIncrement(true);
                        if (log.isDebugEnabled()) {
                            log.debug("Found identity column {} on {}", columnName,
                                    table.getName());
                        }
                    }
                }
            }
        } finally {
            JdbcSqlTemplate.close(rs);
            JdbcSqlTemplate.close(pstmt);
        }
        log.debug("done reading additional column data");
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        Column column = super.readColumn(metaData, values);
        if (column.getDefaultValue() != null) {
            if (column.getMappedTypeCode() == Types.TIME) {
                Matcher matcher = db2TimePattern.matcher(column.getDefaultValue());
                // Db2 returns "HH24.MI.SS"
                if (matcher.matches()) {
                    StringBuilder newDefault = new StringBuilder();
                    newDefault.append("'");
                    // the hour
                    newDefault.append(matcher.group(1));
                    newDefault.append(":");
                    // the minute
                    newDefault.append(matcher.group(2));
                    newDefault.append(":");
                    // the second
                    newDefault.append(matcher.group(3));
                    newDefault.append("'");
                    column.setDefaultValue(newDefault.toString());
                }
            } else if (column.getMappedTypeCode() == Types.TIMESTAMP) {
                Matcher matcher = db2TimestampPattern.matcher(column.getDefaultValue());
                // Db2 returns "YYYY-MM-DD-HH24.MI.SS.FF"
                if (matcher.matches()) {
                    StringBuilder newDefault = new StringBuilder();
                    newDefault.append("'");
                    // group 1 is the date which has the correct format
                    newDefault.append(matcher.group(1));
                    newDefault.append(" ");
                    // the hour
                    newDefault.append(matcher.group(2));
                    newDefault.append(":");
                    // the minute
                    newDefault.append(matcher.group(3));
                    newDefault.append(":");
                    // the second
                    newDefault.append(matcher.group(4));
                    // optionally, the fraction
                    if ((matcher.groupCount() > 4) && (matcher.group(4) != null)) {
                        newDefault.append(matcher.group(5));
                    }
                    newDefault.append("'");
                    column.setDefaultValue(newDefault.toString());
                }
            } else if (TypeMap.isTextType(column.getMappedTypeCode())) {
                String defaultValue = column.getDefaultValue();
                // DB2 stores default text values quoted
                if ((defaultValue.length() >= 2) && defaultValue.startsWith("'") && defaultValue.endsWith("'")) {
                    defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
                }
                column.setDefaultValue(unescape(defaultValue, "'", "''"));
            }
        }
        if (column.getJdbcTypeCode() == Types.TIMESTAMP || column.getJdbcTypeCode() == Types.TIME) {
            resetColumnSize(column, String.valueOf(column.getScale()));
        }
        if (column.getJdbcTypeCode() == Types.DATE) {
            removeColumnSize(column);
        }
        return column;
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) throws SQLException {
        // Db2 uses the form "SQL060205225246220" if the primary key was defined
        // during table creation
        // When the ALTER TABLE way was used however, the index has the name of
        // the primary key
        if (index.getName().startsWith("SQL")) {
            try {
                Long.parseLong(index.getName().substring(3));
                return true;
            } catch (NumberFormatException ex) {
                // we ignore it
            }
            return false;
        } else {
            // we'll compare the index name to the names of all primary keys
            // TODO: Once primary key names are supported, this can be done
            // easier via the table object
            ResultSet pkData = null;
            HashSet<String> pkNames = new HashSet<String>();
            try {
                log.debug("getting pk info");
                pkData = metaData.getPrimaryKeys(table.getName());
                log.debug("done getting pk info");
                while (pkData.next()) {
                    Map<String, Object> values = readMetaData(pkData, getColumnsForPK());
                    pkNames.add((String) values.get("PK_NAME"));
                }
            } finally {
                if (pkData != null) {
                    pkData.close();
                }
            }
            return pkNames.contains(index.getName());
        }
    }

    @Override
    protected boolean isInternalForeignKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, ForeignKey fk, IIndex index) throws SQLException {
        return fk.getName().equalsIgnoreCase(index.getName());
    }

    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) throws SqlException {
        List<Trigger> triggers = new ArrayList<Trigger>();
        log.debug("Reading triggers for: " + tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform
                .getSqlTemplate();
        String sql = "SELECT "
                + "NAME as TRIGGER_NAME, "
                + "SCHEMA, "
                + "DEFINER, "
                + "TBNAME as TABLE_NAME, "
                + "TBCREATOR as TABLE_CREATOR, "
                + "TRIGEVENT as TRIGGER_TYPE, "
                + "TRIGTIME as TRIGGER_TIME, "
                + "GRANULARITY, "
                + "VALID, "
                + "TEXT, "
                + "ENABLED, "
                + "CREATE_TIME, "
                + "FUNC_PATH as FUNCTION_PATH, "
                + "ALTER_TIME as LAST_ALTERED "
                + "FROM SYSIBM.SYSTRIGGERS "
                + "WHERE TBNAME=? and SCHEMA=?";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("TRIGGER_NAME"));
                trigger.setSchemaName(row.getString("SCHEMA"));
                trigger.setTableName(row.getString("TABLE_NAME"));
                trigger.setEnabled(row.getString("ENABLED").equals("Y"));
                trigger.setSource(row.getString("TEXT"));
                row.remove("TEXT");
                String trigEvent = row.getString("TRIGGER_TYPE");
                switch (trigEvent.charAt(0)) {
                    case ('I'):
                        trigEvent = "INSERT";
                        break;
                    case ('U'):
                        trigEvent = "UPDATE";
                        break;
                    case ('D'):
                        trigEvent = "DELETE";
                }
                trigger.setTriggerType(TriggerType.valueOf(trigEvent));
                row.put("TRIGGER_TYPE", trigEvent);
                switch (row.getString("TRIGGER_TIME").charAt(0)) {
                    case ('A'):
                        row.put("TRIGGER_TIME", "AFTER");
                        break;
                    case ('B'):
                        row.put("TRIGGER_TIME", "BEFORE");
                        break;
                    case ('I'):
                        row.put("TRIGGER_TIME", "INSTEAD OF");
                }
                if (row.getString("GRANULARITY").equals("S"))
                    row.put("GRANULARITY", "ONCE PER STATEMENT");
                else if (row.getString("GRANULARITY").equals("R"))
                    row.put("GRANULARITY", "ONCE PER ROW");
                trigger.setMetaData(row);
                return trigger;
            }
        }, tableName, schema);
        return triggers;
    }

    @Override
    protected Integer mapUnknownJdbcTypeForColumn(Map<String, Object> values) {
        String typeName = (String) values.get("TYPE_NAME");
        if (typeName != null && typeName.startsWith("ROWID")) {
            return Types.VARCHAR;
        } else if (typeName != null && typeName.endsWith("CLOB")) {
            return Types.LONGVARCHAR;
        } else if (typeName != null && typeName.endsWith("LONG VARCHAR")) {
            return Types.CLOB;
        } else if (typeName != null && typeName.endsWith("XML")) {
            return Types.SQLXML;
        } else {
            return super.mapUnknownJdbcTypeForColumn(values);
        }
    }

    @Override
    protected void removeGeneratedColumns(Connection connection, DatabaseMetaDataWrapper metaData, Table table) throws SQLException {
        Collection<Column> tempColumns = new ArrayList<Column>();
        boolean found = false;
        Column[] columns = table.getColumns();
        for (int i = 0; columns != null && i < columns.length; i++) {
            if (columns[i].getMappedTypeCode() == Types.ROWID || columns[i].getName().equals("DB2_GENERATED_ROWID_FOR_LOBS")) {
                found = true;
                log.info("Found generated and/or rowid column on table " + table.getFullyQualifiedName() + ", column " + columns[i].getName());
            } else {
                tempColumns.add(columns[i]);
            }
        }
        if (found) {
            table.removeAllColumns();
            table.addColumns(tempColumns);
        }
    }
}
