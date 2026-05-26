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
package org.jumpmind.db.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.lang3.Strings;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a named, columnar object in the database schema — either a table or a view.
 */
public abstract class Relation extends SchemaObject {
    private static final Logger log = LoggerFactory.getLogger(Relation.class);
    private static final long serialVersionUID = 1L;
    protected static final ColumnPkSequenceComparator COLUMN_PK_SEQUENCE_COMPARATOR = new ColumnPkSequenceComparator();
    protected ArrayList<Column> columns = new ArrayList<>();
    protected ArrayList<Column> lobColumns;
    protected boolean madeAllColumnsPrimaryKey;

    protected Relation() {
    }

    protected Relation(String name) {
        this(null, null, name);
    }

    protected Relation(String catalog, String schema, String name) {
        super(catalog, schema, name);
    }

    public int getColumnCount() {
        return columns.size();
    }

    public int getPrimaryKeyColumnCount() {
        return getPrimaryKeyColumns().length;
    }

    public Column getColumn(int idx) {
        return columns.get(idx);
    }

    public Column[] getColumns() {
        return columns.toArray(new Column[columns.size()]);
    }

    public List<Column> getColumnsAsList() {
        return new ArrayList<>(columns);
    }

    public final void addColumn(Column column) {
        if (column != null) {
            columns.add(column);
            lobColumns = null;
        }
    }

    public void addColumn(int idx, Column column) {
        if (column != null) {
            columns.add(idx, column);
            lobColumns = null;
        }
    }

    public void addColumn(Column previousColumn, Column column) {
        if (column != null) {
            if (previousColumn == null) {
                columns.add(0, column);
            } else {
                columns.add(columns.indexOf(previousColumn), column);
            }
            lobColumns = null;
        }
    }

    public void addColumns(Collection<Column> columns) {
        for (Iterator<Column> it = columns.iterator(); it.hasNext();) {
            addColumn(it.next());
        }
    }

    public void addColumns(String[] columnNames) {
        if (columnNames != null) {
            for (String columnName : columnNames) {
                addColumn(new Column(columnName));
            }
        }
    }

    public void removeColumn(Column column) {
        if (column != null) {
            columns.remove(column);
            lobColumns = null;
        }
    }

    public void removeColumn(int idx) {
        columns.remove(idx);
        lobColumns = null;
    }

    public void removeAllColumns() {
        columns.clear();
        lobColumns = null;
    }

    public void removeAllColumnDefaults() {
        for (Column column : columns) {
            column.setDefaultValue(null);
            Map<String, PlatformColumn> platformColumns = column.getPlatformColumns();
            if (platformColumns != null) {
                Collection<PlatformColumn> cols = platformColumns.values();
                for (PlatformColumn platformColumn : cols) {
                    platformColumn.setDefaultValue(null);
                }
            }
        }
    }

    public void setPrimaryKeys(String[] primaryKeys) {
        if (primaryKeys != null) {
            for (Column column : columns) {
                boolean foundMatch = false;
                for (String primaryKey : primaryKeys) {
                    if (column.getName().equalsIgnoreCase(primaryKey)) {
                        column.setPrimaryKey(true);
                        foundMatch = true;
                    }
                }
                if (!foundMatch) {
                    column.setPrimaryKey(false);
                }
            }
        }
    }

    public boolean isMadeAllColumnsPrimaryKey() {
        return madeAllColumnsPrimaryKey;
    }

    public void setMadeAllColumnsPrimaryKey(boolean madeAllColumnsPrimaryKey) {
        this.madeAllColumnsPrimaryKey = madeAllColumnsPrimaryKey;
    }

    public boolean hasPrimaryKey() {
        for (Iterator<Column> it = columns.iterator(); it.hasNext();) {
            if (it.next().isPrimaryKey()) {
                return true;
            }
        }
        return false;
    }

    public List<Column> getPrimaryKeyColumnsAsList() {
        List<Column> selectedColumns = new ArrayList<>();
        if (columns != null) {
            for (Column column : columns) {
                if (column != null && column.isPrimaryKey()) {
                    selectedColumns.add(column);
                }
            }
        }
        return selectedColumns;
    }

