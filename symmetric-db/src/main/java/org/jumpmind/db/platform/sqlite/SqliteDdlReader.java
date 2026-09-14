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
package org.jumpmind.db.platform.sqlite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.NonUniqueIndex;
import org.jumpmind.db.model.PlatformTrigger;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.model.TypeMap;
import org.jumpmind.db.model.UniqueIndex;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlConstants;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.mapper.RowMapper;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.db.util.TableRow;

public class SqliteDdlReader implements IDdlReader {
    final static ColumnMapper COLUMN_MAPPER = new ColumnMapper();
    final static IndexMapper INDEX_MAPPER = new IndexMapper();
    final static IndexColumnMapper INDEX_COLUMN_MAPPER = new IndexColumnMapper();
    protected IDatabasePlatform platform;

    public SqliteDdlReader(IDatabasePlatform platform) {
        this.platform = platform;
    }

    public List<String> getRelationNames(String catalog, String schema, String[] relationTypes) {
        return platform.getSqlTemplate().query("select tbl_name from sqlite_master where type='table'", SqlConstants.STRING_MAPPER);
    }

    public Database readRelations(String catalog, String schema, String[] relationTypes) {
        List<String> tableNames = getRelationNames(catalog, schema, relationTypes);
        Database database = new Database();
        for (String tableName : tableNames) {
            Table table = (Table) readRelation(catalog, schema, tableName);
            if (table != null) {
                database.addTable(table);
            }
        }
        return database;
    }

    protected void checkColumns(List<Column> columns, String relationName) {
        String ddl = platform.getSqlTemplate().queryForObject("select sql from sqlite_master where tbl_name=?", String.class, relationName);
        if (StringUtils.isBlank(ddl)) {
            return;
        }
        int openingParen = ddl.indexOf("(");
        if (openingParen != -1) {
            ddl = ddl.substring(openingParen + 1);
        }
        int closingParen = ddl.lastIndexOf(")");
        if (closingParen != -1) {
            ddl = ddl.substring(0, closingParen);
        }
        String[] commaSplit = ddl.split(",");
        for (String columnDdl : commaSplit) {
            for (Column col : columns) {
                if (columnDdl.contains(col.getName())) {
                    applyColumnFlagsFromDdl(col, columnDdl);
                }
            }
        }
    }

    private void applyColumnFlagsFromDdl(Column col, String columnDdl) {
        if (columnDdl.toUpperCase().contains("AUTOINCREMENT")) {
            col.setAutoIncrement(true);
        }
        if (col.isGenerated()) {
            applyGeneratedColumnExpression(col, columnDdl);
        }
    }

    private void applyGeneratedColumnExpression(Column col, String columnDdl) {
        String[] split = StringUtils.split(columnDdl);
        int i;
        for (i = 0; i < split.length; i++) {
            if (split[i].equalsIgnoreCase("as")) {
                break;
            }
        }
        if (i < split.length - 1) {
            col.setDefaultValue(String.join(" ", Arrays.copyOfRange(split, i + 1, split.length)));
        }
    }

    public Table readTable(String catalog, String schema, String tableName, String sql) {
        throw new NotImplementedException();
    }

    @Override
    public Relation readRelation(ISqlTransaction transaction, String catalog, String schema, String relationName) {
        return readRelation(catalog, schema, relationName);
    }

    private String quote(String name) {
        String quote = platform.getDatabaseInfo().getDelimiterToken();
        return quote + name + quote;
    }

    @Override
    public Relation readRelation(String catalog, String schema, String relationName) {
        List<Column> columns = platform.getSqlTemplate().query("pragma table_xinfo(" + quote(relationName) + ")", COLUMN_MAPPER);
        checkColumns(columns, relationName);
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        Table table = new Table(relationName);
        for (Column column : columns) {
            table.addColumn(column);
        }
        readIndexesIntoTable(table, relationName);
        readForeignKeysIntoTable(table, relationName);
        return table;
    }

