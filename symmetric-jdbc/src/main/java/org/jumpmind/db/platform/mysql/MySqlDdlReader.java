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
package org.jumpmind.db.platform.mysql;

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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.PlatformColumn;
import org.jumpmind.db.model.Reference;
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
import org.jumpmind.util.VersionUtil;

/*
 * Reads a database model from a MySql database.
 */
public class MySqlDdlReader extends AbstractJdbcDdlReader {
    private Boolean mariaDbDriver = null;
    private boolean supportsGeneratedColumns;

    public MySqlDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern("%");
        setDefaultColumnPattern("%");
        String versionString = platform.getSqlTemplate().getDatabaseProductVersion();
        supportsGeneratedColumns = !VersionUtil.isOlderThanVersion(versionString, "5.7");
    }

    @Override
    protected String getResultSetCatalogName() {
        if (isMariaDbDriver()) {
            return "TABLE_SCHEMA";
        } else {
            return super.getResultSetCatalogName();
        }
    }

    @Override
    protected Table readTable(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        // TODO This needs some more work, since table names can be case
        // sensitive or lowercase
        // depending on the platform (really cute).
        // See http://dev.mysql.com/doc/refman/4.1/en/name-case-sensitivity.html
        // for more info.
        Table table = super.readTable(connection, metaData, values);
        if (table != null) {
            determineExtraColumnInfo(table);
        }
        return table;
    }

    protected void determineExtraColumnInfo(Table table) {
        String sql = "SELECT column_name, extra, column_type" + (supportsGeneratedColumns ? ", generation_expression" : "") +
                " FROM information_schema.columns WHERE table_schema = ? AND table_name = ?";
        List<Row> rows = platform.getSqlTemplateDirty().query(sql, new Object[] { table.getCatalog(), table.getName() });
        for (Row row : rows) {
            String extra = row.getString("extra");
            String columnName = row.getString("column_name");
            String columnType = row.getString("column_type");
            if (StringUtils.isNotBlank(extra)) {
                Column column = table.findColumn(columnName);
                if (column != null) {
                    if (supportsGeneratedColumns && column.isGenerated()) {
                        if (extra.equalsIgnoreCase("DEFAULT_GENERATED")) {
                            column.setGenerated(false);
                            column.setExpressionAsDefaultValue(true);
                        } else if (column.getDefaultValue() == null || column.getDefaultValue().equalsIgnoreCase("NULL")) {
                            column.setDefaultValue(row.getString("generation_expression"));
                        }
                    } else if (column.getMappedTypeCode() == Types.TIMESTAMP) {
                        column.setAutoUpdate(extra.toLowerCase().startsWith("on update"));
                    } else if (extra.equalsIgnoreCase("auto_increment")) {
                        column.setAutoIncrement(true);
                    }
                }
            }
            if (columnType != null && columnType.toLowerCase().startsWith("enum(")) {
                String[] parsedEnums = columnType.substring(5, columnType.length() - 1).split(",");
                for (int i = 0; i < parsedEnums.length; i++) {
                    parsedEnums[i] = StringUtils.unwrap(parsedEnums[i], "'");
                }
                Column column = table.findColumn(columnName);
                if (column != null) {
                    PlatformColumn platformColumn = column.getPlatformColumns().get(platform.getName());
                    if (platformColumn != null) {
                        platformColumn.setEnumValues(parsedEnums);
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            log.warn("Could not find extra column info for table {}", table.getFullyQualifiedTableName());
        }
    }

    @Override
    protected Integer mapUnknownJdbcTypeForColumn(Map<String, Object> values) {
        String typeName = (String) values.get("TYPE_NAME");
        Integer type = (Integer) values.get("DATA_TYPE");
        if ("YEAR".equals(typeName)) {
            // it is safe to map a YEAR to INTEGER
            return Types.INTEGER;
        } else if (typeName != null && typeName.endsWith("TEXT")) {
            String catalog = (String) values.get("TABLE_CAT");
            String tableName = (String) values.get("TABLE_NAME");
            String columnName = (String) values.get("COLUMN_NAME");
            String collation = platform.getSqlTemplate().queryForString("select collation_name from information_schema.columns " +
                    "where table_schema = ? and table_name = ? and column_name = ?",
                    catalog, tableName, columnName);
            String convertTextToLobParm = System.getProperty("mysqlddlreader.converttexttolob",
                    "true");
            boolean convertTextToLob = collation != null && collation.endsWith("_bin") &&
                    convertTextToLobParm.equalsIgnoreCase("true");
            if ("LONGTEXT".equals(typeName)) {
                return convertTextToLob ? Types.BLOB : Types.CLOB;
            } else if ("MEDIUMTEXT".equals(typeName)) {
                return convertTextToLob ? Types.BLOB : Types.LONGVARCHAR;
            } else if ("TEXT".equals(typeName)) {
                return convertTextToLob ? Types.BLOB : Types.LONGVARCHAR;
            } else if ("TINYTEXT".equals(typeName)) {
                return convertTextToLob ? Types.BLOB : Types.LONGVARCHAR;
            }
            return super.mapUnknownJdbcTypeForColumn(values);
        } else if (type != null && type == Types.OTHER && "UUID".equalsIgnoreCase("UUID")) {
            return Types.VARCHAR;
        } else if (type != null && type == Types.OTHER) {
            return Types.LONGVARCHAR;
        } else {
            return super.mapUnknownJdbcTypeForColumn(values);
        }
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        Column column = super.readColumn(metaData, values);
        if (column.getMappedTypeCode() == Types.TIMESTAMP) {
            adjustColumnSize(column, -20);
        } else if (column.getMappedTypeCode() == Types.TIME) {
            adjustColumnSize(column, -9);
        } else if (column.getMappedTypeCode() == Types.DATE) {
            removeColumnSize(column);
            if ("0000-00-00".equals(column.getDefaultValue())) {
                column.setDefaultValue(null);
                column.getPlatformColumns().get(platform.getName()).setDefaultValue("0000-00-00");
            }
        }
        // MySQL converts illegal date/time/timestamp values to
        // "0000-00-00 00:00:00", but this
        // is an illegal ISO value, so we replace it with NULL
        if ((column.getMappedTypeCode() == Types.TIMESTAMP)
                && "0000-00-00 00:00:00".equals(column.getDefaultValue())) {
            column.setDefaultValue(null);
        }
        // make sure the defaultvalue is null when an empty is returned.
        if ("".equals(column.getDefaultValue())) {
            column.setDefaultValue(null);
        }
        if (column.getJdbcTypeName().equalsIgnoreCase(TypeMap.POINT) ||
                column.getJdbcTypeName().equalsIgnoreCase(TypeMap.LINESTRING) ||
                column.getJdbcTypeName().equalsIgnoreCase(TypeMap.POLYGON)) {
            column.setJdbcTypeName(TypeMap.GEOMETRY);
        }
        if ("UUID".equalsIgnoreCase(column.getJdbcTypeName())) {
            column.setSize("36");
            PlatformColumn platformColumn = column.getPlatformColumns().get(platform.getName());
            if (platformColumn != null) {
                if ("UUID".equalsIgnoreCase(platformColumn.getType())) {
                    platformColumn.setSize(-1);
                }
            }
        }
        return column;
    }

    @Override
    protected void genericizeDefaultValuesAndUpdatePlatformColumn(Column column) {
        PlatformColumn platformColumn = column.findPlatformColumn(platform.getName());
        if (!"0000-00-00".equals(platformColumn.getDefaultValue())) {
            platformColumn.setDefaultValue(column.getDefaultValue());
        }
        /*
         * Translate from platform specific functions to ansi sql functions
         */
        if ("getdate()".equalsIgnoreCase(column.getDefaultValue())) {
            column.setDefaultValue("CURRENT_TIMESTAMP");
        }
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) {
        // MySql defines a unique index "PRIMARY" for primary keys
        return "PRIMARY".equals(index.getName());
    }

    @Override
    protected boolean isInternalForeignKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, ForeignKey fk, IIndex index) {
        // MySql defines a non-unique index of the same name as the fk
        return getPlatform().getDdlBuilder().getForeignKeyName(table, fk).equals(index.getName());
    }

    protected boolean isMariaDbDriver() {
        if (mariaDbDriver == null) {
            mariaDbDriver = "mariadb-jdbc".equals(getPlatform().getSqlTemplate().getDriverName());
        }
        return mariaDbDriver;
    }

    @Override
    protected Collection<ForeignKey> readForeignKeys(Connection connection,
            DatabaseMetaDataWrapper metaData, String tableName) throws SQLException {
        if (!isMariaDbDriver()) {
            return super.readForeignKeys(connection, metaData, tableName);
        } else {
            Map<String, ForeignKey> fks = new LinkedHashMap<String, ForeignKey>();
            ResultSet fkData = null;
            try {
                fkData = metaData.getForeignKeys(tableName);
                while (fkData.next()) {
                    int count = fkData.getMetaData().getColumnCount();
                    Map<String, Object> values = new HashMap<String, Object>();
                    for (int i = 1; i <= count; i++) {
                        values.put(fkData.getMetaData().getColumnName(i), fkData.getObject(i));
                    }
                    String fkName = (String) values.get("CONSTRAINT_NAME");
                    ForeignKey fk = (ForeignKey) fks.get(fkName);
                    if (fk == null) {
                        fk = new ForeignKey(fkName);
                        fk.setForeignTableName((String) values.get("REFERENCED_TABLE_NAME"));
                        fks.put(fkName, fk);
                    }
                    Reference ref = new Reference();
                    ref.setForeignColumnName((String) values.get("REFERENCED_COLUMN_NAME"));
                    ref.setLocalColumnName((String) values.get("COLUMN_NAME"));
                    Object pos = values.get("POSITION_IN_UNIQUE_CONSTRAINT");
                    if (pos instanceof Number) {
                        ref.setSequenceValue(((Number) pos).intValue());
                    }
                    fk.addReference(ref);
                }
            } finally {
                close(fkData);
            }
            return fks.values();
        }
    }

    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) {
        List<Trigger> triggers = new ArrayList<Trigger>();
        log.debug("Reading triggers for: " + tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform
                .getSqlTemplate();
        String sql = "SELECT "
                + "TRIGGER_NAME, "
                + "TRIGGER_SCHEMA, "
                + "TRIGGER_CATALOG, "
                + "EVENT_MANIPULATION AS TRIGGER_TYPE, "
                + "EVENT_OBJECT_TABLE AS TABLE_NAME, "
                + "EVENT_OBJECT_SCHEMA AS TABLE_SCHEMA, "
                + "EVENT_OBJECT_CATALOG AS TABLE_CATALOG, "
                + "TRIG.* "
                + "FROM INFORMATION_SCHEMA.TRIGGERS AS TRIG "
                + "WHERE EVENT_OBJECT_TABLE=? and EVENT_OBJECT_SCHEMA=? ;";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("TRIGGER_NAME"));
                trigger.setCatalogName(row.getString("TRIGGER_CATALOG"));
                trigger.setSchemaName(row.getString("TRIGGER_SCHEMA"));
                trigger.setTableName(row.getString("TABLE_NAME"));
                trigger.setEnabled(true);
                String triggerType = row.getString("TRIGGER_TYPE");
                if (triggerType.equals("DELETE")
                        || triggerType.equals("INSERT")
                        || triggerType.equals("UPDATE")) {
                    trigger.setTriggerType(TriggerType.valueOf(triggerType));
                }
                trigger.setMetaData(row);
                return trigger;
            }
        }, tableName, catalog);
        for (final Trigger trigger : triggers) {
            String name = trigger.getName();
            String sourceSql = "SHOW CREATE TRIGGER `" + catalog + "`." + name;
            sqlTemplate.query(sourceSql, new ISqlRowMapper<Trigger>() {
                public Trigger mapRow(Row row) {
                    trigger.setSource(row.getString("SQL Original Statement"));
                    return trigger;
                }
            });
        }
        return triggers;
    }
}
