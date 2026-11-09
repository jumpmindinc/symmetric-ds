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
package org.jumpmind.db.platform.clickhouse;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.alter.AddColumnChange;
import org.jumpmind.db.alter.AddPrimaryKeyChange;
import org.jumpmind.db.alter.ColumnChange;
import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.ColumnRequiredChange;
import org.jumpmind.db.alter.ColumnSizeChange;
import org.jumpmind.db.alter.PrimaryKeyChange;
import org.jumpmind.db.alter.RemoveColumnChange;
import org.jumpmind.db.alter.RemovePrimaryKeyChange;
import org.jumpmind.db.alter.TableChange;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

/**
 * Builds DDL for ClickHouse as a target. Every table is a plain MergeTree ordered by the source primary key (or <code>tuple()</code> when there is none).
 * ClickHouse does not enforce keys, so the ORDER BY is only a sorting key.
 */
public class ClickHouseDdlBuilder extends AbstractDdlBuilder {
    /**
     * Lightweight UPDATE fails with error 48 unless the table keeps these hidden columns.
     */
    public static final String TABLE_SETTINGS = "enable_block_number_column = 1, enable_block_offset_column = 1";
    /**
     * A ClickHouse String holds text and bytes alike, so the column comment records that a column holds binary data. The reader turns it back into a binary
     * type, which makes the loader decode the hex or base64 from the data stream into bytes.
     */
    public static final String BINARY_COLUMN_COMMENT = "symds:binary";
    public static final int MAX_DECIMAL_PRECISION = 76;
    public static final int DEFAULT_DECIMAL_PRECISION = 38;
    public static final int DEFAULT_DECIMAL_SCALE = 10;
    private static final int[] STRING_TYPES = { Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.CLOB, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR,
            Types.NCLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB, Types.SQLXML, Types.OTHER, Types.ARRAY, Types.STRUCT,
            Types.JAVA_OBJECT, Types.DISTINCT, Types.REF, Types.DATALINK, Types.ROWID };