    public Column[] getPrimaryKeyColumns() {
        List<Column> pkColumns = getPrimaryKeyColumnsAsList();
        return pkColumns.toArray(new Column[pkColumns.size()]);
    }

    public Column[] getPrimaryKeyColumnsInIndexOrder() {
        List<Column> pkColumns = getPrimaryKeyColumnsAsList();
        Collections.sort(pkColumns, COLUMN_PK_SEQUENCE_COMPARATOR);
        return pkColumns.toArray(new Column[pkColumns.size()]);
    }

    public Column[] getNonPrimaryKeyColumns() {
        List<Column> nonPkColumns = new ArrayList<>();
        List<Column> pkColumns = getPrimaryKeyColumnsAsList();
        for (Column c : columns) {
            if (!pkColumns.contains(c)) {
                nonPkColumns.add(c);
            }
        }
        return nonPkColumns.toArray(new Column[nonPkColumns.size()]);
    }

    public Column[] getAutoIncrementColumns() {
        if (columns != null) {
            List<Column> selectedColumns = new ArrayList<>();
            for (Column column : columns) {
                if (column.isAutoIncrement()) {
                    selectedColumns.add(column);
                }
            }
            return selectedColumns.toArray(new Column[selectedColumns.size()]);
        }
        return new Column[0];
    }

    public Column[] getDistributionKeyColumns() {
        if (columns != null) {
            List<Column> selectedColumns = new ArrayList<>();
            for (Column column : columns) {
                if (column.isDistributionKey()) {
                    selectedColumns.add(column);
                }
            }
            return selectedColumns.toArray(new Column[selectedColumns.size()]);
        }
        return new Column[0];
    }

