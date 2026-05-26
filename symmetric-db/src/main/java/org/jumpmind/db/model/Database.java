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
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the database model, ie. the relations in the database. It also contains the corresponding dyna classes for creating dyna beans for the objects
 * stored in the relations.
 */
public class Database implements Serializable, Cloneable {
    private static final Logger log = LoggerFactory.getLogger(Database.class);
    /** Unique ID for serialization purposes. */
    private static final long serialVersionUID = 1L;
    /** The name of the database model. */
    private String name;
    private String catalog;
    private String schema;
    /** The method for generating primary keys (currently ignored). */
    private String idMethod;
    /** The version of the model. */
    private String version;
    /** The tables. */
    private ArrayList<Table> tables = new ArrayList<>();
    private Map<String, Integer> tableIndexCache = new HashMap<>();
    private ArrayList<View> views = new ArrayList<>();

    /**
     * Implements modified topological sort of relations (@see <a href="http://en.wikipedia.org/wiki/Topological_sorting">topological sorting</a>). The
     * 'depth-first search' is implemented in order to detect and ignore cycles.
     * 
     * @param relations
     *            List of relations to sort.
     * @param allRelations
     *            List of relations in database, if null the relations param will be used.
     * @param tablePrefix
     *            The SymmetricDS runtime table prefix.
     * @param dependencyMap
     *            Map to separate dependent relations into groups. The key will be an integer based counter (1,2...) to identify the grouping. The value will
     *            contain all the relations that are dependent on each other but independent for other relations in other groups. Used to identify which
     *            relations could be placed in a specific group. This should be passed in empty so that it can be used by reference after the method finishes.
     * @param missingDependencyMap
     *            This is a used for any relations that are missing from the relations param that should be included in synchronization to avoid FK issues.
     * @return List of relations in their dependency order - if relation A has a foreign key for relation B then relation B will precede relation A in the list.
     */
    public static List<Relation> sortByForeignKeys(List<Relation> relations, Map<String, Relation> allRelations,
            Map<Integer, Set<Relation>> dependencyMap, Map<Relation, Set<String>> missingDependencyMap) {
        if (allRelations == null) {
            allRelations = new HashMap<>();
            for (Relation r : relations) {
                allRelations.put(r.getName(), r);
            }
        }
        if (dependencyMap == null) {
            dependencyMap = new HashMap<>();
        }
        if (missingDependencyMap == null) {
            missingDependencyMap = new HashMap<>();
        }
        Set<Relation> resolved = new HashSet<>();
        Set<Relation> temporary = new HashSet<>();
        List<Relation> finalList = new ArrayList<>();
        MutableInt depth = new MutableInt(1);
        MutableInt position = new MutableInt(1);
        MutableInt parentPosition = new MutableInt(-1);
        Map<Relation, Integer> resolvedPosition = new HashMap<>();
        for (Relation r : relations) {
            if (r != null) {
                depth.setValue(1);
                parentPosition.setValue(-1);
                resolveForeignKeyOrder(r, allRelations, resolved, temporary, finalList, null, missingDependencyMap,
                        dependencyMap, depth, position, resolvedPosition, parentPosition);
            }
        }
        Collections.reverse(finalList);
        return finalList;
    }

    public static void logMissingDependentTableNames(List<Relation> relations) {
        Map<String, List<String>> missingTablesByChildTable = findMissingDependentTableNames(relations);
        for (String childTableName : missingTablesByChildTable.keySet()) {
            List<String> missingTables = missingTablesByChildTable.get(childTableName);
            StringBuilder dependentTables = new StringBuilder();
            for (String missingTableName : missingTables) {
                if (dependentTables.length() > 0) {
                    dependentTables.append(", ");
                }
                dependentTables.append(missingTableName);
            }
            log.info("Unable to resolve foreign keys for table " + childTableName + " because the following dependent tables were not included ["
                    + dependentTables.toString() + "].");
        }
    }

