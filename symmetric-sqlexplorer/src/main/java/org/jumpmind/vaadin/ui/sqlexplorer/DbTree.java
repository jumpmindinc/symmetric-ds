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
package org.jumpmind.vaadin.ui.sqlexplorer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.function.ValueProvider;

public class DbTree extends TreeGrid<DbTreeNode> {
    public static final String PROPERTY_SCHEMA_NAME = "schemaName";
    public static final String PROPERTY_CATALOG_NAME = "catalogName";
    private static final long serialVersionUID = 1L;
    final Logger log = LoggerFactory.getLogger(getClass());
    public final static String[] TABLE_TYPES = new String[] { "TABLE",
            "SYSTEM TABLE", "SYSTEM VIEW" };
    public static final String NODE_TYPE_DATABASE = "Database";
    public static final String NODE_TYPE_CATALOG = "Catalog";
    public static final String NODE_TYPE_SCHEMA = "Schema";
    public static final String NODE_TYPE_TABLE = "Table";
    public static final String NODE_TYPE_TRIGGER = "Trigger";
    public static final String NODE_TYPE_PLACEHOLDER = "Placeholder";
    IDbProvider databaseProvider;
    ISettingsProvider settingsProvider;
    TreeData<DbTreeNode> treeData;
    TreeDataProvider<DbTreeNode> treeDataProvider;
    Set<DbTreeNode> rootNodes;
    Set<DbTreeNode> expandedNodes;