    private void readIndexesIntoTable(Table table, String relationName) {
        List<IIndex> indexes = platform.getSqlTemplate().query("pragma index_list(" + quote(relationName) + ")", INDEX_MAPPER);
        for (IIndex index : indexes) {
            List<IndexColumn> indexColumns = platform.getSqlTemplate().query("pragma index_info(" + index.getName() + ")",
                    INDEX_COLUMN_MAPPER);
            populateIndexColumns(index, indexColumns, table);
            applyIndexToTable(index, indexColumns, table);
        }
    }

    private void populateIndexColumns(IIndex index, List<IndexColumn> indexColumns, Table table) {
        for (IndexColumn indexColumn : indexColumns) {
            /* Ignore auto index columns */
            if (!indexColumn.getName().startsWith("sqlite_autoindex_")) {
                index.addColumn(indexColumn);
                indexColumn.setColumn(table.getColumnWithName(indexColumn.getName()));
            }
        }
    }

    private void applyIndexToTable(IIndex index, List<IndexColumn> indexColumns, Table table) {
        String nameLower = index.getName().toLowerCase();
        if (index.isUnique() && nameLower.contains("autoindex") && !index.hasAllPrimaryKeys()) {
            for (IndexColumn indexColumn : indexColumns) {
                table.getColumnWithName(indexColumn.getName()).setUnique(true);
            }
        } else if (!((index.hasAllPrimaryKeys() || index.isUnique()) && nameLower.contains("autoindex"))) {
            table.addIndex(index);
        }
    }

    private void readForeignKeysIntoTable(Table table, String relationName) {
        Map<Integer, ForeignKey> keys = new HashMap<Integer, ForeignKey>();
        List<Row> rows = platform.getSqlTemplate().query("pragma foreign_key_list(" + quote(relationName) + ")", new RowMapper());
        for (Row row : rows) {
            Integer id = row.getInt("id");
            ForeignKey fk = keys.get(id);
            if (fk == null) {
                fk = new ForeignKey();
                fk.setForeignTable(new Table(row.getString("table")));
                keys.put(id, fk);
                table.addForeignKey(fk);
            }
            fk.addReference(new Reference(new Column(row.getString("from")), new Column(row.getString("to"))));
        }
    }

    public List<String> getCatalogNames() {
        return new ArrayList<String>(0);
    }

    public List<String> getSchemaNames(String catalog) {
        return new ArrayList<String>(0);
    }

    public List<String> getRelationTypes() {
        return new ArrayList<String>(0);
    }

    public List<String> getColumnNames(String catalog, String schema, String relationName) {
        return new ArrayList<String>(0);
    }

    static class ColumnMapper extends AbstractSqlRowMapper<Column> {
        public Column mapRow(Row row) {
            Column col = new Column((String) row.get("name"), booleanValue(row.get("pk")));
            col.setMappedType(toJdbcType((String) row.get("type")));
            col.setRequired(booleanValue(row.get("notnull")));
            col.setDefaultValue(scrubDefaultValue((String) row.get("dflt_value")));
            col.setGenerated(intValue(row.get("hidden")) == 2);
            return col;
        }

        protected String scrubDefaultValue(String defaultValue) {
            if (defaultValue != null && defaultValue.startsWith("'") && defaultValue.endsWith("'")) {
                defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
            }
            return defaultValue;
        }

        public String toJdbcType(String colType) {
            colType = colType == null ? "TEXT" : colType.toUpperCase();
            if (colType.startsWith("INT")) {
                colType = TypeMap.INTEGER;
            } else if (colType.startsWith("NUM")) {
                colType = TypeMap.NUMERIC;
            } else if (colType.startsWith("BLOB")) {
                colType = TypeMap.BLOB;
            } else if (colType.startsWith("CLOB")) {
                colType = TypeMap.CLOB;
            } else if (colType.startsWith("TEXT") || colType.contains("CHAR")) {
                colType = TypeMap.VARCHAR;
            } else if (colType.startsWith("FLOAT")) {
                colType = TypeMap.FLOAT;
            } else if (colType.startsWith("DOUBLE")) {
                colType = TypeMap.DOUBLE;
            } else if (colType.startsWith("REAL")) {
                colType = TypeMap.REAL;
            } else if (colType.startsWith("DECIMAL")) {
                colType = TypeMap.DECIMAL;
            } else if (colType.startsWith("DATE")) {
                colType = TypeMap.DATE;
            } else if (colType.startsWith("TIMESTAMP")) {
                colType = TypeMap.TIMESTAMP;
            } else if (colType.startsWith("TIME")) {
                colType = TypeMap.TIME;
            } else {
                colType = TypeMap.VARCHAR;
            }
            return colType;
        }
    }