    public static Map<String, List<String>> findMissingDependentTableNames(List<Relation> relations) {
        Map<String, List<String>> missingTablesByChildTable = new HashMap<String, List<String>>();
        Map<String, Relation> allRelations = new HashMap<String, Relation>();
        for (Relation r : relations) {
            allRelations.put(r.getName(), r);
        }
        for (Relation relation : relations) {
            if (relation instanceof Table table) {
                List<String> missingTables = missingTablesByChildTable.get(table.getName());
                for (ForeignKey fk : table.getForeignKeys()) {
                    if (allRelations.get(fk.getForeignTableName()) == null) {
                        if (missingTables == null) {
                            missingTables = new ArrayList<String>();
                            missingTablesByChildTable.put(table.getName(), missingTables);
                        }
                        missingTables.add(fk.getForeignTableName());
                    }
                }
            }
        }
        return missingTablesByChildTable;
    }

    public static void resolveForeignKeyOrder(Relation r, Map<String, Relation> allRelations, Set<Relation> resolved, Set<Relation> temporary,
            List<Relation> finalList, Relation parentRelation, Map<Relation, Set<String>> missingDependencyMap,
            Map<Integer, Set<Relation>> dependencyMap, MutableInt depth, MutableInt position,
            Map<Relation, Integer> resolvedPosition, MutableInt parentPosition) {
        if (resolved.contains(r)) {
            parentPosition.setValue(resolvedPosition.get(r));
            return;
        }
        if (!temporary.contains(r) && !resolved.contains(r)) {
            Set<Integer> parentRelationsChannels = new HashSet<Integer>();
            if (r == null) {
                if (parentRelation instanceof Table parentTable) {
                    for (ForeignKey fk : parentTable.getForeignKeys()) {
                        if (allRelations.get(fk.getForeignTableName()) == null) {
                            if (missingDependencyMap.get(parentTable) == null) {
                                missingDependencyMap.put(parentTable, new HashSet<String>());
                            }
                            missingDependencyMap.get(parentTable).add(fk.getForeignTableName());
                        }
                    }
                }
            } else {
                temporary.add(r);
                ForeignKey[] foreignKeys = r instanceof Table table ? table.getForeignKeys() : new ForeignKey[0];
                for (ForeignKey fk : foreignKeys) {
                    Relation fkRelation = allRelations.get(fk.getForeignTableName());
                    if (fkRelation != r) {
                        depth.increment();
                        resolveForeignKeyOrder(fkRelation, allRelations, resolved, temporary, finalList, r, missingDependencyMap,
                                dependencyMap, depth, position, resolvedPosition, parentPosition);
                        Integer resolvedParentRelationChannel = resolvedPosition.get(fkRelation);
                        if (resolvedParentRelationChannel != null) {
                            parentRelationsChannels.add(resolvedParentRelationChannel);
                        }
                    }
                }
            }
            if (r != null) {
                if (parentPosition.intValue() > 0) {
                    if (dependencyMap.get(parentPosition.intValue()) == null) {
                        dependencyMap.put(parentPosition.intValue(), new HashSet<>());
                    }
                    if (parentRelationsChannels.size() > 1) {
                        parentPosition.setValue(mergeChannels(parentRelationsChannels, dependencyMap, resolvedPosition));
                    }
                    dependencyMap.get(parentPosition.intValue()).add(r);
                } else {
                    if (dependencyMap.get(position.intValue()) == null) {
                        dependencyMap.put(position.intValue(), new HashSet<>());
                    }
                    dependencyMap.get(position.intValue()).add(r);
                }
                resolved.add(r);
                resolvedPosition.put(r, parentPosition.intValue() > 0 ? parentPosition.intValue() : position.intValue());
                finalList.add(0, r);
                if (depth.intValue() == 1) {
                    if (parentPosition.intValue() < 0) {
                        position.increment();
                    }
                } else {
                    depth.decrement();
                }
            }
        }
    }