    public DbTree(IDbProvider databaseProvider, ISettingsProvider settingsProvider) {
        this.databaseProvider = databaseProvider;
        this.settingsProvider = settingsProvider;
        setWidthFull();
        setPartNameGenerator(new DbTreePartNameGenerator());
        addThemeName("dbTree");
        expandedNodes = new LinkedHashSet<DbTreeNode>();
        addComponentHierarchyColumn(new ValueProvider<DbTreeNode, Label>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Label apply(DbTreeNode node) {
                Label label = new Label(node.getName());
                if (node.getIcon() != null) {
                    label.setLeftIcon(node.getIcon());
                }
                return label;
            }
        });
        addExpandListener(event -> {
            Collection<DbTreeNode> items = event.getItems();
            if (!items.isEmpty()) {
                DbTreeNode node = items.iterator().next();
                expandedNodes.add(node);
                if (!node.getType().equals(NODE_TYPE_TRIGGER) && !node.hasChildren()) {
                    loadChildren(node);
                }
                DbTreeNode firstChild = treeData.getChildren(node).get(0);
                if (node.hasChildren() && firstChild.getType().equals(NODE_TYPE_PLACEHOLDER)) {
                    treeData.removeItem(firstChild);
                    for (DbTreeNode child : node.getChildren()) {
                        treeData.addItem(node, child);
                        String childType = child.getType();
                        if (!childType.equals(NODE_TYPE_TRIGGER)
                                && (settingsProvider.get().getProperties().get(Settings.SQL_EXPLORER_SHOW_TRIGGERS, "")
                                        .equals("true") || !childType.equals(NODE_TYPE_TABLE))) {
                            treeData.addItem(child, new DbTreeNode(this, NODE_TYPE_PLACEHOLDER, child));
                        }
                    }
                }
            }
        });
        addCollapseListener(event -> {
            Collection<DbTreeNode> items = event.getItems();
            if (!items.isEmpty()) {
                DbTreeNode node = event.getItems().iterator().next();
                expandedNodes.remove(node);
                for (DbTreeNode child : new ArrayList<DbTreeNode>(treeData.getChildren(node))) {
                    if (expandedNodes.contains(child)) {
                        collapse(child);
                    }
                    deselect(child);
                    treeData.removeItem(child);
                }
                treeData.addItem(node, new DbTreeNode(this, NODE_TYPE_PLACEHOLDER, node));
            }
        });
    }

    public void refresh(boolean fullRefresh) {
        Set<DbTreeNode> selected = getSelectedItems();
        DbTreeNode firstNode = null;
        if (fullRefresh || rootNodes == null) {
            List<IDb> databases = databaseProvider.getDatabases();
            if (treeData == null) {
                treeData = new TreeData<DbTreeNode>();
            } else {
                treeData.clear();
            }
            expandedNodes.clear();
            rootNodes = new LinkedHashSet<DbTreeNode>();
            for (IDb database : databases) {
                DbTreeNode databaseNode = new DbTreeNode(this, database.getName(), NODE_TYPE_DATABASE, VaadinIcon.DATABASE, null);
                treeData.addItem(null, databaseNode);
                treeData.addItem(databaseNode, new DbTreeNode(this, NODE_TYPE_PLACEHOLDER, databaseNode));
                rootNodes.add(databaseNode);
                if (firstNode == null) {
                    firstNode = databaseNode;
                }
            }
        } else {
            treeData.clear();
            for (DbTreeNode databaseNode : rootNodes) {
                treeData.addItem(null, databaseNode);
                treeData.addItem(databaseNode, new DbTreeNode(this, NODE_TYPE_PLACEHOLDER, databaseNode));
            }
        }
        treeDataProvider = new TreeDataProvider<DbTreeNode>(treeData);
        setDataProvider(treeDataProvider);
        expand(expandedNodes);
        if (selected == null || selected.size() == 0) {
            selected = new HashSet<DbTreeNode>();
            selected.add(firstNode);
        }
        for (DbTreeNode node : selected) {
            if (node != null) {
                select(node);
            }
        }
        focus();
    }

    public Set<DbTreeNode> getSelected(String type) {
        HashSet<DbTreeNode> nodes = new HashSet<DbTreeNode>();
        Set<DbTreeNode> selected = getSelectedItems();
        for (DbTreeNode treeNode : selected) {
            if (treeNode.getType().equals(type)) {
                nodes.add(treeNode);
            }
        }
        return nodes;
    }

    public Set<Table> getSelectedTables() {
        Set<Table> tables = new HashSet<Table>();
        for (DbTreeNode treeNode : getSelectedItems()) {
            Table table = treeNode.getTableFor();
            if (table != null) {
                tables.add(table);
            }
        }
        return tables;
    }

    protected IDb getDbForNode(DbTreeNode node) {
        while (node.getParent() != null) {
            node = node.getParent();
        }
        String databaseName = node.getName();
        List<IDb> databases = databaseProvider.getDatabases();
        for (IDb database : databases) {
            if (database.getName().equals(databaseName)) {
                return database;
            }
        }
        return null;
    }

    protected void loadChildren(DbTreeNode treeNode) {
        try {
            IDatabasePlatform platform = getDbForNode(treeNode).getPlatform();
            IDdlReader reader = platform.getDdlReader();
            if (treeNode.getType().equals(NODE_TYPE_DATABASE)) {
                List<String> catalogs = reader.getCatalogNames();
                if (catalogs.size() > 0) {
                    addCatalogNodes(reader, treeNode, platform, catalogs);
                } else {
                    List<String> schemas = reader.getSchemaNames(null);
                    addSchemaNodes(reader, treeNode, platform, schemas);
                }
                if (treeNode.getChildren().size() == 0) {
                    addTableNodes(reader, treeNode, null, null);
                }
            } else if (treeNode.getType().equals(NODE_TYPE_CATALOG)) {
                List<String> schemas = reader.getSchemaNames(treeNode.getName());
                addSchemaNodes(reader, treeNode, platform, schemas);
                if (treeNode.getChildren().size() == 0) {
                    addTableNodes(reader, treeNode, treeNode.getName(), null);
                }
            } else if (treeNode.getType().equals(NODE_TYPE_SCHEMA)) {
                String catalogName = null;
                DbTreeNode parent = treeNode.getParent();
                if (parent != null && parent.getType().equals(NODE_TYPE_CATALOG)) {
                    catalogName = parent.getName();
                }
                addTableNodes(reader, treeNode, catalogName,
                        treeNode.getName());
            } else if (treeNode.getType().equals(NODE_TYPE_TABLE)) {
                String catalogName = null, schemaName = null;
                DbTreeNode parent = treeNode.getParent();
                if (parent != null && parent.getType().equals(NODE_TYPE_SCHEMA)) {
                    schemaName = parent.getName();
                    DbTreeNode grandparent = parent.getParent();
                    if (grandparent != null && grandparent.getType().equals(NODE_TYPE_CATALOG)) {
                        catalogName = grandparent.getName();
                    }
                } else if (parent != null && parent.getType().equals(NODE_TYPE_CATALOG)) {
                    catalogName = parent.getName();
                }
                addTriggerNodes(reader, treeNode, catalogName, schemaName);
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            CommonUiUtils.notifyError(ex.getMessage());
        }
    }

    protected List<DbTreeNode> getTableTreeNodes(IDdlReader reader,
            DbTreeNode parent, String catalogName, String schemaName) {
        List<DbTreeNode> list = new ArrayList<DbTreeNode>();
        List<String> tables = reader.getTableNames(catalogName, schemaName, TABLE_TYPES);
        Collections.sort(tables, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.toUpperCase().compareTo(o2.toUpperCase());
            }
        });
        for (String tableName : tables) {
            String excludeRegex = settingsProvider.get().getProperties().get(Settings.SQL_EXPLORER_EXCLUDE_TABLES_REGEX);
            if (!tableName.matches(excludeRegex)
                    && !tableName.toUpperCase().matches(excludeRegex)
                    && !tableName.toLowerCase().matches(excludeRegex)) {
                DbTreeNode treeNode = new DbTreeNode(this, tableName,
                        NODE_TYPE_TABLE, VaadinIcon.TABLE, parent);
                if (catalogName != null) {
                    treeNode.getProperties().setProperty(PROPERTY_CATALOG_NAME, catalogName);
                }
                if (schemaName != null) {
                    treeNode.getProperties().setProperty(PROPERTY_SCHEMA_NAME, schemaName);
                }
                list.add(treeNode);
            }
        }
        return list;
    }

    protected void addTableNodes(IDdlReader reader, DbTreeNode parent,
            String catalogName, String schemaName) {
        List<DbTreeNode> nodes = getTableTreeNodes(reader, parent, catalogName, schemaName);
        for (DbTreeNode treeNode : nodes) {
            parent.getChildren().add(treeNode);
        }
    }

    protected List<DbTreeNode> getTriggerTreeNodes(IDdlReader reader,
            DbTreeNode parent, String catalogName, String schemaName) {
        List<DbTreeNode> list = new ArrayList<DbTreeNode>();
        List<Trigger> triggers = new ArrayList<Trigger>();
        try {
            triggers = reader.getTriggers(catalogName, schemaName, parent.getName());
        } catch (Exception e) {
            log.warn("Unable to look up triggers for catalog, schema, table : " + catalogName + "." + schemaName + "." + parent.getName());
        }
        for (Trigger trigger : triggers) {
            DbTreeNode treeNode = new DbTreeNode(this, trigger.getName(), NODE_TYPE_TRIGGER, VaadinIcon.CROSSHAIRS, parent);
            if (catalogName != null) {
                treeNode.getProperties().setProperty(PROPERTY_CATALOG_NAME, catalogName);
            }
            if (schemaName != null) {
                treeNode.getProperties().setProperty(PROPERTY_SCHEMA_NAME, schemaName);
            }
            list.add(treeNode);
        }
        return list;
    }

    protected void addTriggerNodes(IDdlReader reader, DbTreeNode parent, String catalogName, String schemaName) {
        if (settingsProvider.get().getProperties().get(Settings.SQL_EXPLORER_SHOW_TRIGGERS, "").equals("true")) {
            List<DbTreeNode> nodes = getTriggerTreeNodes(reader, parent, catalogName, schemaName);
            for (DbTreeNode treeNode : nodes) {
                parent.getChildren().add(treeNode);
            }
        }
    }

    protected void addCatalogNodes(IDdlReader reader, DbTreeNode parent, IDatabasePlatform platform, List<String> catalogs) {
        Collections.sort(catalogs);
        if (catalogs.remove(platform.getDefaultCatalog())) {
            catalogs.add(0, platform.getDefaultCatalog());
        }
        for (String catalog : catalogs) {
            DbTreeNode catalogNode = new DbTreeNode(this, catalog, NODE_TYPE_CATALOG, VaadinIcon.BOOK, parent);
            parent.getChildren().add(catalogNode);
        }
    }

    protected void addSchemaNodes(IDdlReader reader, DbTreeNode parent, IDatabasePlatform platform, List<String> schemas) {
        Collections.sort(schemas);
        if (schemas.remove(platform.getDefaultSchema())) {
            schemas.add(0, platform.getDefaultSchema());
        }
        for (String schema : schemas) {
            DbTreeNode schemaNode = new DbTreeNode(this, schema, NODE_TYPE_SCHEMA, VaadinIcon.BOOK, parent);
            parent.getChildren().add(schemaNode);
        }
    }

    class DbTreePartNameGenerator implements SerializableFunction<DbTreeNode, String> {
        private static final long serialVersionUID = 1L;

        @Override
        public String apply(DbTreeNode node) {
            try {
                if (node.getType().equals(NODE_TYPE_CATALOG)) {
                    IDatabasePlatform platform = getDbForNode(node).getPlatform();
                    String catalog = platform.getDefaultCatalog();
                    if (catalog != null && catalog.equals(node.getName())) {
                        return "bold";
                    }
                } else if (node.getType().equals(NODE_TYPE_SCHEMA)) {
                    IDatabasePlatform platform = getDbForNode(node).getPlatform();
                    String schema = platform.getDefaultSchema();
                    if (schema != null && schema.equals(node.getName())) {
                        return "bold";
                    }
                }
            } catch (Exception e) {
                log.error("Failed to see if this node is the default catalog and/or schema", e);
            }
            return null;
        }
    }
}
