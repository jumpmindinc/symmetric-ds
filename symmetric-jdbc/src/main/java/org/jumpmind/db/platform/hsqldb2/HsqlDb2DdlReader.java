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
package org.jumpmind.db.platform.hsqldb2;

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
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
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
 * Reads a database model from a HsqlDb database.
 */
public class HsqlDb2DdlReader extends AbstractJdbcDdlReader {
    public HsqlDb2DdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        Column column = super.readColumn(metaData, values);
        if (column.isGenerated() && column.getDefaultValue() == null) {
            JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform.getSqlTemplateDirty();
            String sql = "SELECT generation_expression\n"
                    + "FROM information_schema.columns\n"
                    + "WHERE table_name = ?\n"
                    + "AND column_name = ?\n";
            List<String> l = new ArrayList<String>();
            String schemaName = (String) values.get("TABLE_SCHEM");
            String tableName = (String) values.get("TABLE_NAME");
            l.add(tableName);
            l.add(column.getName());
            String definition = sqlTemplate.queryForString(sql, l.toArray());
            definition = definition.replace(schemaName + "." + tableName + ".", "");
            column.setDefaultValue(definition);
        }
        if (TypeMap.isTextType(column.getMappedTypeCode()) && (column.getDefaultValue() != null)) {
            column.setDefaultValue(unescape(column.getDefaultValue(), "'", "''"));
        }
        String autoIncrement = (String) values.get("IS_AUTOINCREMENT");
        if (autoIncrement != null) {
            column.setAutoIncrement("YES".equalsIgnoreCase(autoIncrement.trim()));
        }
        if (column.getMappedTypeCode() == Types.TIMESTAMP) {
            adjustColumnSize(column, -20, 3);
        }
        if (column.getMappedTypeCode() == Types.TIME) {
            adjustColumnSize(column, -9, 3);
        }
        if (column.getMappedTypeCode() == Types.DATE) {
            removeColumnSize(column);
        }
        return column;
    }

    @Override
    protected boolean isInternalForeignKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, ForeignKey fk, IIndex index) {
        String name = index.getName();
        return (name != null) && name.startsWith("SYS_IDX_");
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) {
        String name = index.getName();
        return (name != null) && (name.startsWith("SYS_PK_") || name.startsWith("SYS_IDX_"));
    }

    @Override
    protected Integer mapUnknownJdbcTypeForColumn(Map<String, Object> values) {
        String typeName = (String) values.get("TYPE_NAME");
        String size = (String) values.get("COLUMN_SIZE");
        if (typeName != null && typeName.startsWith("VARCHAR") && StringUtils.isNotBlank(size)) {
            long sizeAsNumber = Long.parseLong(size);
            if (sizeAsNumber >= 16777216) {
                return Types.LONGVARCHAR;
            } else {
                return super.mapUnknownJdbcTypeForColumn(values);
            }
        } else {
            return super.mapUnknownJdbcTypeForColumn(values);
        }
    }

    @Override
    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) throws SqlException {
        List<Trigger> triggers = new ArrayList<Trigger>();
        log.debug("Reading triggers for: " + tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform
                .getSqlTemplate();
        String sql = "SELECT * FROM INFORMATION_SCHEMA.TRIGGERS "
                + "WHERE TABLE_NAME=? and TRIGGER_SCHEMA=? and TRIGGER_CATALOG=? ;";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("TRIGGER_NAME"));
                trigger.setCatalogName(row.getString("TRIGGER_CATALOG"));
                trigger.setSchemaName(row.getString("TRIGGER_SCHEMA"));
                trigger.setTableName(row.getString("TABLE_NAME"));
                trigger.setEnabled(true);
                trigger.setSource(row.getString("SQL"));
                row.remove("SQL");
                String triggerType = row.getString("TRIGGER_TYPE");
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