    static class IndexMapper extends AbstractSqlRowMapper<IIndex> {
        public IIndex mapRow(Row row) {
            boolean unique = booleanValue(row.get("unique"));
            String name = (String) row.get("name");
            if (unique) {
                return new UniqueIndex(name);
            } else {
                return new NonUniqueIndex(name);
            }
        }
    }

    static class IndexColumnMapper extends AbstractSqlRowMapper<IndexColumn> {
        public IndexColumn mapRow(Row row) {
            IndexColumn column = new IndexColumn();
            column.setName((String) row.get("name"));
            column.setOrdinalPosition(intValue(row.get("seqno")));
            return column;
        }
    }

    public Trigger getTriggerFor(Table table, String triggerName) {
        Trigger trigger = null;
        List<Trigger> triggers = getTriggers(table.getCatalog(), table.getSchema(), table.getName());
        for (Trigger t : triggers) {
            if (t.getName().equals(triggerName)) {
                trigger = t;
                break;
            }
        }
        return trigger;
    }

    public List<Trigger> getTriggers(final String catalog, final String schema, final String tableName) throws SqlException {
        List<Trigger> triggers = new ArrayList<Trigger>();
        String sql = "SELECT " + "name AS trigger_name, " + "tbl_name AS table_name, " + "rootpage, " + "sql, " + "type AS object_type "
                + "FROM sqlite_master " + "WHERE table_name=? AND object_type='trigger';";
        triggers = platform.getSqlTemplate().query(sql, new ISqlRowMapper<Trigger>() {
            public Trigger mapRow(Row row) {
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("trigger_name"));
                trigger.setTableName(row.getString("table_name"));
                trigger.setEnabled(true);
                trigger.setSource(row.getString("sql"));
                row.remove("sql");
                trigger.setMetaData(row);
                return trigger;
            }
        }, tableName.toLowerCase());
        return triggers;
    }

    @Override
    public List<Trigger> getApplicationTriggersForModel(String catalog, String schema, String tableName, String triggerPrefix) {
        List<org.jumpmind.db.model.Trigger> triggers = platform.getDdlReader().getTriggers(catalog, schema, tableName)
                .stream()
                .filter(t -> !t.getName().toUpperCase().startsWith(triggerPrefix.toUpperCase() + "_"))
                .collect(Collectors.toList());
        if (triggers != null && triggers.size() > 0) {
            for (org.jumpmind.db.model.Trigger trigger : triggers) {
                PlatformTrigger platformTrigger = new PlatformTrigger(platform.getName(), trigger.getSource());
                trigger.addPlatformTrigger(platformTrigger);
            }
        }
        return triggers;
    }

    @Override
    public Collection<ForeignKey> getExportedKeys(Table table) {
        return null;
    }

    @Override
    public Collection<ForeignKey> getForeignKeys(String catalog, String schema, String tableName) {
        return null;
    }

    @Override
    public List<TableRow> getExportedForeignTableRows(ISqlTransaction transaction, List<TableRow> tableRows, Set<TableRow> visited, BinaryEncoding encoding) {
        return null;
    }

    @Override
    public List<TableRow> getImportedForeignTableRows(List<TableRow> tableRows, Set<TableRow> visited, BinaryEncoding encoding) {
        return null;
    }

    @Override
    public List<String> getViewNames(String catalog, String schema) {
        return new ArrayList<>();
    }

    @Override
    public PlatformTrigger getPlatformTrigger(IDatabasePlatform platform, Trigger trigger) {
        return new PlatformTrigger(platform.getName(), trigger.getSource());
    }
}