    public boolean hasNTypeColumns() {
        for (Iterator<Column> it = columns.iterator(); it.hasNext();) {
            Column column = it.next();
            if (column.getJdbcTypeCode() == ColumnTypes.NCHAR || column.getJdbcTypeCode() == ColumnTypes.NVARCHAR
                    || column.getJdbcTypeCode() == ColumnTypes.LONGNVARCHAR || column.getJdbcTypeCode() == ColumnTypes.NCLOB
                    || (column.getJdbcTypeName() != null
                            && (column.getJdbcTypeName().startsWith("NVARCHAR") || column.getJdbcTypeName().startsWith("NCHAR")))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasGeneratedColumns() {
        for (Iterator<Column> it = columns.iterator(); it.hasNext();) {
            if (it.next().isGenerated()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAutoIncrementColumn() {
        if (columns != null) {
            for (Column column : getColumns()) {
                if (column.isAutoIncrement()) {
                    return true;
                }
            }
        }
        return false;
    }

    public Column findColumn(String name) {
        return findColumn(name, false);
    }

    public Column findColumn(String name, boolean caseSensitive) {
        for (Iterator<Column> it = columns.iterator(); it.hasNext();) {
            Column column = it.next();
            if (caseSensitive) {
                if (column.getName().equals(name)) {
                    return column;
                }
            } else {
                if (column.getName().equalsIgnoreCase(name)) {
                    return column;
                }
            }
        }
        return null;
    }

    public int getColumnIndex(Column column) {
        return getColumnIndex(column.getName());
    }

    public int getColumnIndex(String columnName) {
        int idx = 0;
        for (Iterator<Column> it = columns.iterator(); it.hasNext(); idx++) {
            if (columnName != null && columnName.equalsIgnoreCase(it.next().getName())) {
                return idx;
            }
        }
        return -1;
    }

    public int getPrimaryKeyColumnIndex(String columnName) {
        int idx = 0;
        List<Column> primaryKeyColumns = getPrimaryKeyColumnsAsList();
        for (Iterator<Column> it = primaryKeyColumns.iterator(); it.hasNext(); idx++) {
            if (columnName != null && columnName.equalsIgnoreCase(it.next().getName())) {
                return idx;
            }
        }
        return -1;
    }

    public final Column getColumnWithName(String name) {
        Column[] cols = getColumns();
        if (cols != null) {
            for (Column column : cols) {
                if (column.getName().equalsIgnoreCase(name)) {
                    return column;
                }
            }
        }
        return null;
    }

    public Column[] getColumnsWithName(String[] columnNames) {
        Column[] result = new Column[columnNames.length];
        int index = 0;
        for (String columnName : columnNames) {
            result[index++] = getColumnWithName(columnName);
        }
        return result;
    }

    public String[] getColumnNames() {
        String[] columnNames = new String[columns.size()];
        int i = 0;
        for (Column col : columns) {
            columnNames[i++] = col.getName();
        }
        return columnNames;
    }

    public String[] getPrimaryKeyColumnNames() {
        Column[] pkCols = getPrimaryKeyColumns();
        String[] columnNames = new String[pkCols.length];
        int i = 0;
        for (Column col : pkCols) {
            columnNames[i++] = col.getName();
        }
        return columnNames;
    }

    public boolean containsJdbcTypes() {
        Column[] cols = getColumns();
        if (cols != null && cols.length > 0) {
            for (Column column : cols) {
                if (!column.containsJdbcTypes()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public void copyColumnTypesFrom(Relation source) {
        if (source != null && columns != null) {
            for (Column column : columns) {
                Column sourceColumn = source.getColumnWithName(column.getName());
                if (sourceColumn != null) {
                    column.setJdbcTypeCode(sourceColumn.getJdbcTypeCode());
                    column.setJdbcTypeName(sourceColumn.getJdbcTypeName());
                    column.setMappedTypeCode(sourceColumn.getMappedTypeCode());
                    column.setMappedType(sourceColumn.getMappedType());
                }
            }
        }
    }

    public boolean containsLobColumns(IDatabasePlatform platform) {
        if (lobColumns == null) {
            lobColumns = populateLobColumns(platform);
        }
        return !lobColumns.isEmpty();
    }

    public List<Column> getLobColumns(IDatabasePlatform platform) {
        if (lobColumns == null) {
            lobColumns = populateLobColumns(platform);
        }
        return lobColumns;
    }

    private ArrayList<Column> populateLobColumns(IDatabasePlatform platform) {
        ArrayList<Column> lobs = new ArrayList<>();
        for (Column c : columns) {
            if (platform.isLob(c)) {
                lobs.add(c);
            }
        }
        return lobs;
    }

    public void orderColumns(String[] columnNames) {
        orderColumns(columnNames, false);
    }

    public void orderColumns(String[] columnNames, boolean addMissingColumns) {
        Column[] orderedColumns = orderColumns(columnNames, this, addMissingColumns);
        this.columns.clear();
        for (Column column : orderedColumns) {
            if (column != null) {
                this.columns.add(column);
            }
        }
    }

    public static Column[] orderColumns(String[] columnNames, Relation relation, boolean addMissingColumns) {
        Column[] unorderedColumns = relation.getColumns();
        Column[] orderedColumns = new Column[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            String colName = columnNames[i];
            for (Column column : unorderedColumns) {
                if (column != null && column.getName().equals(colName)) {
                    orderedColumns[i] = column;
                    break;
                }
            }
            if (orderedColumns[i] == null) {
                for (Column column : unorderedColumns) {
                    if (column != null && column.getName().equalsIgnoreCase(colName)) {
                        orderedColumns[i] = column;
                        break;
                    }
                }
            }
            if (orderedColumns[i] == null) {
                if (!addMissingColumns) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not find column with the name of {} on {}.", colName, relation.getFullyQualifiedName());
                    }
                } else {
                    orderedColumns[i] = new Column(colName);
                    if (log.isDebugEnabled()) {
                        log.debug("Could not find column with the name of {} on {}. Added this column to the list of columns.",
                                colName, relation.getFullyQualifiedName());
                    }
                }
            }
        }
        return orderedColumns;
    }

    public abstract Relation copy();

    public String toVerboseString() {
        StringBuilder result = new StringBuilder();
        result.append(getClass().getSimpleName()).append(" [name=");
        result.append(getName());
        result.append("; catalog=");
        result.append(getCatalog());
        result.append("; schema=");
        result.append(getSchema());
        result.append("; type=");
        result.append(getType());
        result.append("] columns:");
        for (int idx = 0; idx < getColumnCount(); idx++) {
            result.append(" ");
            result.append(getColumn(idx).toVerboseString());
        }
        return result.toString();
    }

    public abstract Relation copyAndFilterColumns(String[] orderedColumnNames, String[] pkColumnNames,
            boolean setPrimaryKeys, boolean addMissingColumns);

    public boolean equalsByName(Relation other) {
        if (this == other) {
            return true;
        }
        if (other != null && Strings.CI.equals(catalog, other.catalog) && Strings.CI.equals(schema, other.schema) &&
                Strings.CI.equals(name, other.name)) {
            if (columns == other.columns) {
                return true;
            }
            ListIterator<Column> iter1 = columns.listIterator();
            ListIterator<Column> iter2 = other.columns.listIterator();
            while (iter1.hasNext() && iter2.hasNext()) {
                Column c1 = iter1.next();
                Column c2 = iter2.next();
                if (!(c1 == null ? c2 == null : c1.equalsByName(c2))) {
                    return false;
                }
            }
            return !(iter1.hasNext() || iter2.hasNext());
        }
        return false;
    }

    public int calculateHashcode() {
        return calculateHashcode(true);
    }

    public int calculateLiteHashcode() {
        return calculateHashcode(false);
    }

    protected int calculateHashcode(boolean includeTypes) {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + name.hashCode();
        result = PRIME * result + calculateHashcodeForColumns(PRIME, getColumns(), includeTypes);
        result = PRIME * result + calculateHashcodeForColumns(PRIME, getPrimaryKeyColumns(), includeTypes);
        return result;
    }

    private static int calculateHashcodeForColumns(final int PRIME, Column[] cols, boolean includeTypes) {
        int result = 1;
        if (cols != null && cols.length > 0) {
            for (Column column : cols) {
                result = PRIME * result + column.getName().hashCode();
                if (includeTypes && column.getMappedType() != null) {
                    result = PRIME * result + column.getMappedType().hashCode();
                }
                result = PRIME * result + column.getSizeAsInt();
            }
        }
        return result;
    }

    public String getKey() {
        return getFullyQualifiedName() + "-" + calculateLiteHashcode();
    }

    public String getQualifiedColumnName(Column column) {
        return getFullyQualifiedName() + "." + column.getName();
    }

    public static boolean areAllColumnsPrimaryKeys(Column[] cols) {
        boolean allPks = true;
        if (cols != null) {
            for (Column column : cols) {
                allPks &= column.isPrimaryKey();
            }
        }
        return allPks;
    }

    public static String getCommaDeliminatedColumns(Column[] cols) {
        StringBuilder sb = new StringBuilder();
        if (cols != null && cols.length > 0) {
            for (Column column : cols) {
                sb.append(escapeColumnNameForCsv(column.getName()));
                sb.append(",");
            }
            sb.replace(sb.length() - 1, sb.length(), "");
            return sb.toString();
        }
        return " ";
    }

    public static String[] getArrayColumns(Column[] cols) {
        if (cols != null) {
            String[] result = new String[cols.length];
            for (int i = 0; i < cols.length; i++) {
                result[i] = cols[i].getName();
            }
            return result;
        }
        return new String[0];
    }

    public static String escapeColumnNameForCsv(String columnName) {
        if (columnName != null &&
                (columnName.indexOf('\\') != -1
                        || columnName.indexOf(',') != -1
                        || columnName.indexOf('"') != -1)) {
            columnName = columnName.replace("\\", "\\\\");
            columnName = columnName.replace("\"", "\\\"");
            return "\"" + columnName + "\"";
        }
        return columnName;
    }

    static class ColumnPkSequenceComparator implements Comparator<Column> {
        @Override
        public int compare(Column o1, Column o2) {
            if (o1 != null && o2 != null) {
                return Integer.compare(o1.getPrimaryKeySequence(), o2.getPrimaryKeySequence());
            } else if (o1 == null && o2 != null) {
                return -1;
            } else if (o1 != null) {
                return 1;
            }
            return 0;
        }
    }
}
