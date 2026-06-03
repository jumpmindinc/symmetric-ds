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
package org.jumpmind.db.platform.nuodb;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.ForeignKey.ForeignKeyAction;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.Trigger.TriggerType;
import org.jumpmind.db.platform.AbstractJdbcDdlReader;
import org.jumpmind.db.platform.DatabaseMetaDataWrapper;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.Row;

/*
 * Reads a database model from a MySql database.
 */
public class NuoDbDdlReader extends AbstractJdbcDdlReader {
    protected static Map<String, String> columnNames;
    static {
        columnNames = mapNames();
    }

    public NuoDbDdlReader(IDatabasePlatform platform) {
        super(platform);
        setDefaultCatalogPattern(null);
        setDefaultSchemaPattern(null);
        setDefaultTablePattern(null);
    }

    protected static Map<String, String> mapNames() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("TABLE_NAME", "TABLENAME");
        values.put("TABLE_TYPE", "TYPE");
        values.put("TABLE_CAT", "TABLE_CAT");
        values.put("TABLE_SCHEM", "SCHEMA");
        values.put("REMARKS", "REMARKS");
        values.put("COLUMN_DEF", "COLUMN_DEF");
        values.put("COLUMN_DEFAULT", "DEFAULTVALUE");
        values.put("COLUMN_NAME", "FIELD");
        values.put("TYPE_NAME", "TYPE_NAME");
        values.put("DATA_TYPE", "DATA_TYPE");
        values.put("NUM_PREC_RADIX", "NUM_PREC_RADIX");
        values.put("DECIMAL_DIGITS", "SCALE");
        values.put("COLUMN_SIZE", "LENGTH");
        values.put("IS_NULLABLE", "IS_NULLABLE");
        values.put("IS_AUTOINCREMENT", "IS_AUTOINCREMENT");
        values.put("PK_NAME", "PK_NAME");
        values.put("PKTABLE_NAME", "PKTABLE_NAME");
        values.put("FKTABLE_NAME", "FKTABLE_NAME");
        values.put("KEY_SEQ", "KEY_SEQ");
        values.put("FK_NAME", "FOREIGNKEYNAME");
        values.put("PKCOLUMN_NAME", "PKCOLUMN_NAME");
        values.put("FKCOLUMN_NAME", "FKCOLUMN_NAME");
        values.put("UPDATE_RULE", "UPDATE_RULE");
        values.put("DELETE_RULE", "DELETE_RULE");
        values.put("INDEX_NAME", "INDEXNAME");
        values.put("NON_UNIQUE", "NON_UNIQUE");
        values.put("ORDINAL_POSITION", "POSITION");
        values.put("TYPE", "INDEXTYPE");
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
    protected String getResultSetSchemaName() {
        return super.getResultSetSchemaName();
    }

    @Override
    protected Relation readRelation(Connection connection, DatabaseMetaDataWrapper metaData, Map<String, Object> values) throws SQLException {
        Relation relation = super.readRelation(connection, metaData, values);
        if (relation != null) {
            if (relation instanceof Table table) {
                determineAutoIncrementFromResultSetMetaData(connection, table, relation.getColumns());
            }
            relation.setCatalog(null);
        }
        return relation;
    }

    @Override
    protected Column readColumn(DatabaseMetaDataWrapper metaData, Map<String, Object> values)
            throws SQLException {
        Column column = super.readColumn(metaData, values);
        // make sure the defaultvalue is null when an empty is returned.
        if ("".equals(column.getDefaultValue())) {
            column.setDefaultValue(null);
        }
        if (column.getJdbcTypeName().equalsIgnoreCase("enum")) {
            column.setMappedTypeCode(Types.VARCHAR);
            column.setMappedType(JDBCType.VARCHAR.name());
            ISqlTemplate template = platform.getSqlTemplate();
            String unParsedEnums = template.queryForString("SELECT SUBSTRING(ENUMERATION, 2, LENGTH(ENUMERATION)-2) FROM SYSTEM.FIELDS"
                    + " WHERE SCHEMA=? AND TABLENAME=? AND FIELD=?", (String) values.get("SCHEMA"), (String) values.get("TABLENAME"), column.getName());
            if (unParsedEnums != null) {
                String[] parsedEnums = unParsedEnums.split("\\^");
                column.getPlatformColumns().get(platform.getName()).setEnumValues(parsedEnums);
            }
        }
        return column;
    }

