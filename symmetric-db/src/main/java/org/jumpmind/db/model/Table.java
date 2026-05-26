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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Represents a table in the database model.
 */
public class Table extends Relation {
    private static final long serialVersionUID = 1L;
    private String oldCatalog = null;
    private String oldSchema = null;
    private boolean isAccessControlled;
    private ArrayList<ForeignKey> foreignKeys = new ArrayList<>();
    private ArrayList<IIndex> indices = new ArrayList<>();
    private ArrayList<ForeignKey> exportedForeignKeys = new ArrayList<>();
    private String primaryKeyConstraintName;
    private CompressionTypes compressionType = CompressionTypes.NONE;
    private boolean logging = true;
    private ArrayList<Trigger> triggers = new ArrayList<>();

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
        super(catalog, schema, tableName);
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

    @Override
    public void setCatalog(String catalog) {
        for (ForeignKey fk : getForeignKeys()) {
            if (fk.getForeignTableCatalog() != null && fk.getForeignTableCatalog().equals(this.catalog)) {
                fk.setForeignTableCatalog(catalog);
            }
        }
        this.oldCatalog = this.catalog != null ? this.catalog : catalog;
        super.setCatalog(catalog);
    }

    @Override
    public void setSchema(String schema) {
        for (ForeignKey fk : getForeignKeys()) {
            if (fk.getForeignTableSchema() != null && fk.getForeignTableSchema().equals(this.schema)) {
                fk.setForeignTableSchema(schema);
            }
        }
        this.oldSchema = this.schema != null ? this.schema : schema;
        super.setSchema(schema);
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

    public int getForeignKeyCount() {
        return foreignKeys.size();
    }

    public ForeignKey getForeignKey(int idx) {
        return foreignKeys.get(idx);
    }

    public ForeignKey[] getForeignKeys() {
        return foreignKeys.toArray(new ForeignKey[foreignKeys.size()]);
    }

    public void addForeignKey(ForeignKey foreignKey) {
        if (foreignKey != null) {
            foreignKeys.add(foreignKey);
        }
    }

    public void addForeignKey(int idx, ForeignKey foreignKey) {
        if (foreignKey != null) {
            foreignKeys.add(idx, foreignKey);
        }
    }

    public void addForeignKeys(Collection<ForeignKey> foreignKeys) {
        for (Iterator<ForeignKey> it = foreignKeys.iterator(); it.hasNext();) {
            addForeignKey(it.next());
        }
    }

    public void removeForeignKey(ForeignKey foreignKey) {
        if (foreignKey != null) {
            foreignKeys.remove(foreignKey);
        }
    }

    public void removeForeignKey(int idx) {
        foreignKeys.remove(idx);
    }

    public int getExportedForeignKeyCount() {
        return exportedForeignKeys.size();
    }

    public ForeignKey getExportedForeignKey(int idx) {
        return exportedForeignKeys.get(idx);
    }

    public ForeignKey[] getExportedForeignKeys() {
        return exportedForeignKeys.toArray(new ForeignKey[exportedForeignKeys.size()]);
    }

    public void addExportedForeignKey(ForeignKey foreignKey) {
        if (foreignKey != null) {
            exportedForeignKeys.add(foreignKey);
        }
    }

    public void addExportedForeignKey(int idx, ForeignKey foreignKey) {
        if (foreignKey != null) {
            exportedForeignKeys.add(idx, foreignKey);
        }
    }

    public void addExportedForeignKeys(Collection<ForeignKey> foreignKeys) {
        for (Iterator<ForeignKey> it = foreignKeys.iterator(); it.hasNext();) {
            addExportedForeignKey(it.next());
        }
    }

    public void removeExportedForeignKey(ForeignKey foreignKey) {
        if (foreignKey != null) {
            exportedForeignKeys.remove(foreignKey);
        }
    }

    public void removeExportedForeignKey(int idx) {
        exportedForeignKeys.remove(idx);
    }

    public int getIndexCount() {
        return indices.size();
    }

    public IIndex getIndex(int idx) {
        return indices.get(idx);
    }

    public void addIndex(IIndex index) {
        if (index != null) {
            indices.add(index);
        }
    }

    public void addIndex(int idx, IIndex index) {
        if (index != null) {
            indices.add(idx, index);
        }
    }

    public void addIndices(Collection<IIndex> indices) {
        for (Iterator<IIndex> it = indices.iterator(); it.hasNext();) {
            addIndex(it.next());
        }
    }

    public IIndex[] getIndices() {
        return indices.toArray(new IIndex[indices.size()]);
    }

    public IIndex[] getNonUniqueIndices() {
        if (indices != null) {
            List<IIndex> nonunique = new ArrayList<>();
            for (IIndex index : indices) {
                if (!index.isUnique()) {
                    nonunique.add(index);
                }
            }
            return nonunique.toArray(new IIndex[nonunique.size()]);
        }
        return new IIndex[0];
    }

    public IIndex[] getUniqueIndices() {
        if (indices != null) {
            List<IIndex> unique = new ArrayList<>();
            for (IIndex index : indices) {
                if (index.isUnique()) {
                    unique.add(index);
                }
            }
            return unique.toArray(new IIndex[unique.size()]);
        }
        return new IIndex[0];
    }

    public void removeIndex(IIndex index) {
        if (index != null) {
            indices.remove(index);
        }
    }

    public void removeIndex(int idx) {
        indices.remove(idx);
    }

    public IIndex findIndex(String name) {
        return findIndex(name, false);
    }

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

    public ForeignKey findForeignKey(ForeignKey key) {
        for (int idx = 0; idx < getForeignKeyCount(); idx++) {
            ForeignKey fk = getForeignKey(idx);
            if (fk.equals(key)) {
                return fk;
            }
        }
        return null;
    }

    public ForeignKey findForeignKey(ForeignKey key, boolean caseSensitive) {
        for (int idx = 0; idx < getForeignKeyCount(); idx++) {
            ForeignKey fk = getForeignKey(idx);
            if ((caseSensitive && fk.equals(key)) || (!caseSensitive && fk.equalsIgnoreCase(key))) {
                return fk;
            }
        }
        return null;
    }

    public ForeignKey getSelfReferencingForeignKey() {
        for (int idx = 0; idx < getForeignKeyCount(); idx++) {
            ForeignKey fk = getForeignKey(idx);
            if (this.getName().equalsIgnoreCase(fk.getForeignTableName())) {
                return fk;
            }
        }
        return null;
    }

    public boolean doesIndexContainOnlyPrimaryKeyColumns(IIndex index) {
        IndexColumn[] indexColumns = index.getColumns();
        if (indexColumns != null) {
            for (IndexColumn indexColumn : indexColumns) {
                Column column = getColumnWithName(indexColumn.getName());
                if (column == null || !column.isPrimaryKey()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

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
        result.fullyQualifiedName = null;
        result.fullyQualifiedNameLowerCase = null;
        result.nameLowerCase = null;
        result.columns = new ArrayList<>(columns.size());
        for (Column col : columns) {
            if (col != null) {
                result.columns.add((Column) col.clone());
            }
        }
        result.lobColumns = null;
        result.foreignKeys = new ArrayList<>(foreignKeys.size());
        for (ForeignKey fk : foreignKeys) {
            if (fk != null) {
                result.foreignKeys.add((ForeignKey) fk.clone());
            }
        }
        result.indices = new ArrayList<>(indices.size());
        for (IIndex i : indices) {
            if (i != null) {
                result.indices.add((IIndex) i.clone());
            }
        }
        result.triggers = new ArrayList<>(triggers.size());
        for (Trigger t : triggers) {
            if (t != null) {
                result.triggers.add((Trigger) t.clone());
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Table other) {
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

    @Override
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

    public boolean isAccessControlled() {
        return isAccessControlled;
    }

    public void setAccessControlled(boolean isAccessControlled) {
        this.isAccessControlled = isAccessControlled;
    }

    @Override
    public Table copy() {
        try {
            return (Table) this.clone();
        } catch (CloneNotSupportedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Table copyAndFilterColumns(String[] orderedColumnNames, String[] pkColumnNames,
            boolean setPrimaryKeys, boolean addMissingColumns) {
        Table table = copy();
        table.orderColumns(orderedColumnNames, addMissingColumns);
        Set<String> columnNameSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        columnNameSet.addAll(Arrays.asList(orderedColumnNames));
        List<IIndex> filteredIndices = new ArrayList<>();
        for (IIndex index : table.getIndices()) {
            boolean keepIndex = true;
            for (IndexColumn columnInIndex : index.getColumns()) {
                if (columnInIndex == null || columnInIndex.getName() == null) {
                    keepIndex = false;
                    break;
                } else if (!columnNameSet.contains(columnInIndex.getName())) {
                    Set<String> functionalIndexColumnNames = parseFunctionalIndexColumnName(columnInIndex.getName());
                    if (functionalIndexColumnNames.isEmpty()) {
                        keepIndex = false;
                        break;
                    }
                    for (String functionalIndexColumnName : functionalIndexColumnNames) {
                        if (!columnNameSet.contains(functionalIndexColumnName)) {
                            keepIndex = false;
                            break;
                        }
                    }
                }
            }
            if (keepIndex) {
                filteredIndices.add(index);
            }
        }
        table.removeAllIndices();
        table.addIndices(filteredIndices);
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
                List<ForeignKey> filteredForeignKeys = new ArrayList<>();
                Set<String> columnsSet = new HashSet<>(Arrays.asList(orderedColumnNames));
                for (ForeignKey fk : table.getForeignKeys()) {
                    boolean addFk = true;
                    for (Reference ref : fk.getReferences()) {
                        if (ref != null && ref.getLocalColumnName() != null && !columnsSet.contains(ref.getLocalColumnName())) {
                            addFk = false;
                        }
                    }
                    if (addFk) {
                        filteredForeignKeys.add(fk);
                    }
                }
                table.removeAllForeignKeys();
                table.addForeignKeys(filteredForeignKeys);
            }
        }
        return table;
    }

    private Set<String> parseFunctionalIndexColumnName(String indexName) {
        Set<String> nameSet = new HashSet<>();
        if (indexName.contains(")::")) {
            String[] splitName = StringUtils.split(indexName, ")::");
            for (int i = 0; i < splitName.length - 1; i++) {
                String s = splitName[i];
                int lastParen = s.lastIndexOf("(");
                if (lastParen != -1) {
                    nameSet.add(s.substring(lastParen + 1));
                }
            }
        } else if (StringUtils.countMatches(indexName, "\"") >= 2) {
            for (String columnName : StringUtils.substringsBetween(indexName, "\"", "\"")) {
                nameSet.add(columnName);
            }
        }
        return nameSet;
    }

    public void setPrimaryKeyConstraintName(String primaryKeyConstraintName) {
        this.primaryKeyConstraintName = primaryKeyConstraintName;
    }

    public String getPrimaryKeyConstraintName() {
        return primaryKeyConstraintName;
    }

    public CompressionTypes getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(CompressionTypes compressionType) {
        this.compressionType = compressionType;
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
        return new ArrayList<>(triggers);
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

    public boolean getLogging() {
        return logging;
    }

    public void setLogging(boolean value) {
        this.logging = value;
    }

    public static Table buildTable(String tableName, String[] keyNames, String[] columnNames) {
        Table table = new Table();
        table.setName(tableName);
        for (String columnName : columnNames) {
            table.addColumn(new Column(columnName));
        }
        for (String keyName : keyNames) {
            table.getColumnWithName(keyName).setPrimaryKey(true);
        }
        return table;
    }
}