    public ClickHouseDdlBuilder() {
        super(DatabaseNamesConstants.CLICKHOUSE);
        databaseInfo.setTriggersSupported(false);
        databaseInfo.setIndicesSupported(false);
        databaseInfo.setIndicesEmbedded(false);
        databaseInfo.setForeignKeysSupported(false);
        databaseInfo.setForeignKeysEmbedded(false);
        databaseInfo.setPrimaryKeyEmbedded(false);
        databaseInfo.setRequiresAutoCommitForDdl(true);
        databaseInfo.addNativeTypeMapping(Types.BIT, "Bool", Types.BOOLEAN);
        databaseInfo.addNativeTypeMapping(Types.BOOLEAN, "Bool");
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "Int8");
        databaseInfo.addNativeTypeMapping(Types.SMALLINT, "Int16");
        databaseInfo.addNativeTypeMapping(Types.INTEGER, "Int32");
        databaseInfo.addNativeTypeMapping(Types.BIGINT, "Int64");
        databaseInfo.addNativeTypeMapping(Types.REAL, "Float32");
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "Float64", Types.DOUBLE);
        databaseInfo.addNativeTypeMapping(Types.DOUBLE, "Float64");
        databaseInfo.addNativeTypeMapping(Types.DECIMAL, "Decimal");
        databaseInfo.addNativeTypeMapping(Types.NUMERIC, "Decimal", Types.DECIMAL);
        databaseInfo.addNativeTypeMapping(Types.DATE, "Date32");
        databaseInfo.addNativeTypeMapping(Types.TIMESTAMP, "DateTime64(6)");
        databaseInfo.addNativeTypeMapping(Types.TIMESTAMP_WITH_TIMEZONE, "DateTime64(6)", Types.TIMESTAMP);
        // A time of day has no ClickHouse type, so it is kept as text
        databaseInfo.addNativeTypeMapping(Types.TIME, "String", Types.VARCHAR);
        databaseInfo.addNativeTypeMapping(Types.TIME_WITH_TIMEZONE, "String", Types.VARCHAR);
        for (int type : STRING_TYPES) {
            // Binary data is stored as String, which is what the ClickHouse driver reports
            databaseInfo.addNativeTypeMapping(type, "String", Types.VARCHAR);
            databaseInfo.setHasSize(type, false);
        }
        databaseInfo.setHasSize(Types.TIMESTAMP, false);
        databaseInfo.setHasSize(Types.TIMESTAMP_WITH_TIMEZONE, false);
        databaseInfo.setHasSize(Types.TIME, false);
        databaseInfo.setHasSize(Types.TIME_WITH_TIMEZONE, false);
        databaseInfo.setNonBlankCharColumnSpacePadded(false);
        databaseInfo.setBlankCharColumnSpacePadded(false);
        databaseInfo.setCharColumnSpaceTrimmed(false);
        databaseInfo.setEmptyStringNulled(false);
        addEscapedCharSequence("\\", "\\\\");
        addEscapedCharSequence("'", "\\'");
        addEscapedCharSequence("\b", "\\b");
        addEscapedCharSequence("\f", "\\f");
        addEscapedCharSequence("\n", "\\n");
        addEscapedCharSequence("\r", "\\r");
        addEscapedCharSequence("\t", "\\t");
    }

    @Override
    protected void writeTableCreationStmtEnding(Table table, StringBuilder ddl) {
        println(ddl);
        ddl.append("ENGINE = MergeTree ORDER BY ");
        Column[] keys = table.getPrimaryKeyColumnsInIndexOrder();
        if (keys == null || keys.length == 0) {
            ddl.append("tuple()");
        } else if (keys.length == 1) {
            // The driver reports a parenthesised single key as "(id)", so the parentheses are left off
            printIdentifier(getColumnName(keys[0]), ddl);
        } else {
            ddl.append("(");
            for (int i = 0; i < keys.length; i++) {
                if (i > 0) {
                    ddl.append(", ");
                }
                printIdentifier(getColumnName(keys[i]), ddl);
            }
            ddl.append(")");
        }
        ddl.append(" SETTINGS ").append(TABLE_SETTINGS);
        printEndOfStatement(ddl);
    }

    @Override
    protected void writeExternalPrimaryKeysCreateStmt(Table table, Column[] primaryKeyColumns, StringBuilder ddl) {
        // The key is the ORDER BY of the CREATE TABLE, and ClickHouse has no ADD CONSTRAINT PRIMARY KEY
    }

    @Override
    protected void writeColumn(Table table, Column column, StringBuilder ddl) {
        writeColumnTypeDefaultRequired(table, column, ddl);
        if (isBinary(column)) {
            ddl.append(" COMMENT '").append(BINARY_COLUMN_COMMENT).append("'");
        }
    }

    protected boolean isBinary(Column column) {
        int type = column.getMappedTypeCode();
        return type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY || type == Types.BLOB;
    }

    protected boolean sameDefinition(Column first, Column second) {
        return isNullable(first) == isNullable(second) && isBinary(first) == isBinary(second) && getSqlType(first).equals(getSqlType(second));
    }

    @Override
    protected void writeColumnType(Table table, Column column, StringBuilder ddl) {
        // Defaults are not written because the loader always supplies every column value
        String type = getSqlType(column);
        ddl.append(isNullable(column) ? "Nullable(" + type + ")" : type);
    }

    /**
     * Key columns can't be Nullable because ORDER BY on a Nullable column fails with error 44.
     */
    protected boolean isNullable(Column column) {
        return !column.isRequired() && !column.isPrimaryKey();
    }

    @Override
    public String getSqlType(Column column) {
        int type = column.getMappedTypeCode();
        if ((type == Types.DECIMAL || type == Types.NUMERIC) && column.findPlatformColumn(databaseName) == null) {
            int size = column.getSizeAsInt();
            boolean sizeValid = size > 0 && size <= MAX_DECIMAL_PRECISION;
            int precision = sizeValid ? size : DEFAULT_DECIMAL_PRECISION;
            int scale = column.getScale() < 0 ? 0 : column.getScale();
            scale = sizeValid ? Math.min(scale, precision) : Math.min(scale, DEFAULT_DECIMAL_SCALE);
            return "Decimal(" + precision + ", " + scale + ")";
        }
        String sqlType = super.getSqlType(column);
        // A type that arrives without its precision would default to milliseconds
        return "DateTime64".equals(sqlType) ? "DateTime64(6)" : sqlType;
    }

    @Override
    protected void processTableStructureChanges(Database currentModel, Database desiredModel, Table sourceTable, Table targetTable,
            List<TableChange> changes, StringBuilder ddl) {
        Map<String, Column> modifiedColumns = new LinkedHashMap<String, Column>();
        for (Iterator<TableChange> it = changes.iterator(); it.hasNext();) {
            TableChange change = it.next();
            if (change instanceof AddColumnChange) {
                AddColumnChange addColumn = (AddColumnChange) change;
                writeTableAlterStmt(addColumn.getChangedTable(), ddl);
                ddl.append(" ADD COLUMN ");
                writeColumn(addColumn.getChangedTable(), addColumn.getNewColumn(), ddl);
                printEndOfStatement(ddl);
                addColumn.apply(currentModel, delimitedIdentifierModeOn);
            } else if (change instanceof RemoveColumnChange) {
                RemoveColumnChange removeColumn = (RemoveColumnChange) change;
                writeTableAlterStmt(removeColumn.getChangedTable(), ddl);
                ddl.append(" DROP COLUMN ");
                printIdentifier(getColumnName(removeColumn.getColumn()), ddl);
                printEndOfStatement(ddl);
                removeColumn.apply(currentModel, delimitedIdentifierModeOn);
            } else if (change instanceof ColumnDataTypeChange || change instanceof ColumnSizeChange || change instanceof ColumnRequiredChange) {
                Column changed = ((ColumnChange) change).getChangedColumn();
                modifiedColumns.put(changed.getName(), changed);
            } else if (change instanceof PrimaryKeyChange || change instanceof AddPrimaryKeyChange || change instanceof RemovePrimaryKeyChange) {
                log.warn("ClickHouse cannot change the primary key of table {}, so the change is not applied", change.getChangedTable().getName());
            } else {
                log.debug("ClickHouse ignores change {} on table {}", change.getClass().getSimpleName(), change.getChangedTable().getName());
            }
        }
        for (Column changed : new ArrayList<Column>(modifiedColumns.values())) {
            Column desired = targetTable.findColumn(changed.getName(), false);
            if (desired != null && !sameDefinition(changed, desired)) {
                writeTableAlterStmt(targetTable, ddl);
                ddl.append(" MODIFY COLUMN ");
                writeColumn(targetTable, desired, ddl);
                printEndOfStatement(ddl);
            }
        }
        // Everything was handled or deliberately skipped, so the table is never rebuilt
        changes.clear();
    }
}
