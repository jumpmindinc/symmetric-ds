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
package org.jumpmind.db.platform.firebird;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.ListOrderedMap;
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
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;

/*
 * The Jdbc Model Reader for Firebird.
 */
public class FirebirdDdlReader extends AbstractJdbcDdlReader {
    private static final String JAYBIRD_UNDERSCORE_ESCAPE = "\\\\_";
    protected boolean isLegacyJaybird;

    public FirebirdDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern("%");
        String classpath = System.getProperty("java.class.path");
        isLegacyJaybird = classpath.contains("jaybird-2.1");
        if (isLegacyJaybird) {
            log.info("Detected older Jaybird driver");
        }
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        Relation relation = super.readRelation(connection, metaData, values);
        if (relation instanceof Table table) {
            determineAutoIncrementColumns(connection, table);
            setPrimaryKeyConstraintName(connection, table);
        }
        return relation;
    }

    protected void setPrimaryKeyConstraintName(Connection connection, Table table) throws SQLException {
        String sql = "select RDB$CONSTRAINT_NAME from RDB$RELATION_CONSTRAINTS where RDB$RELATION_NAME=? and RDB$CONSTRAINT_TYPE='PRIMARY KEY'";
        PreparedStatement pstmt = null;
        try {
            pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, table.getName());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                table.setPrimaryKeyConstraintName(rs.getString(1).trim());
            }
            rs.close();
        } finally {
            JdbcSqlTemplate.close(pstmt);
        }
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values) throws SQLException {
        Column column = super.readColumn(metaData, values);
        if (column.isGenerated() && column.getDefaultValue() == null) {
            JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplateDirty();
            String sql = "SELECT rdb$computed_source\n"
                    + "FROM rdb$fields f JOIN rdb$relation_fields rf\n"
                    + "ON f.rdb$field_name = rf.rdb$field_source\n"
                    + "WHERE rf.rdb$relation_name = ?\n"
                    + "AND rf.rdb$field_name = ?";
            List<String> l = new ArrayList<String>();
            l.add((String) values.get("TABLE_NAME"));
            l.add(column.getName());
            String definition = sqlTemplate.queryForString(sql, l.toArray());
            column.setDefaultValue(definition);
        }
        if (column.getMappedTypeCode() == Types.FLOAT) {
            column.setMappedTypeCode(Types.REAL);
        } else if (TypeMap.isTextType(column.getMappedTypeCode())) {
            column.setDefaultValue(unescape(column.getDefaultValue(), "'", "''"));
        }
        if (column.getJdbcTypeCode() == Types.TIMESTAMP) {
            resetColumnSize(column, "3");
            removePlatformColumnSize(column);
        } else if (column.getJdbcTypeCode() == Types.TIME) {
            resetColumnSize(column, "0");
            removePlatformColumnSize(column);
        } else if (column.getJdbcTypeCode() == Types.DATE) {
            removeColumnSize(column);
        }
        return column;
    }

    /*
     * Helper method that determines the auto increment status using Firebird's system tables.
     * 
     * @param table The table
     */
    protected void determineAutoIncrementColumns(Connection connection, Table table)
            throws SQLException {
        /*
         * Since for long table and column names, the trigger name will be shortened we have to determine for each column whether there is a trigger on it
         */
        Column[] columns = table.getColumns();
        HashMap<String, Column> names = new HashMap<String, Column>();
        String name;
        for (int idx = 0; idx < columns.length; idx++) {
            name = ((FirebirdDdlBuilder) getPlatform().getDdlBuilder()).getTriggerName(table,
                    columns[idx]);
            if (!getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn()) {
                name = name.toUpperCase();
            }
            names.put(name, columns[idx]);
        }
        PreparedStatement stmt = connection.prepareStatement("SELECT RDB$TRIGGER_NAME FROM RDB$TRIGGERS WHERE RDB$RELATION_NAME=?");
        stmt.setString(1, table.getName());
        ResultSet rs = null;
        try {
            rs = stmt.executeQuery();
            while (rs.next()) {
                String triggerName = rs.getString(1).trim();
                Column column = (Column) names.get(triggerName);
                if (column != null) {
                    column.setAutoIncrement(true);
                }
            }
        } finally {
            if (rs != null) {
                rs.close();
            }
            stmt.close();
        }
    }

    @Override
    protected Collection<IIndex> readIndices(Connection connection, DatabaseMetaDataWrapper metaData,
            String tableName) throws SQLException {
        // Jaybird is not able to read indices when delimited identifiers are
        // turned on, so we gather the data manually using Firebird's system tables
        Map<String, IIndex> indices = new ListOrderedMap<String, IIndex>();
        StringBuilder query = new StringBuilder();
        query.append("SELECT a.RDB$INDEX_NAME INDEX_NAME, b.RDB$RELATION_NAME TABLE_NAME, b.RDB$UNIQUE_FLAG NON_UNIQUE,");
        query.append(" a.RDB$FIELD_POSITION ORDINAL_POSITION, a.RDB$FIELD_NAME COLUMN_NAME, 3 INDEX_TYPE");
        query.append(" FROM RDB$INDEX_SEGMENTS a, RDB$INDICES b WHERE a.RDB$INDEX_NAME=b.RDB$INDEX_NAME AND b.RDB$RELATION_NAME = ?");
        PreparedStatement stmt = connection.prepareStatement(query.toString());
        ResultSet indexData = null;
        stmt.setString(1,
                getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn() ? tableName : tableName.toUpperCase());
        try {
            indexData = stmt.executeQuery();
            while (indexData.next()) {
                Map<String, Object> values = readMetaData(indexData, getColumnsForIndex());
                // we have to reverse the meaning of the unique flag
                values.put("NON_UNIQUE",
                        Boolean.FALSE.equals(values.get("NON_UNIQUE")) ? Boolean.TRUE
                                : Boolean.FALSE);
                // and trim the names
                values.put("INDEX_NAME", ((String) values.get("INDEX_NAME")).trim());
                values.put("TABLE_NAME", ((String) values.get("TABLE_NAME")).trim());
                values.put("COLUMN_NAME", ((String) values.get("COLUMN_NAME")).trim());
                readIndex(metaData, values, indices);
            }
        } finally {
            if (indexData != null) {
                indexData.close();
            }
        }
        return indices.values();
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) throws SQLException {
        IDdlBuilder builder = getPlatform().getDdlBuilder();
        String tableName = builder.getTableName(table.getName());
        String indexName = builder.getIndexName(index);
        StringBuilder query = new StringBuilder();
        query.append("SELECT RDB$CONSTRAINT_NAME FROM RDB$RELATION_CONSTRAINTS where RDB$RELATION_NAME=? AND RDB$CONSTRAINT_TYPE=? AND RDB$INDEX_NAME=?");
        PreparedStatement stmt = connection.prepareStatement(query.toString());
        try {
            stmt.setString(
                    1,
                    getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn() ? tableName
                            : tableName
                                    .toUpperCase());
            stmt.setString(2, "PRIMARY KEY");
            stmt.setString(3, indexName);
            ResultSet resultSet = stmt.executeQuery();
            return resultSet.next();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }

    @Override
    protected boolean isInternalForeignKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, ForeignKey fk, IIndex index)
            throws SQLException {
        IDdlBuilder builder = getPlatform().getDdlBuilder();
        String tableName = builder.getTableName(table.getName());
        String indexName = builder.getIndexName(index);
        String fkName = builder.getForeignKeyName(table, fk);
        StringBuilder query = new StringBuilder();
        query.append(
                "SELECT RDB$CONSTRAINT_NAME FROM RDB$RELATION_CONSTRAINTS where RDB$RELATION_NAME=? AND RDB$CONSTRAINT_TYPE=? AND RDB$CONSTRAINT_NAME=? AND RDB$INDEX_NAME=?");
        PreparedStatement stmt = connection.prepareStatement(query.toString());
        try {
            stmt.setString(
                    1,
                    getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn() ? tableName
                            : tableName
                                    .toUpperCase());
            stmt.setString(2, "FOREIGN KEY");
            stmt.setString(3, fkName);
            stmt.setString(4, indexName);
            ResultSet resultSet = stmt.executeQuery();
            return resultSet.next();
        } finally {
            if (stmt != null) {
                stmt.close();
            }
        }
    }

    @Override
    protected String getRelationNamePattern(String relationName) {
        /*
         * When looking up a table definition, Jaybird treats underscore (_) in the table name as a wildcard, so it needs to be escaped, or you'll get back
         * column names for more than one table. Example: DatabaseMetaData.metaData.getColumns(null, null, "SYM\\_NODE", null)
         */
        if (isLegacyJaybird) {
            return String.format("\"%s\"", relationName).replace("\\_", JAYBIRD_UNDERSCORE_ESCAPE);
        } else {
            return String.format("%s", relationName).replace("\\_", JAYBIRD_UNDERSCORE_ESCAPE);
        }
    }

    @Override
    protected String getTableNamePatternForConstraints(String tableName) {
        if (isLegacyJaybird) {
            return String.format("\"%s\"", tableName).replace("\\_", JAYBIRD_UNDERSCORE_ESCAPE);
        } else {
            return super.getTableNamePatternForConstraints(tableName);
        }
    }

    @Override
    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) throws SqlException {
        List<Trigger> triggers = new ArrayList<Trigger>();
        log.debug("Reading triggers for: " + tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform
                .getSqlTemplate();
        String sql = "select "
                + "TRIG.RDB$TRIGGER_NAME as TRIGGER_NAME, "
                + "TRIG.RDB$RELATION_NAME as TABLE_NAME, "
                + "TYPES1.RDB$TYPE_NAME as TRIGGER_TYPE, "
                + "TRIG.RDB$TRIGGER_SEQUENCE as TRIGGER_SEQUENCE, "
                + "TRIG.RDB$TRIGGER_BLR as TRIGGER_BLR, "
                + "TRIG.RDB$DESCRIPTION as DESCRIPTION, "
                + "TRIG.RDB$TRIGGER_INACTIVE as TRIGGER_INACTIVE, "
                + "TYPES2.RDB$TYPE_NAME as SYSTEM_FLAG, "
                + "TRIG.RDB$FLAGS as FLAGS, "
                + "TRIG.RDB$VALID_BLR as VALID_BLR, "
                + "TRIG.RDB$DEBUG_INFO as DEBUG_INFO,"
                + "TRIG.RDB$TRIGGER_SOURCE as TRIGGER_SOURCE "
                + "from RDB$TRIGGERS as TRIG "
                + "inner join RDB$TYPES as TYPES1 "
                + "on TYPES1.RDB$FIELD_NAME = 'RDB$TRIGGER_TYPE' "
                + "and TYPES1.RDB$TYPE = TRIG.RDB$TRIGGER_TYPE "
                + "inner join RDB$TYPES as TYPES2 "
                + "on TYPES2.RDB$FIELD_NAME = 'RDB$SYSTEM_FLAG' "
                + "and TYPES2.RDB$TYPE = TRIG.RDB$SYSTEM_FLAG "
                + "where RDB$RELATION_NAME = ? ;";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("TRIGGER_NAME"));
                trigger.setTableName(row.getString("TABLE_NAME"));
                trigger.setEnabled(true);
                trigger.setSource(row.getString("TRIGGER_SOURCE"));
                row.remove("TRIGGER_SOURCE");
                String triggerType = row.getString("TRIGGER_TYPE");
                if (triggerType.contains("STORE"))
                    trigger.setTriggerType(TriggerType.INSERT);
                else if (triggerType.contains("ERASE"))
                    trigger.setTriggerType(TriggerType.DELETE);
                else if (triggerType.contains("MODIFY"))
                    trigger.setTriggerType(TriggerType.UPDATE);
                trigger.setMetaData(row);
                return trigger;
            }
        }, tableName);
        return triggers;
    }
}