    protected static Integer mergeChannels(Set<Integer> parentRelationsChannels, Map<Integer, Set<Relation>> dependencyMap,
            Map<Relation, Integer> resolvedPosition) {
        Iterator<Integer> i = parentRelationsChannels.iterator();
        Set<Relation> mergedRelations = new HashSet<>();
        Integer minChannelId = null;
        Set<Integer> unusedChannels = new HashSet<>();
        while (i.hasNext()) {
            Integer channelToMerge = (Integer) i.next();
            if (dependencyMap.get(channelToMerge) != null) {
                mergedRelations.addAll(dependencyMap.get(channelToMerge));
                if (minChannelId == null) {
                    minChannelId = channelToMerge;
                } else if (channelToMerge < minChannelId) {
                    unusedChannels.add(minChannelId);
                    minChannelId = channelToMerge;
                } else {
                    unusedChannels.add(channelToMerge);
                }
            }
        }
        dependencyMap.put(minChannelId, mergedRelations);
        for (Relation r : mergedRelations) {
            resolvedPosition.put(r, minChannelId);
        }
        for (Integer unusedChannel : unusedChannels) {
            dependencyMap.remove(unusedChannel);
        }
        return minChannelId;
    }

    public static String printTables(List<Relation> relations) {
        StringBuilder sb = new StringBuilder();
        for (Relation r : relations) {
            sb.append(r.getName() + ",");
        }
        return sb.toString();
    }

    public static Relation[] sortByForeignKeys(Relation... relations) {
        if (relations != null) {
            List<Relation> list = new ArrayList<>(relations.length);
            for (Relation relation : relations) {
                list.add(relation);
            }
            list = sortByForeignKeys(list, null, null, null);
            relations = list.toArray(new Relation[list.size()]);
        }
        return relations;
    }

    public static List<Relation> sortByForeignKeys(List<Relation> relations) {
        return sortByForeignKeys(relations, null, null, null);
    }

    /**
     * Adds all tables from the other database to this database. Note that the other database is not changed.
     * 
     * @param otherDb
     *            The other database model
     */
    public void mergeWith(Database otherDb) throws ModelException {
        for (Iterator<Table> it = otherDb.tables.iterator(); it.hasNext();) {
            Table table = (Table) it.next();
            if (findTable(table.getName()) != null) {
                // TODO: It might make more sense to log a warning and overwrite
                // the table (or merge them) ?
                throw new ModelException("Cannot merge the models because table " + table.getName()
                        + " already defined in this model");
            }
            try {
                addTable((Table) table.clone());
            } catch (CloneNotSupportedException ex) {
                // won't happen
            }
        }
    }

    /**
     * Returns the name of this database model.
     * 
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this database model.
     * 
     * @param name
     *            The name
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    /**
     * Returns the version of this database model.
     * 
     * @return The version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the version of this database model.
     * 
     * @param version
     *            The version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Returns the method for generating primary key values.
     * 
     * @return The method
     */
    public String getIdMethod() {
        return idMethod;
    }

    /**
     * Sets the method for generating primary key values. Note that this value is ignored by DdlUtils and only for compatibility with Torque.
     * 
     * @param idMethod
     *            The method
     */
    public void setIdMethod(String idMethod) {
        this.idMethod = idMethod;
    }

    /**
     * Returns the number of tables in this model.
     * 
     * @return The number of tables
     */
    public int getTableCount() {
        return tables.size();
    }

    /**
     * Returns the tables in this model.
     * 
     * @return The tables
     */
    public Table[] getTables() {
        return (Table[]) tables.toArray(new Table[tables.size()]);
    }

    /**
     * Returns the table at the specified position.
     * 
     * @param idx
     *            The index of the table
     * @return The table
     */
    public Table getTable(int idx) {
        return (Table) tables.get(idx);
    }

    /**
     * Adds a table.
     * 
     * @param table
     *            The table to add
     */
    public void addTable(Table table) {
        if (table != null) {
            tables.add(table);
        }
    }

    /**
     * Adds a table at the specified position.
     * 
     * @param idx
     *            The index where to insert the table
     * @param table
     *            The table to add
     */
    public void addTable(int idx, Table table) {
        if (table != null) {
            tables.add(idx, table);
        }
    }

