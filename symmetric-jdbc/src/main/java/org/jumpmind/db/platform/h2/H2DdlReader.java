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
package org.jumpmind.db.platform.h2;

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
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import org.jumpmind.db.platform.MetaDataColumnDescriptor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;

/*
 * Reads a database model from a H2 database. From patch <a
 * href="https://issues.apache.org/jira/browse/DDLUTILS-185"
 * >https://issues.apache.org/jira/browse/DDLUTILS-185</a>
 */
public class H2DdlReader extends AbstractJdbcDdlReader {
    private boolean isVersion2;

    public H2DdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        isVersion2 = platform.getSqlTemplate().getDatabaseMajorVersion() == 2;
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        Relation relation = super.readRelation(connection, metaData, values);
        if (relation != null && relation.getColumnsAsList().stream().anyMatch(col -> col.getDefaultValue() != null
                && col.getDefaultValue().contains("(") && col.getDefaultValue().contains(")"))) {
            determineGeneratedColumns(relation, relation.getColumns());
        }
        return relation;
    }

    protected void determineGeneratedColumns(Relation relation, final Column[] columnsToCheck) {
        StringBuilder query = new StringBuilder();
        if (columnsToCheck == null || columnsToCheck.length == 0) {
            return;
        }
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplateDirty();
        String isGeneratedColumnName = "is_computed";
        if (isVersion2) {
            isGeneratedColumnName = "is_generated";
        }
        query.append("SELECT column_name, " + isGeneratedColumnName + " FROM information_schema.columns WHERE ");
        List<String> l = new ArrayList<>();
        if (relation.getCatalog() != null) {
            query.append("table_catalog = ? AND ");
            l.add(relation.getCatalog());
        }
        if (relation.getSchema() != null) {
            query.append("table_schema = ? AND ");
            l.add(relation.getSchema());
        }
        query.append("table_name = ?");
        l.add(relation.getName());
        List<Row> result = sqlTemplate.query(query.toString(), l.toArray());
        for (Column column : columnsToCheck) {
            updateColumnGenerationStatus(column, result, isVersion2);
        }
    }

    private static void updateColumnGenerationStatus(Column column, List<Row> generationInfo, boolean isVersion2) {
        for (Row row : generationInfo) {
            if (column.getName().equalsIgnoreCase(row.getString("column_name"))) {
                column.setGenerated(isVersion2 ? "ALWAYS".equals(row.getString("is_generated")) : row.getBoolean("is_computed"));
                break;
            }
        }
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData,
            Map<String, Object> values) throws SQLException {
        Column column = super.readColumn(metaData, values);
        if (values.get("CHARACTER_MAXIMUM_LENGTH") != null) {
            String maxLength = (String) values.get("CHARACTER_MAXIMUM_LENGTH");
            if (isNotBlank(maxLength)) {
                Integer size = Integer.valueOf(maxLength);
                if (!isVersion2 && size.intValue() == Integer.MAX_VALUE && column.getMappedTypeCode() == Types.VARCHAR) {
                    column.setMappedTypeCode(Types.LONGVARCHAR);
                    column.setMappedType("LONGVARCHAR");
                    column.findPlatformColumn(platform.getName()).setType("LONGVARCHAR");
                }
                column.setSize(size.toString());
                column.findPlatformColumn(platform.getName()).setSize(size);
            }
        }
        if (values.get("COLUMN_SIZE") != null) {
            String maxLength = (String) values.get("COLUMN_SIZE");
            if (isNotBlank(maxLength)) {
                Integer size = Integer.valueOf(maxLength);
                if (isVersion2 && size.intValue() == 1000000000) {
                    if (column.getMappedTypeCode() == Types.VARCHAR || column.getMappedTypeCode() == Types.VARBINARY) {
                        if (column.getMappedTypeCode() == Types.VARCHAR) {
                            column.setMappedTypeCode(Types.LONGVARCHAR);
                            column.setMappedType("LONGVARCHAR");
                            column.findPlatformColumn(platform.getName()).setType("LONGVARCHAR");
                        } else {
                            column.setMappedTypeCode(Types.LONGVARBINARY);
                            column.setMappedType("LONGVARBINARY");
                            column.findPlatformColumn(platform.getName()).setType("LONGVARBINARY");
                        }
                        column.setSize(size.toString());
                        column.findPlatformColumn(platform.getName()).setSize(size);
                    }
                }
            }
        }
        if (values.get("COLUMN_DEFAULT") != null) {
            column.setDefaultValue(values.get("COLUMN_DEFAULT").toString());
        }
        if (values.get("NUMERIC_SCALE") != null && values.get("DECIMAL_DIGITS") != null && ((Integer) values.get("DECIMAL_DIGITS")) == 0) {
            int scale = (Integer) values.get("NUMERIC_SCALE");
            column.setScale(scale);
            column.findPlatformColumn(platform.getName()).setDecimalDigits(
                    scale);
        }
        if (column.getMappedTypeCode() == Types.TIMESTAMP) {
            resetColumnSize(column, column.getScale() > 9 ? "9" : String.valueOf(column.getScale()));
        }
        if (column.getMappedTypeCode() == Types.TIME) {
            resetColumnSize(column, "0");
        }
        if (column.getMappedTypeCode() == Types.DATE) {
            removeColumnSize(column);
        }
        String defaultValue = column.getDefaultValue();
        if (TypeMap.isTextType(column.getMappedTypeCode())
                && (defaultValue != null)) {
            column.setDefaultValue(unescape(defaultValue, "'", "''"));
        }
        String autoIncrement = (String) values.get("IS_AUTOINCREMENT");
        if ((autoIncrement != null && "YES".equalsIgnoreCase(autoIncrement.trim()))
                || (isVersion2 && defaultValue != null && defaultValue.toUpperCase().startsWith("NEXTVAL("))) {
            column.setAutoIncrement(true);
            column.setDefaultValue(null);
        }
        return column;
    }

    @Override
    protected String getResultSetSchemaName() {
        if (isVersion2) {
            return "TABLE_SCHEM";
        }
        return "TABLE_SCHEMA";
    }

    @Override
    protected String getResultSetCatalogName() {
        if (isVersion2) {
            return "TABLE_CAT";
        }
        return "TABLE_CATALOG";
    }

    @Override
    protected List<MetaDataColumnDescriptor> initColumnsForColumn() {
        List<MetaDataColumnDescriptor> result = super.initColumnsForColumn();
        result.add(new MetaDataColumnDescriptor("COLUMN_DEFAULT", 12));
        result.add(new MetaDataColumnDescriptor("NUMERIC_SCALE", 4,
                Integer.valueOf(0)));
        result.add(new MetaDataColumnDescriptor("CHARACTER_MAXIMUM_LENGTH", 12));
        return result;
    }

    @Override
    protected boolean isInternalForeignKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, ForeignKey fk,
            IIndex index) {
        String name = index.getName();
        return name != null
                && (name.startsWith(fk.getName()) || name
                        .startsWith("CONSTRAINT_INDEX_"));
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) {
        String name = index.getName();
        return name != null && name.startsWith("PRIMARY_KEY_");
    }

    @Override
    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) throws SqlException {
        String tableNameColumnName;
        String triggerTypeColumnName;
        if (isVersion2) {
            tableNameColumnName = "EVENT_OBJECT_TABLE";
            triggerTypeColumnName = "EVENT_MANIPULATION";
        } else {
            tableNameColumnName = "TABLE_NAME";
            triggerTypeColumnName = "TRIGGER_TYPE";
        }
        log.debug("Reading triggers for: {}", tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform
                .getSqlTemplate();
        String sql = "SELECT * FROM INFORMATION_SCHEMA.TRIGGERS "
                + "WHERE " + tableNameColumnName + "=? and TRIGGER_SCHEMA=? and TRIGGER_CATALOG=? ;";
        List<Trigger> triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("TRIGGER_NAME"));
                trigger.setCatalogName(row.getString("TRIGGER_CATALOG"));
                trigger.setSchemaName(row.getString("TRIGGER_SCHEMA"));
                trigger.setTableName(row.getString(tableNameColumnName));
                trigger.setEnabled(true);
                if (!isVersion2) {
                    trigger.setSource(row.getString("SQL"));
                    row.remove("SQL");
                }
                String triggerType = row.getString(triggerTypeColumnName);
                if (triggerType.equals("DELETE")
                        || triggerType.equals("INSERT")
                        || triggerType.equals("UPDATE")) {
                    trigger.setTriggerType(TriggerType.valueOf(triggerType));
                }
                trigger.setMetaData(row);
                return trigger;
            }
        }, tableName, schema, catalog);
        return triggers;
    }
}