    @Override
    protected boolean isInternalPrimaryKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, IIndex index) {
        return ((table.getName()).toUpperCase() + "..PRIMARY_KEY").equals(index.getName());
    }

    @Override
    protected boolean isInternalForeignKeyIndex(Connection connection,
            DatabaseMetaDataWrapper metaData, Table table, ForeignKey fk, IIndex index) {
        return getPlatform().getDdlBuilder().getForeignKeyName(table, fk).equals(index.getName());
    }

    @Override
    protected Collection<ForeignKey> readForeignKeys(Connection connection,
            DatabaseMetaDataWrapper metaData, String tableName) throws SQLException {
        Map<String, ForeignKey> fks = new LinkedHashMap<String, ForeignKey>();
        ResultSet fkData = null;
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT f2.FIELD AS REFERENCED_COLUMN_NAME, t2.TABLENAME AS REFERENCED_TABLE_NAME, f.FIELD AS COLUMN_NAME, \"POSITION\", FOREIGNKEYNAME AS CONSTRAINT_NAME "
                            +
                            "FROM \"SYSTEM\".FOREIGNKEYS AS fk " +
                            "INNER JOIN SYSTEM.TABLES AS t ON t.TABLEID = fk.FOREIGNTABLEID " +
                            "INNER JOIN SYSTEM.TABLES AS t2 ON t2.TABLEID = fk.PRIMARYTABLEID " +
                            "INNER JOIN SYSTEM.FIELDS AS f ON f.FIELDID = fk.FOREIGNFIELDID AND f.TABLENAME = t.TABLENAME AND f.\"SCHEMA\"= t.\"SCHEMA\" " +
                            "INNER JOIN SYSTEM.FIELDS AS f2 ON f2.FIELDID = fk.PRIMARYFIELDID AND f2.TABLENAME = t2.TABLENAME AND f2.\"SCHEMA\" = t2.\"SCHEMA\" "
                            +
                            "WHERE t.TABLENAME = ?");
            ps.setString(1, tableName);
            fkData = ps.executeQuery();
            while (fkData.next()) {
                String fkName = fkData.getString(5);
                ForeignKey fk = (ForeignKey) fks.get(fkName);
                if (fk == null) {
                    fk = new ForeignKey(fkName);
                    fk.setForeignTableName(fkData.getString(2));
                    fks.put(fkName, fk);
                }
                Reference ref = new Reference();
                ref.setForeignColumnName(fkData.getString(1));
                ref.setLocalColumnName(fkData.getString(3));
                ref.setSequenceValue(fkData.getInt(4));
                fk.addReference(ref);
            }
        } finally {
            close(fkData);
        }
        return fks.values();
    }

    public List<Trigger> getTriggers(final String catalog, final String schema,
            final String tableName) {
        List<Trigger> triggers = new ArrayList<Trigger>();
        log.debug("Reading triggers for: " + tableName);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) platform
                .getSqlTemplate();
        String sql = "SELECT "
                + "TRIGGERNAME, "
                + "SCHEMA, "
                + "TRIGGER_TYPE, "
                + "TABLENAME, "
                + "TRIG.* "
                + "FROM SYSTEM.TRIGGERS AS TRIG "
                + "WHERE TABLENAME=? and SCHEMA=? ;";
        triggers = sqlTemplate.query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("TRIGGERNAME"));
                trigger.setSchemaName(row.getString("SCHEMA"));
                trigger.setTableName(row.getString("TABLENAME"));
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
        return triggers;
    }

    @Override
    protected Integer mapUnknownJdbcTypeForColumn(Map<String, Object> values) {
        Integer type = (Integer) values.get("DATA_TYPE");
        if (type != null && type.intValue() == Types.CLOB) {
            // XML longvarchar becoms longvarchar on Column but becomes clob in database
            return Types.LONGVARCHAR;
        } else {
            return super.mapUnknownJdbcTypeForColumn(values);
        }
    }

    @Override
    protected void readForeignKeyUpdateRule(Map<String, Object> values, ForeignKey fk) {
        // NuoDb does not support cascade actions
        fk.setOnUpdateAction(ForeignKeyAction.NOACTION);
    }

    @Override
    protected void readForeignKeyDeleteRule(Map<String, Object> values, ForeignKey fk) {
        // NuoDb does not support cascade actions
        fk.setOnDeleteAction(ForeignKeyAction.NOACTION);
    }
}
