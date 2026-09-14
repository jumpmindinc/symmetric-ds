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
package org.jumpmind.db.platform.redshift;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;

public class RedshiftDdlReader extends AbstractJdbcDdlReader {
    protected static Map<String, String> columnNames;
    static {
        columnNames = mapNames();
    }

    public RedshiftDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern(null);
    }

    protected static Map<String, String> mapNames() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("IS_NULLABLE", "NULLABLE");
        return values;
    }

    @Override
    protected String getName(String defaultName) {
        String name = columnNames.get(defaultName);
        if (name == null) {
            name = super.getName(defaultName);
        }
        return name;
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        Relation relation = super.readRelation(connection, metaData, values);
        if (relation instanceof Table table) {
            determineAutoIncrementFromResultSetMetaData(connection, table, table.getColumns());
        }
        return relation;
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values) throws SQLException {
        Column column = super.readColumn(metaData, values);
        if (column.getJdbcTypeCode() == Types.VARCHAR && column.getSizeAsInt() == 65535) {
            column.setJdbcTypeCode(Types.LONGVARCHAR);
            column.setMappedTypeCode(Types.LONGVARCHAR);
            column.setSize(null);
        }
        String defaultValue = column.getDefaultValue();
        if ((defaultValue != null) && (defaultValue.length() > 0)) {
            // If the default value looks like "identity"(102643, 0, '1,1'::text)
            // then it is an auto-increment column
            if (defaultValue.startsWith("\"identity\"")) {
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
     * Extracts the default value from a default value spec of the form "'some value'::character varying" or "'2000-01-01'::date".
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
     * Extracts the default value from a default value spec of the form "-9000000000000000000::bigint".
     * 
     * Handles sequence evaluations where the end paren is after the :: nextval('"sym_data_data_id_seq"'::text)
     * 
     * @param defaultValue The default value spec
     * 
     * @return The default value
     */
    private String extractUndelimitedDefaultValue(String defaultValue) {
        int valueEnd = defaultValue.indexOf("::");
        if (valueEnd > 0) {
            defaultValue = defaultValue.substring(0, valueEnd);
            int startParen = defaultValue.indexOf("(");
            int endParen = defaultValue.indexOf(")");
            if (startParen > 0 && endParen < 0) {
                defaultValue = defaultValue + ")";
            }
        } else {
            if (defaultValue.startsWith("(") && defaultValue.endsWith(")")) {
                defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
            }
        }
        return defaultValue;
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) {
        return table.doesIndexContainOnlyPrimaryKeyColumns(index);
    }

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
                + "pgproc.prosrc "
                + "FROM INFORMATION_SCHEMA.TRIGGERS AS trig "
                + "INNER JOIN pg_catalog.pg_trigger AS pgtrig "
                + "ON pgtrig.tgname=trig.trigger_name "
                + "INNER JOIN pg_catalog.pg_proc AS pgproc "
                + "ON pgproc.oid=pgtrig.tgfoid "
                + "WHERE event_object_table=? AND event_object_schema=?;";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
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
    protected void readForeignKeyUpdateRule(Map<String, Object> values, ForeignKey fk) {
        // Redshift does not support cascade actions
        fk.setOnUpdateAction(ForeignKeyAction.NOACTION);
    }

    @Override
    protected void readForeignKeyDeleteRule(Map<String, Object> values, ForeignKey fk) {
        // Redshift does not support cascade actions
        fk.setOnDeleteAction(ForeignKeyAction.NOACTION);
    }
}