    /**
     * Adds the given tables.
     * 
     * @param tables
     *            The tables to add
     */
    public void addTables(Collection<Table> tables) {
        for (Iterator<Table> it = tables.iterator(); it.hasNext();) {
            addTable((Table) it.next());
        }
    }

    public void addTables(Table[] tables) {
        for (Table table : tables) {
            addTable(table);
        }
    }

    /**
     * Removes the given table.
     * 
     * @param table
     *            The table to remove
     */
    public void removeTable(Table table) {
        if (table != null) {
            tables.remove(table);
        }
    }

    /**
     * Removes the indicated table.
     * 
     * @param idx
     *            The index of the table to remove
     */
    public void removeTable(int idx) {
        tables.remove(idx);
    }

    public int getViewCount() {
        return views.size();
    }

    public View[] getViews() {
        return views.toArray(new View[views.size()]);
    }

    public void addView(View view) {
        if (view != null) {
            views.add(view);
        }
    }

    public void removeView(View view) {
        if (view != null) {
            views.remove(view);
        }
    }

    public View findView(String name) {
        return findView(name, false);
    }

    public View findView(String name, boolean caseSensitive) {
        for (View view : views) {
            if ((caseSensitive ? Strings.CS : Strings.CI).equals(view.getName(), name)) {
                return view;
            }
        }
        return null;
    }
    // Helper methods

