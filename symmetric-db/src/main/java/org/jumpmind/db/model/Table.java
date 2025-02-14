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

import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a table in the database model.
 */
public class Table implements Serializable, Cloneable, Comparable<Table> {
    private final static Logger log = LoggerFactory.getLogger(Table.class);
    /** Unique ID for serialization purposes. */
    private static final long serialVersionUID = 1L;
    private static final ColumnPkSequenceComparator columnPkSequenceComparator = new ColumnPkSequenceComparator();
    private String oldCatalog = null;
    private String oldSchema = null;
    /** The catalog of this table as read from the database. */
    private String catalog = null;
    /** The table's schema. */
    private String schema = null;
    /** The name. */
    private String name = null;
    /** A description of the table. */
    private String description = null;
    /** The table's type as read from the database. */
    private String type = null;
    private boolean isAccessControlled;
    /** The columns in this table. */
    private ArrayList<Column> columns = new ArrayList<Column>();
    /** The foreign keys associated to this table. */
    private ArrayList<ForeignKey> foreignKeys = new ArrayList<ForeignKey>();
    /** The indices applied to this table. */
    private ArrayList<IIndex> indices = new ArrayList<IIndex>();
    private String primaryKeyConstraintName;
    private String fullyQualifiedTableName;
    private String fullyQualifiedTableNameLowerCase;
    private String tableNameLowerCase;
    private ArrayList<Column> lobColumns;
    private CompressionTypes compressionType = CompressionTypes.NONE;
    private boolean madeAllColumnsPrimaryKey;
    /** This table has changes logged by the transaction log. */
    private boolean logging = true;
    private ArrayList<Trigger> triggers = new ArrayList<Trigger>();

    public Table() {
    }

    public Table(String tableName) {
        this(null, null, tableName);
    }

    public Table(String tableName, Column... columns) {
        this(null, null, tableName);
        if (columns != null) {
            for (Column column : columns) {
                addColumn(column);
            }
        }
    }

    public Table(String catalog, String schema, String tableName) {
        this.catalog = catalog;
        this.schema = schema;
        this.name = tableName;
    }

    public Table(String catalog, String schema, String tableName, String[] columnNames,
            String[] keyNames) {
        this(catalog, schema, tableName);
        for (String name : columnNames) {
            addColumn(new Column(name));
        }
        for (String name : keyNames) {
            Column column = getColumnWithName(name);
            if (column != null) {
                column.setPrimaryKey(true);
            }
        }
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

    public void removeAllIndices() {
        indices.clear();
    }

    public void removeAllForeignKeys() {
        foreignKeys.clear();
    }

    public void removeAllIndexes() {
        indices.clear();
    }

    public void removeAllTriggers() {
        triggers.clear();
    }

    /**
     * Returns the catalog of this table as read from the database.
     * 
     * @return The catalog
     */
    public String getCatalog() {
        return catalog;
    }

    /**
     * Sets the catalog of this table.
     * 
     * @param catalog
     *            The catalog
     */
    public void setCatalog(String catalog) {
        for (ForeignKey fk : getForeignKeys()) {
            if (fk.getForeignTableCatalog() != null && fk.getForeignTableCatalog().equals(this.catalog)) {
                fk.setForeignTableCatalog(catalog);
            }
        }
        this.oldCatalog = this.catalog != null ? this.catalog : catalog;
        this.catalog = catalog;
        this.fullyQualifiedTableName = this.fullyQualifiedTableNameLowerCase = null;
    }

    /**
     * Returns the schema of this table as read from the database.
     * 
     * @return The schema
     */
    public String getSchema() {
        return schema;
    }

    /**
     * Sets the schema of this table.
     * 
     * @param schema
     *            The schema
     */
    public void setSchema(String schema) {
        for (ForeignKey fk : getForeignKeys()) {
            if (fk.getForeignTableSchema() != null && fk.getForeignTableSchema().equals(this.schema)) {
                fk.setForeignTableSchema(schema);
            }
        }
        this.oldSchema = this.schema != null ? this.schema : schema;
        this.schema = schema;
        this.fullyQualifiedTableName = this.fullyQualifiedTableNameLowerCase = null;
    }

    /**
     * Returns the type of this table as read from the database.
     * 
     * @return The type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of this table.
     * 
     * @param type
     *            The type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the name of the table.
     * 
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the table.
     * 
     * @param name
     *            The name
     */
    public void setName(String name) {
        this.name = name;
        this.fullyQualifiedTableName = this.fullyQualifiedTableNameLowerCase = null;
    }

    /**
     * Returns the description of the table.
     * 
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the table.
     * 
     * @param description
     *            The description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the number of columns in this table.
     * 
     * @return The number of columns
     */
    public int getColumnCount() {
        return columns.size();
    }

    public int getPrimaryKeyColumnCount() {
        return getPrimaryKeyColumns().length;
    }

    /**
     * Returns the column at the specified position.
     * 
     * @param idx
     *            The column index
     * @return The column at this position
     */
    public Column getColumn(int idx) {
        return columns.get(idx);
    }

    /**
     * Returns the columns in this table.
     * 
     * @return The columns
     */
    public Column[] getColumns() {
        return columns.toArray(new Column[columns.size()]);
    }

    /**
     * Returns the columns in this table.
     * 
     * @return The columns
     */
    public List<Column> getColumnsAsList() {
        return new ArrayList<Column>(this.columns);
    }

    /**
     * Adds the given column.
     * 
     * @param column
     *            The column
     */
    public final void addColumn(Column column) {
        if (column != null) {
            columns.add(column);
            lobColumns = null;
        }
    }

    /**
     * Adds the given column at the specified position.
     * 
     * @param idx
     *            The index where to add the column
     * @param column
     *            The column
     */
    public void addColumn(int idx, Column column) {
        if (column != null) {
            columns.add(idx, column);
            lobColumns = null;
        }
    }

    /**
     * Adds the column after the given previous column.
     * 
     * @param previousColumn
     *            The column to add the new column after; use <code>null</code> for adding at the begin
     * @param column
     *            The column
     */
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

    /**
     * Adds the given columns.
     * 
     * @param columns
     *            The columns
     */
    public void addColumns(Collection<Column> columns) {
        for (Iterator<Column> it = columns.iterator(); it.hasNext();) {
            addColumn(it.next());
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

    public void addColumns(String[] columnNames) {
        if (columnNames != null) {
            for (String columnName : columnNames) {
                addColumn(new Column(columnName));
            }
        }
    }

    /**
     * Removes the given column.
     * 
     * @param column
     *            The column to remove
     */
    public void removeColumn(Column column) {
        if (column != null) {
            columns.remove(column);
            lobColumns = null;
        }
    }

    /**
     * Removes the indicated column.
     * 
     * @param idx
     *            The index of the column to remove
     */
    public void removeColumn(int idx) {
        columns.remove(idx);
        lobColumns = null;
    }

    /**
     * Returns the number of foreign keys.
     * 
     * @return The number of foreign keys
     */
    public int getForeignKeyCount() {
        return foreignKeys.size();
    }

    /**
     * Returns the foreign key at the given position.
     * 
     * @param idx
     *            The foreign key index
     * @return The foreign key
     */
    public ForeignKey getForeignKey(int idx) {
        return foreignKeys.get(idx);
    }

    /**
     * Returns the foreign keys of this table.
     * 
     * @return The foreign keys
     */
    public ForeignKey[] getForeignKeys() {
        return foreignKeys.toArray(new ForeignKey[foreignKeys.size()]);
    }

    /**
     * Adds the given foreign key.
     * 
     * @param foreignKey
     *            The foreign key
     */
    public void addForeignKey(ForeignKey foreignKey) {
        if (foreignKey != null) {
            foreignKeys.add(foreignKey);
        }
    }

    /**
     * Adds the given foreign key at the specified position.
     * 
     * @param idx
     *            The index to add the foreign key at
     * @param foreignKey
     *            The foreign key
     */
    public void addForeignKey(int idx, ForeignKey foreignKey) {
        if (foreignKey != null) {
            foreignKeys.add(idx, foreignKey);
        }
    }

    /**
     * Adds the given foreign keys.
     * 
     * @param foreignKeys
     *            The foreign keys
     */
    public void addForeignKeys(Collection<ForeignKey> foreignKeys) {
        for (Iterator<ForeignKey> it = foreignKeys.iterator(); it.hasNext();) {
            addForeignKey(it.next());
        }
    }

    /**
     * Removes the given foreign key.
     * 
     * @param foreignKey
     *            The foreign key to remove
     */
    public void removeForeignKey(ForeignKey foreignKey) {
        if (foreignKey != null) {
            foreignKeys.remove(foreignKey);
        }
    }

    /**
     * Removes the indicated foreign key.
     * 
     * @param idx
     *            The index of the foreign key to remove
     */
    public void removeForeignKey(int idx) {
        foreignKeys.remove(idx);
    }

    /**
     * Returns the number of indices.
     * 
     * @return The number of indices
     */
    public int getIndexCount() {
        return indices.size();
    }

    /**
     * Returns the index at the specified position.
     * 
     * @param idx
     *            The position
     * @return The index
     */
    public IIndex getIndex(int idx) {
        return indices.get(idx);
    }

    /**
     * Adds the given index.
     * 
     * @param index
     *            The index
     */
    public void addIndex(IIndex index) {
        if (index != null) {
            indices.add(index);
        }
    }

    /**
     * Adds the given index at the specified position.
     * 
     * @param idx
     *            The position to add the index at
     * @param index
     *            The index
     */
    public void addIndex(int idx, IIndex index) {
        if (index != null) {
            indices.add(idx, index);
        }
    }

    /**
     * Adds the given indices.
     * 
     * @param indices
     *            The indices
     */
    public void addIndices(Collection<IIndex> indices) {
        for (Iterator<IIndex> it = indices.iterator(); it.hasNext();) {
            addIndex(it.next());
        }
    }

    /**
     * Returns the indices of this table.
     * 
     * @return The indices
     */
    public IIndex[] getIndices() {
        return indices.toArray(new IIndex[indices.size()]);
    }

    /**
     * Gets a list of non-unique indices on this table.
     * 
     * @return The unique indices
     */
    public IIndex[] getNonUniqueIndices() {
        if (indices != null) {
            List<IIndex> nonunique = new ArrayList<IIndex>();
            for (IIndex index : indices) {
                if (!index.isUnique()) {
                    nonunique.add(index);
                }
            }
            return nonunique.toArray(new IIndex[nonunique.size()]);
        } else {
            return new IIndex[0];
        }
    }

    /**
     * Gets a list of unique indices on this table.
     * 
     * @return The unique indices
     */
    public IIndex[] getUniqueIndices() {
        if (indices != null) {
            List<IIndex> unique = new ArrayList<IIndex>();
            for (IIndex index : indices) {
                if (index.isUnique()) {
                    unique.add(index);
                }
            }
            return unique.toArray(new IIndex[unique.size()]);
        } else {
            return new IIndex[0];
        }
    }

    /**
     * Removes the given index.
     * 
     * @param index
     *            The index to remove
     */
    public void removeIndex(IIndex index) {
        if (index != null) {
            indices.remove(index);
        }
    }

    /**
     * Removes the indicated index.
     * 
     * @param idx
     *            The position of the index to remove
     */
    public void removeIndex(int idx) {
        indices.remove(idx);
    }

    /**
     * Determines whether there is at least one primary key column on this table.
     * 
     * @return <code>true</code> if there are one or more primary key columns
     */
    public boolean hasPrimaryKey() {
        for (Iterator<Column> it = columns.iterator(); it.hasNext();) {
            Column column = it.next();
            if (column.isPrimaryKey()) {
                return true;
            }
        }
        return false;
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
            Column column = it.next();
            if (column.isGenerated()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the column with the specified name, using case insensitive matching. Note that this method is not called getColumn(String) to avoid introspection
     * problems.
     * 
     * @param name
     *            The name of the column
     * @return The column or <code>null</code> if there is no such column
     */
    public Column findColumn(String name) {
        return findColumn(name, false);
    }

    /**
     * Finds the column with the specified name, using case insensitive matching. Note that this method is not called getColumn(String) to avoid introspection
     * problems.
     * 
     * @param name
     *            The name of the column
     * @param caseSensitive
     *            Whether case matters for the names
     * @return The column or <code>null</code> if there is no such column
     */
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

    /**
     * Determines the index of the given column.
     * 
     * @param column
     *            The column
     * @return The index or <code>-1</code> if it is no column of this table
     */
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

    /**
     * Finds the index with the specified name, using case insensitive matching. Note that this method is not called getIndex to avoid introspection problems.
     * 
     * @param name
     *            The name of the index
     * @return The index or <code>null</code> if there is no such index
     */
    public IIndex findIndex(String name) {
        return findIndex(name, false);
    }

    /**
     * Finds the index with the specified name, using case insensitive matching. Note that this method is not called getIndex to avoid introspection problems.
     * 
     * @param name
     *            The name of the index
     * @param caseSensitive
     *            Whether case matters for the names
     * @return The index or <code>null</code> if there is no such index
     */
    public IIndex findIndex(String name, boolean caseSensitive) {
        for (int idx = 0; idx < getIndexCount(); idx++) {
            IIndex index = getIndex(idx);
            if (caseSensitive) {
                if (index.getName().equals(name)) {
                    return index;
                }
            } else {
                if (index.getName().equalsIgnoreCase(name)) {
                    return index;
                }
            }
        }
        return null;
    }

    /**
     * Finds the foreign key in this table that is equal to the supplied foreign key.
     * 
     * @param key
     *            The foreign key to search for
     * @return The found foreign key
     */
    public ForeignKey findForeignKey(ForeignKey key) {
        for (int idx = 0; idx < getForeignKeyCount(); idx++) {
            ForeignKey fk = getForeignKey(idx);
            if (fk.equals(key)) {
                return fk;
            }
        }
        return null;
    }

    /**
     * Finds the foreign key in this table that is equal to the supplied foreign key.
     * 
     * @param key
     *            The foreign key to search for
     * @param caseSensitive
     *            Whether case matters for the names
     * @return The found foreign key
     */
    public ForeignKey findForeignKey(ForeignKey key, boolean caseSensitive) {
        for (int idx = 0; idx < getForeignKeyCount(); idx++) {
            ForeignKey fk = getForeignKey(idx);
            if ((caseSensitive && fk.equals(key)) || (!caseSensitive && fk.equalsIgnoreCase(key))) {
                return fk;
            }
        }
        return null;
    }

    /**
     * Returns the foreign key referencing this table if it exists.
     * 
     * @return The self-referencing foreign key if any
     */
    public ForeignKey getSelfReferencingForeignKey() {
        for (int idx = 0; idx < getForeignKeyCount(); idx++) {
            ForeignKey fk = getForeignKey(idx);
            if (this.getName().equalsIgnoreCase(fk.getForeignTableName())) {
                return fk;
            }
        }
        return null;
    }

    /**
     * Returns the primary key columns of this table.
     * 
     * @return The primary key columns
     */
    public List<Column> getPrimaryKeyColumnsAsList() {
        if (columns != null) {
            List<Column> selectedColumns = new ArrayList<Column>();
            for (Column column : columns) {
                if (column != null) {
                    if (column.isPrimaryKey()) {
                        selectedColumns.add(column);
                    }
                }
            }
            return selectedColumns;
        } else {
            return new ArrayList<Column>(0);
        }
    }

    /**
     * Returns the primary key columns of this table.
     * 
     * @return The primary key columns
     */
    public Column[] getPrimaryKeyColumns() {
        List<Column> pkColumns = getPrimaryKeyColumnsAsList();
        return pkColumns.toArray(new Column[pkColumns.size()]);
    }

    public Column[] getPrimaryKeyColumnsInIndexOrder() {
        List<Column> pkColumns = getPrimaryKeyColumnsAsList();
        Collections.sort(pkColumns, columnPkSequenceComparator);
        return pkColumns.toArray(new Column[pkColumns.size()]);
    }

    /**
     * Returns the columns in this table that are not a PK.
     * 
     */
    public Column[] getNonPrimaryKeyColumns() {
        List<Column> nonPkColumns = new ArrayList<Column>();
        List<Column> pkColumns = getPrimaryKeyColumnsAsList();
        for (Column c : columns) {
            if (!pkColumns.contains(c)) {
                nonPkColumns.add(c);
            }
        }
        return nonPkColumns.toArray(new Column[nonPkColumns.size()]);
    }

    /**
     * Returns the auto increment columns in this table. If no incrementcolumns are found, it will return an empty array.
     * 
     * @return The columns
     */
    public Column[] getAutoIncrementColumns() {
        if (columns != null) {
            List<Column> selectedColumns = new ArrayList<Column>();
            for (Column column : columns) {
                if (column.isAutoIncrement()) {
                    selectedColumns.add(column);
                }
            }
            return selectedColumns.toArray(new Column[selectedColumns.size()]);
        } else {
            return new Column[0];
        }
    }

    /**
     * Sorts the foreign keys alphabetically.
     * 
     * @param caseSensitive
     *            Whether case matters
     */
    public void sortForeignKeys(final boolean caseSensitive) {
        if (!foreignKeys.isEmpty()) {
            final Collator collator = Collator.getInstance();
            Collections.sort(foreignKeys, new Comparator<ForeignKey>() {
                @Override
                public int compare(ForeignKey obj1, ForeignKey obj2) {
                    String fk1Name = obj1.getName();
                    String fk2Name = obj2.getName();
                    if (!caseSensitive) {
                        fk1Name = (fk1Name != null ? fk1Name.toLowerCase() : null);
                        fk2Name = (fk2Name != null ? fk2Name.toLowerCase() : null);
                    }
                    return collator.compare(fk1Name, fk2Name);
                }
            });
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        Table result = (Table) super.clone();
        result.catalog = catalog;
        result.schema = schema;
        result.name = name;
        result.type = type;
        result.columns = new ArrayList<Column>(columns.size());
        for (Column col : columns) {
            if (col != null) {
                result.columns.add((Column) col.clone());
            }
        }
        result.foreignKeys = new ArrayList<ForeignKey>(foreignKeys.size());
        for (ForeignKey fk : foreignKeys) {
            if (fk != null) {
                result.foreignKeys.add((ForeignKey) fk.clone());
            }
        }
        result.indices = new ArrayList<IIndex>(indices.size());
        for (IIndex i : indices) {
            if (i != null) {
                result.indices.add((IIndex) i.clone());
            }
        }
        result.triggers = new ArrayList<Trigger>(triggers.size());
        for (Trigger t : triggers) {
            if (t != null) {
                result.triggers.add((Trigger) t.clone());
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Table) {
            Table other = (Table) obj;
            // Note that this compares case sensitive
            return new EqualsBuilder()
                    .append(catalog, other.catalog)
                    .append(schema, other.schema)
                    .append(name, other.name)
                    .append(columns, other.columns)
                    .append(new HashSet<ForeignKey>(foreignKeys),
                            new HashSet<ForeignKey>(other.foreignKeys))
                    .append(new HashSet<IIndex>(indices), new HashSet<IIndex>(other.indices))
                    .isEquals();
        } else {
            return false;
        }
    }

    public boolean equalsByName(Table other) {
        if (this == other) {
            return true;
        }
        if (other != null && StringUtils.equalsIgnoreCase(catalog, other.catalog) && StringUtils.equalsIgnoreCase(schema, other.schema) &&
                StringUtils.equalsIgnoreCase(name, other.name)) {
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

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(name).append(catalog).append(schema)
                .append(columns).append(new HashSet<ForeignKey>(foreignKeys))
                .append(new HashSet<IIndex>(indices)).toHashCode();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Table [name=");
        result.append(getName());
        result.append("; ");
        result.append(getColumnCount());
        result.append(" columns]");
        return result.toString();
    }

    /**
     * Returns a verbose string representation of this table.
     * 
     * @return The string representation
     */
    public String toVerboseString() {
        StringBuilder result = new StringBuilder();
        result.append("Table [name=");
        result.append(getName());
        result.append("; catalog=");
        result.append(getCatalog());
        result.append("; schema=");
        result.append(getSchema());
        result.append("; type=");
        result.append(getType());
        result.append("; logging=");
        result.append(getLogging());
        result.append("] columns:");
        for (int idx = 0; idx < getColumnCount(); idx++) {
            result.append(" ");
            result.append(getColumn(idx).toVerboseString());
        }
        result.append("; indices:");
        for (int idx = 0; idx < getIndexCount(); idx++) {
            result.append(" ");
            result.append(getIndex(idx).toVerboseString());
        }
        result.append("; foreign keys:");
        for (int idx = 0; idx < getForeignKeyCount(); idx++) {
            result.append(" ");
            result.append(getForeignKey(idx).toVerboseString());
        }
        return result.toString();
    }

    public String getTableKey() {
        return getFullyQualifiedTableName() + "-" + calculateTableLiteHashcode();
    }

    public boolean containsLobColumns(IDatabasePlatform platform) {
        if (lobColumns == null) {
            lobColumns = populateLobColumns(platform);
        }
        return lobColumns.size() > 0;
    }

    public List<Column> getLobColumns(IDatabasePlatform platform) {
        if (lobColumns == null) {
            lobColumns = populateLobColumns(platform);
        }
        return lobColumns;
    }

    private ArrayList<Column> populateLobColumns(IDatabasePlatform platform) {
        ArrayList<Column> lobColumns = new ArrayList<Column>();
        for (Column c : columns) {
            if (platform.isLob(c)) {
                lobColumns.add(c);
            }
        }
        return lobColumns;
    }

    public String getFullyQualifiedTableName() {
        if (fullyQualifiedTableName == null) {
            fullyQualifiedTableName = getFullyQualifiedTableName(catalog, schema, name, null, ".", ".");
        }
        return fullyQualifiedTableName;
    }

    public String getFullyQualifiedTableNameLowerCase() {
        if (fullyQualifiedTableNameLowerCase == null) {
            fullyQualifiedTableNameLowerCase = getFullyQualifiedTableName().toLowerCase();
        }
        return fullyQualifiedTableNameLowerCase;
    }

    public String getFullyQualifiedTableName(String quote) {
        return getFullyQualifiedTableName(catalog, schema, name, quote, ".", ".");
    }

    public String getNameLowerCase() {
        if (tableNameLowerCase == null) {
            tableNameLowerCase = getName().toLowerCase();
        }
        return tableNameLowerCase;
    }

    public static String getFullyQualifiedTableName(String catalogName, String schemaName,
            String tableName) {
        return getFullyQualifiedTableName(catalogName, schemaName, tableName, null, ".", ".");
    }

    public String getQualifiedColumnName(Column column) {
        return getFullyQualifiedTableName() + "." + column.getName();
    }

    public static String getFullyQualifiedTablePrefix(String catalogName, String schemaName) {
        return getFullyQualifiedTablePrefix(new StringBuilder(), catalogName, schemaName, null, ".", ".");
    }

    public String getQualifiedTableName(String quoteString, String catalogSeparator, String schemaSeparator) {
        return getFullyQualifiedTableName(catalog, schema, name, quoteString, catalogSeparator, schemaSeparator);
    }

    public String getQualifiedTableName() {
        return getQualifiedTableName("", ".", ".");
    }

    public static String getFullyQualifiedTableName(String catalogName, String schemaName,
            String tableName, String quoteString, String catalogSeparator, String schemaSeparator) {
        if (quoteString == null) {
            quoteString = "";
        }
        StringBuilder sb = new StringBuilder();
        getFullyQualifiedTablePrefix(sb, catalogName, schemaName, quoteString, catalogSeparator, schemaSeparator);
        sb.append(quoteString).append(tableName).append(quoteString);
        return sb.toString();
    }

    public static String getFullyQualifiedTablePrefix(String catalogName, String schemaName,
            String quoteString, String catalogSeparator, String schemaSeparator) {
        return getFullyQualifiedTablePrefix(new StringBuilder(), catalogName, schemaName, quoteString, catalogSeparator, schemaSeparator);
    }

    public static String getFullyQualifiedTablePrefix(StringBuilder sb, String catalogName, String schemaName,
            String quoteString, String catalogSeparator, String schemaSeparator) {
        if (quoteString == null) {
            quoteString = "";
        }
        if (!StringUtils.isBlank(catalogName)) {
            sb.append(quoteString).append(catalogName).append(quoteString).append(catalogSeparator);
        }
        if (!StringUtils.isBlank(schemaName)) {
            sb.append(quoteString).append(schemaName).append(quoteString).append(schemaSeparator);
        }
        return sb.toString();
    }

    public String getQualifiedTablePrefix(String quoteString, String catalogSeparator, String schemaSeparator) {
        return getFullyQualifiedTablePrefix(new StringBuilder(), catalog, schema, quoteString, catalogSeparator, schemaSeparator);
    }

    public final Column getColumnWithName(String name) {
        Column[] columns = getColumns();
        if (columns != null) {
            for (Column column : columns) {
                if (column.getName().equalsIgnoreCase(name)) {
                    return column;
                }
            }
        }
        return null;
    }

    public Column[] getColumnsWithName(String[] columnNames) {
        Column[] columns = new Column[columnNames.length];
        int index = 0;
        for (String columnName : columnNames) {
            columns[index++] = getColumnWithName(columnName);
        }
        return columns;
    }

    public boolean doesIndexContainOnlyPrimaryKeyColumns(IIndex index) {
        IndexColumn[] columns = index.getColumns();
        if (columns != null) {
            for (IndexColumn indexColumn : columns) {
                Column column = getColumnWithName(indexColumn.getName());
                if (column == null || !column.isPrimaryKey()) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
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

    public Column[] getDistributionKeyColumns() {
        if (columns != null) {
            List<Column> selectedColumns = new ArrayList<Column>();
            for (Column column : columns) {
                if (column.isDistributionKey()) {
                    selectedColumns.add(column);
                }
            }
            return selectedColumns.toArray(new Column[selectedColumns.size()]);
        } else {
            return new Column[0];
        }
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

    public static Column[] orderColumns(String[] columnNames, Table table, boolean addMissingColumns) {
        Column[] unorderedColumns = table.getColumns();
        Column[] orderedColumns = new Column[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            String name = columnNames[i];
            for (Column column : unorderedColumns) {
                if (column != null && column.getName().equals(name)) {
                    orderedColumns[i] = column;
                    break;
                }
            }
            if (orderedColumns[i] == null) {
                for (Column column : unorderedColumns) {
                    if (column != null && column.getName().equalsIgnoreCase(name)) {
                        orderedColumns[i] = column;
                        break;
                    }
                }
            }
            if (orderedColumns[i] == null) {
                if (!addMissingColumns) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not find column with the name of {} on table {}.", name, table.toVerboseString());
                    }
                } else {
                    Column newColumn = new Column(name);
                    orderedColumns[i] = newColumn;
                    if (log.isDebugEnabled()) {
                        log.debug("Could not find column with the name of {} on table {}. Added this column to the list of columns.",
                                name, table.toVerboseString());
                    }
                }
            }
        }
        return orderedColumns;
    }

    public String getOldCatalog() {
        return oldCatalog;
    }

    public void setOldCatalog(String oldCatalog) {
        this.oldCatalog = oldCatalog;
    }

    public String getOldSchema() {
        return oldSchema;
    }

    public void setOldSchema(String oldSchema) {
        this.oldSchema = oldSchema;
    }

    /**
     * @return the isAccessControlled
     */
    public boolean isAccessControlled() {
        return isAccessControlled;
    }

    /**
     * @param isAccessControlled
     *            the isAccessControlled to set
     */
    public void setAccessControlled(boolean isAccessControlled) {
        this.isAccessControlled = isAccessControlled;
    }

    public Table copy() {
        try {
            return (Table) this.clone();
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Creates a copy of this table and orders the columns according to the supplied array of column names.
     * 
     * @param orderedColumnNames
     *            An array of column names whose order determines the order of the returned table's columns
     * @param pkColumnNames
     *            An array of the names of the columns that make up the table's primary key
     * @param setPrimaryKeys
     *            Whether to change the returned table's primary key columns based on the supplied array of primary key column names
     * @param addMissingColumns
     *            Whether to add columns to the returned table if they are present in the supplied array of column names but not in the existing table's list of
     *            columns
     * @return A copy of this table
     */
    public Table copyAndFilterColumns(String[] orderedColumnNames, String[] pkColumnNames,
            boolean setPrimaryKeys, boolean addMissingColumns) {
        Table table = copy();
        table.orderColumns(orderedColumnNames, addMissingColumns);
        Set<String> columnNameSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        columnNameSet.addAll(Arrays.asList(orderedColumnNames));
        List<IIndex> indices = new ArrayList<IIndex>();
        for (IIndex index : table.getIndices()) {
            boolean keepIndex = true;
            for (IndexColumn columnInIndex : index.getColumns()) {
                if (columnInIndex == null || columnInIndex.getName() == null) {
                    keepIndex = false;
                    break;
                } else if (!columnNameSet.contains(columnInIndex.getName())) {
                    Set<String> functionalIndexColumnNameSet = parseFunctionalIndexColumnName(columnInIndex.getName());
                    if (functionalIndexColumnNameSet.isEmpty()) {
                        keepIndex = false;
                        break;
                    }
                    for (String functionalIndexColumnName : functionalIndexColumnNameSet) {
                        if (!columnNameSet.contains(functionalIndexColumnName)) {
                            keepIndex = false;
                            break;
                        }
                    }
                }
            }
            if (keepIndex) {
                indices.add(index);
            }
        }
        table.removeAllIndices();
        table.addIndices(indices);
        if (setPrimaryKeys && columns != null) {
            for (Column column : table.columns) {
                if (column != null) {
                    column.setPrimaryKey(false);
                }
            }
            if (pkColumnNames != null) {
                for (Column column : table.columns) {
                    if (column != null) {
                        for (String pkColumnName : pkColumnNames) {
                            if (column.getName().equalsIgnoreCase(pkColumnName)) {
                                boolean required = column.isRequired();
                                column.setPrimaryKey(true);
                                column.setRequired(required);
                            }
                        }
                    }
                }
            }
            if (table.getForeignKeys() != null && table.getForeignKeys().length > 0) {
                List<ForeignKey> foreignKeys = new ArrayList<ForeignKey>();
                Set<String> columnsSet = new HashSet<String>(Arrays.asList(orderedColumnNames));
                for (ForeignKey fk : table.getForeignKeys()) {
                    boolean addFk = true;
                    for (Reference ref : fk.getReferences()) {
                        if (ref != null && ref.getLocalColumnName() != null && !columnsSet.contains(ref.getLocalColumnName())) {
                            addFk = false;
                        }
                    }
                    if (addFk) {
                        foreignKeys.add(fk);
                    }
                }
                table.removeAllForeignKeys();
                table.addForeignKeys(foreignKeys);
            }
        }
        return table;
    }

    private Set<String> parseFunctionalIndexColumnName(String name) {
        Set<String> nameSet = new HashSet<String>();
        if (name.contains(")::")) {
            // Parses PostgreSQL functional indexes
            String[] splitName = StringUtils.split(name, ")::");
            for (int i = 0; i < splitName.length - 1; i++) {
                String s = splitName[i];
                int lastParen = s.lastIndexOf("(");
                if (lastParen != -1) {
                    nameSet.add(s.substring(lastParen + 1));
                }
            }
        } else if (StringUtils.countMatches(name, "\"") >= 2) {
            // Parses Oracle function-based indexes
            for (String columnName : StringUtils.substringsBetween(name, "\"", "\"")) {
                nameSet.add(columnName);
            }
        }
        return nameSet;
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
        Column[] columns = getPrimaryKeyColumns();
        String[] columnNames = new String[columns.length];
        int i = 0;
        for (Column col : columns) {
            columnNames[i++] = col.getName();
        }
        return columnNames;
    }

    public int calculateTableHashcode() {
        return calculateTableHashcode(true);
    }

    public int calculateTableLiteHashcode() {
        return calculateTableHashcode(false);
    }

    protected int calculateTableHashcode(boolean includeTypes) {
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

    public static boolean areAllColumnsPrimaryKeys(Column[] columns) {
        boolean allPks = true;
        if (columns != null) {
            for (Column column : columns) {
                allPks &= column.isPrimaryKey();
            }
        }
        return allPks;
    }

    public static Table buildTable(String tableName, String[] keyNames, String[] columnNames) {
        Table table = new Table();
        table.setName(tableName);
        for (int i = 0; i < columnNames.length; i++) {
            table.addColumn(new Column(columnNames[i]));
        }
        for (int i = 0; i < keyNames.length; i++) {
            table.getColumnWithName(keyNames[i]).setPrimaryKey(true);
        }
        return table;
    }

    public static String getCommaDeliminatedColumns(Column[] cols) {
        StringBuilder columns = new StringBuilder();
        if (cols != null && cols.length > 0) {
            for (Column column : cols) {
                columns.append(escapeColumnNameForCsv(column.getName()));
                columns.append(",");
            }
            columns.replace(columns.length() - 1, columns.length(), "");
            return columns.toString();
        } else {
            return " ";
        }
    }

    public static String[] getArrayColumns(Column[] cols) {
        if (cols != null) {
            String[] columns = new String[cols.length];
            for (int i = 0; i < cols.length; i++) {
                columns[i] = cols[i].getName();
            }
            return columns;
        } else {
            return null;
        }
    }

    public void setPrimaryKeyConstraintName(String primaryKeyConstraintName) {
        this.primaryKeyConstraintName = primaryKeyConstraintName;
    }

    public String getPrimaryKeyConstraintName() {
        return primaryKeyConstraintName;
    }

    public boolean containsJdbcTypes() {
        Column[] columns = getColumns();
        if (columns != null && columns.length > 0) {
            for (Column column : columns) {
                if (!column.containsJdbcTypes()) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public void copyColumnTypesFrom(Table table) {
        if (table != null) {
            if (columns != null) {
                for (Column column : columns) {
                    Column sourceColumn = table.getColumnWithName(column.getName());
                    if (sourceColumn != null) {
                        column.setJdbcTypeCode(sourceColumn.getJdbcTypeCode());
                        column.setJdbcTypeName(sourceColumn.getJdbcTypeName());
                        column.setMappedTypeCode(sourceColumn.getMappedTypeCode());
                        column.setMappedType(sourceColumn.getMappedType());
                    }
                }
            }
        }
    }

    @Override
    public int compareTo(Table o) {
        return this.getFullyQualifiedTableName().compareTo(o.getFullyQualifiedTableName());
    }

    public static String escapeColumnNameForCsv(String columnName) {
        if (columnName != null &&
                (columnName.indexOf('\\') != -1
                        || columnName.indexOf(',') != -1
                        || columnName.indexOf('"') != -1)) {
            columnName = columnName.replace("\\", "\\\\");
            columnName = columnName.replace("\"", "\\\"");
            return "\"" + columnName + "\"";
        } else {
            return columnName;
        }
    }

    public CompressionTypes getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(CompressionTypes compressionType) {
        this.compressionType = compressionType;
    }

    public boolean isMadeAllColumnsPrimaryKey() {
        return madeAllColumnsPrimaryKey;
    }

    public void setMadeAllColumnsPrimaryKey(boolean madeAllColumnsPrimaryKey) {
        this.madeAllColumnsPrimaryKey = madeAllColumnsPrimaryKey;
    }

    public int getTriggerCount() {
        return triggers.size();
    }

    public Trigger getTrigger(int i) {
        return triggers.get(i);
    }

    public Trigger[] getTriggers() {
        return triggers.toArray(new Trigger[triggers.size()]);
    }

    public List<Trigger> getTriggersAsList() {
        return new ArrayList<Trigger>(this.triggers);
    }

    public final void addTrigger(Trigger trigger) {
        if (trigger != null) {
            triggers.add(trigger);
        }
    }

    public void addTrigger(int idx, Trigger trigger) {
        if (trigger != null) {
            triggers.add(idx, trigger);
        }
    }

    public void addTrigger(Trigger previousTrigger, Trigger trigger) {
        if (trigger != null) {
            if (previousTrigger == null) {
                triggers.add(0, trigger);
            } else {
                triggers.add(triggers.indexOf(previousTrigger), trigger);
            }
        }
    }

    public void addTriggers(Collection<Trigger> triggers) {
        for (Iterator<Trigger> it = triggers.iterator(); it.hasNext();) {
            addTrigger(it.next());
        }
    }

    public Trigger findTrigger(String name, boolean caseSensitive) {
        for (Iterator<Trigger> it = triggers.iterator(); it.hasNext();) {
            Trigger trigger = it.next();
            if (caseSensitive) {
                if (trigger.getName().equals(name)) {
                    return trigger;
                }
            } else {
                if (trigger.getName().equalsIgnoreCase(name)) {
                    return trigger;
                }
            }
        }
        return null;
    }

    public void removeTrigger(Trigger trigger) {
        if (trigger != null) {
            triggers.remove(trigger);
        }
    }

    static class ColumnPkSequenceComparator implements Comparator<Column> {
        @Override
        public int compare(Column o1, Column o2) {
            if (o1 != null && o2 != null) {
                return Integer.compare(o1.getPrimaryKeySequence(), o2.getPrimaryKeySequence());
            } else if (o1 == null && o2 != null) {
                return -1;
            } else if (o1 != null && o2 == null) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * Reports table's mode for write-ahead (transaction) log.
     * 
     * @return true, if table is being logged
     */
    public boolean getLogging() {
        return this.logging;
    }

    public void setLogging(boolean value) {
        this.logging = value;
    }
}