    /**
     * Initializes the model by establishing the relationships between elements in this model encoded eg. in foreign keys etc. Also checks that the model
     * elements are valid (table and columns have a name, foreign keys rference existing tables etc.)
     */
    public void initialize() throws ModelException {
        // we have to setup
        // * target tables in foreign keys
        // * columns in foreign key references
        // * columns in indices
        // * columns in uniques
        HashSet<String> namesOfProcessedTables = new HashSet<String>();
        HashSet<String> namesOfProcessedColumns = new HashSet<String>();
        HashSet<String> namesOfProcessedFks = new HashSet<String>();
        HashSet<String> namesOfProcessedIndices = new HashSet<String>();
        int tableIdx = 0;
        for (Iterator<Table> tableIt = tables.iterator(); tableIt.hasNext(); tableIdx++) {
            Table curTable = tableIt.next();
            if ((curTable.getName() == null) || (curTable.getName().length() == 0)) {
                throw new ModelException("The table nr. " + tableIdx + " has no name");
            }
            if (namesOfProcessedTables.contains(curTable.getFullyQualifiedName())) {
                throw new ModelException("There are multiple tables with the name "
                        + curTable.getName());
            }
            namesOfProcessedTables.add(curTable.getFullyQualifiedName());
            namesOfProcessedColumns.clear();
            namesOfProcessedFks.clear();
            namesOfProcessedIndices.clear();
            for (int idx = 0; idx < curTable.getColumnCount(); idx++) {
                Column column = curTable.getColumn(idx);
                if ((column.getName() == null) || (column.getName().length() == 0)) {
                    throw new ModelException("The column nr. " + idx + " in table "
                            + curTable.getName() + " has no name");
                }
                if (namesOfProcessedColumns.contains(column.getName())) {
                    throw new ModelException("There are multiple column with the name "
                            + column.getName() + " in the table " + curTable.getName());
                }
                namesOfProcessedColumns.add(column.getName());
                if ((column.getMappedType() == null) || (column.getMappedType().length() == 0)) {
                    throw new ModelException("The column nr. " + idx + " in table "
                            + curTable.getName() + " has no type");
                }
                if ((column.getMappedTypeCode() == Types.OTHER)
                        && !"OTHER".equalsIgnoreCase(column.getMappedType())) {
                    throw new ModelException("The column nr. " + idx + " in table "
                            + curTable.getName() + " has an unknown type " + column.getMappedType());
                }
                namesOfProcessedColumns.add(column.getName());
            }
            for (int idx = 0; idx < curTable.getForeignKeyCount(); idx++) {
                ForeignKey fk = curTable.getForeignKey(idx);
                String fkName = (fk.getName() == null ? "" : fk.getName());
                String fkDesc = (fkName.length() == 0 ? "nr. " + idx : fkName);
                if (fkName.length() > 0) {
                    if (namesOfProcessedFks.contains(fkName)) {
                        throw new ModelException("There are multiple foreign keys in table "
                                + curTable.getName() + " with the name " + fkName);
                    }
                    namesOfProcessedFks.add(fkName);
                }
                if (fk.getForeignTable() == null) {
                    Table targetTable = findTable(fk.getForeignTableName(), true);
                    if (targetTable != null) {
                        fk.setForeignTable(targetTable);
                        fk.setForeignTableCatalog(targetTable.getCatalog());
                        fk.setForeignTableSchema(targetTable.getSchema());
                    } else {
                        log.debug("The foreignkey "
                                + fkDesc
                                + " in table "
                                + curTable.getName()
                                + " references the undefined table "
                                + fk.getForeignTableName()
                                + ".  This could be because the foreign key table was in another schema which is a bug that should be fixed in the future.");
                    }
                }
                if (fk.getForeignTable() != null) {
                    for (int refIdx = 0; refIdx < fk.getReferenceCount(); refIdx++) {
                        Reference ref = fk.getReference(refIdx);
                        if (ref.getLocalColumn() == null) {
                            Column localColumn = curTable
                                    .findColumn(ref.getLocalColumnName(), true);
                            if (localColumn == null) {
                                throw new ModelException("The foreignkey " + fkDesc + " in table "
                                        + curTable.getName()
                                        + " references the undefined local column "
                                        + ref.getLocalColumnName());
                            } else {
                                ref.setLocalColumn(localColumn);
                            }
                        }
                        if (ref.getForeignColumn() == null) {
                            Column foreignColumn = fk.getForeignTable().findColumn(
                                    ref.getForeignColumnName(), true);
                            if (foreignColumn == null) {
                                throw new ModelException("The foreignkey " + fkDesc + " in table "
                                        + curTable.getName()
                                        + " references the undefined local column "
                                        + ref.getForeignColumnName() + " in table "
                                        + fk.getForeignTable().getName());
                            } else {
                                ref.setForeignColumn(foreignColumn);
                            }
                        }
                    }
                }
            }
            for (int idx = 0; idx < curTable.getIndexCount(); idx++) {
                IIndex index = curTable.getIndex(idx);
                String indexName = (index.getName() == null ? "" : index.getName());
                if (indexName.length() > 0) {
                    if (namesOfProcessedIndices.contains(indexName)) {
                        throw new ModelException("There are multiple indices in table "
                                + curTable.getName() + " with the name " + indexName);
                    }
                    namesOfProcessedIndices.add(indexName);
                }
                for (int indexColumnIdx = 0; indexColumnIdx < index.getColumnCount(); indexColumnIdx++) {
                    IndexColumn indexColumn = index.getColumn(indexColumnIdx);
                    Column column = curTable.findColumn(indexColumn.getName(), true);
                    indexColumn.setColumn(column);
                }
            }
        }
    }

    /**
     * Finds the table with the specified name, using case insensitive matching. Note that this method is not called getTable to avoid introspection problems.
     * 
     * @param name
     *            The name of the table to find
     * @return The table or <code>null</code> if there is no such table
     */
    public Table findTable(String name) {
        return findTable(name, false);
    }

    /**
     * Finds the table with the specified name, using case insensitive matching. Note that this method is not called getTable) to avoid introspection problems.
     * 
     * @param name
     *            The name of the table to find
     * @param caseSensitive
     *            Whether case matters for the names
     * @return The table or <code>null</code> if there is no such table
     */
    public Table findTable(String name, boolean caseSensitive) {
        for (Iterator<Table> iter = tables.iterator(); iter.hasNext();) {
            Table table = (Table) iter.next();
            if (caseSensitive) {
                if (table.getName().equals(name)) {
                    return table;
                }
            } else {
                if (table.getName().equalsIgnoreCase(name)) {
                    return table;
                }
            }
        }
        return null;
    }

    /**
     * Catalog & Schema aware finder for ddlutils Database class
     * 
     * 
     * @param catalogName
     * @param schemaName
     * @param tableName
     * @param caseSensitive
     * @return
     */
    public Table findTable(String catalogName, String schemaName, String tableName,
            boolean caseSensitive) {
        String cacheKey = catalogName + "." + schemaName + "." + tableName + "." + caseSensitive;
        Integer tableIndex = tableIndexCache.get(cacheKey);
        if (tableIndex != null) {
            if (tableIndex < getTableCount()) {
                Table table = getTable(tableIndex);
                if (doesMatch(table, catalogName, schemaName, tableName, caseSensitive)) {
                    return table;
                }
            }
        }
        Table[] tables = getTables();
        for (int i = 0; i < tables.length; i++) {
            Table table = tables[i];
            if (doesMatch(table, catalogName, schemaName, tableName, caseSensitive)) {
                tableIndexCache.put(cacheKey, i);
                return table;
            }
        }
        return null;
    }

    private boolean doesMatch(Table table, String catalogName, String schemaName, String tableName,
            boolean caseSensitive) {
        if (caseSensitive) {
            return ((catalogName == null || (catalogName != null && catalogName.equals(table
                    .getCatalog())))
                    && (schemaName == null || (schemaName != null && schemaName.equals(table
                            .getSchema()))) && table.getName().equals(tableName));
        } else {
            return ((catalogName == null || (catalogName != null && catalogName
                    .equalsIgnoreCase(table.getCatalog())))
                    && (schemaName == null || (schemaName != null && schemaName
                            .equalsIgnoreCase(table.getSchema()))) && table.getName()
                                    .equalsIgnoreCase(tableName));
        }
    }

    public Table findTable(String catalogName, String schemaName, String tableName) {
        return findTable(catalogName, schemaName, tableName, false);
    }

    public void resetTableIndexCache() {
        tableIndexCache.clear();
    }

    public void removeAllTablesExcept(String... tableNames) {
        Iterator<Table> tableIterator = this.tables.iterator();
        while (tableIterator.hasNext()) {
            Table table = tableIterator.next();
            boolean foundTable = false;
            for (String tableName : tableNames) {
                if (tableName.equals(table.getName())) {
                    foundTable = true;
                    break;
                }
            }
            if (!foundTable) {
                tableIterator.remove();
            }
        }
    }

    public Database copy() {
        try {
            return (Database) this.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object clone() throws CloneNotSupportedException {
        Database result = (Database) super.clone();
        result.name = name;
        result.catalog = catalog;
        result.schema = schema;
        result.idMethod = idMethod;
        result.version = version;
        result.tables = new ArrayList<Table>(tables.size());
        for (Table table : tables) {
            result.tables.add((Table) table.clone());
        }
        result.views = new ArrayList<View>(views.size());
        for (View view : views) {
            result.views.add((View) view.clone());
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (obj instanceof Database) {
            Database other = (Database) obj;
            // Note that this compares case sensitive
            return new EqualsBuilder().append(name, other.name).append(catalog, other.catalog)
                    .append(schema, other.schema).append(tables, other.tables).append(views, other.views).isEquals();
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(name).append(tables).append(views).toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Database [name=").append(name);
        result.append("; catalog=").append(catalog);
        result.append("; schema=").append(schema);
        result.append("; tableCount=").append(getTableCount());
        result.append("]");
        return result.toString();
    }

    /**
     * Returns a verbose string representation of this database.
     * 
     * @return The string representation
     */
    public String toVerboseString() {
        StringBuilder result = new StringBuilder();
        result.append("Database [");
        result.append(getName());
        result.append("] tables:");
        for (int idx = 0; idx < getTableCount(); idx++) {
            result.append(" ");
            result.append(getTable(idx).toVerboseString());
        }
        return result.toString();
    }
}