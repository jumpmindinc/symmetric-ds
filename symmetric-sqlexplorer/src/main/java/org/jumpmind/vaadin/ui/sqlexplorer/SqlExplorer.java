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

import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_AUTO_COMPLETE;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.Trigger;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.CustomSplitLayout;
import org.jumpmind.vaadin.ui.common.Label;
import org.jumpmind.vaadin.ui.common.TabSheet.EnhancedTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.selection.MultiSelectionEvent;
import com.vaadin.flow.data.selection.SelectionListener;
import com.vaadin.flow.shared.Registration;

@CssImport("./sqlexplorer.css")
public class SqlExplorer extends CustomSplitLayout {
    private static final long serialVersionUID = 1L;
    final Logger log = LoggerFactory.getLogger(getClass());
    final static VaadinIcon QUERY_ICON = VaadinIcon.FILE_O;
    final static double DEFAULT_SPLIT_POS = 20;
    IDbProvider databaseProvider;
    ISettingsProvider settingsProvider;
    MenuItem showButton;
    DbTree dbTree;
    SelectionListener<Grid<DbTreeNode>, DbTreeNode> listener;
    Registration listenerRegistration;
    SqlExplorerTabPanel contentTabs;
    MenuBar contentMenuBar;
    IContentTab selected;
    double savedSplitPosition = DEFAULT_SPLIT_POS;
    String user = "nouser";
    IDbMenuItem[] additionalMenuItems;
    Set<IInfoPanel> infoTabs = new HashSet<IInfoPanel>();

    public SqlExplorer(String configDir, IDbProvider databaseProvider, ISettingsProvider settingsProvider, String user) {
        this(configDir, databaseProvider, settingsProvider, user, DEFAULT_SPLIT_POS);
    }

    public SqlExplorer(String configDir, IDbProvider databaseProvider, String user, IDbMenuItem... additionalMenuItems) {
        this(configDir, databaseProvider, new DefaultSettingsProvider(configDir, user), user, DEFAULT_SPLIT_POS, additionalMenuItems);
    }

    public SqlExplorer(String configDir, IDbProvider databaseProvider, ISettingsProvider settingsProvider, String user, IDbMenuItem... additionalMenuItems) {
        this(configDir, databaseProvider, settingsProvider, user, DEFAULT_SPLIT_POS, additionalMenuItems);
    }

    public SqlExplorer(String configDir, IDbProvider databaseProvider, String user, double leftSplitPos) {
        this(configDir, databaseProvider, new DefaultSettingsProvider(configDir, user), user, leftSplitPos);
    }

    public SqlExplorer(String configDir, IDbProvider databaseProvider, ISettingsProvider settingsProvider, String user, double leftSplitSize,
            IDbMenuItem... additionalMenuItems) {
        this.databaseProvider = databaseProvider;
        this.settingsProvider = settingsProvider;
        this.user = user;
        this.savedSplitPosition = leftSplitSize;
        this.additionalMenuItems = additionalMenuItems;
        setSizeFull();
        addClassName("sqlexplorer");
        VerticalLayout leftLayout = new VerticalLayout();
        leftLayout.setClassName("sqlexplorer-left");
        leftLayout.setMargin(false);
        leftLayout.setSpacing(false);
        leftLayout.setPadding(false);
        leftLayout.setSizeFull();
        leftLayout.add(buildLeftMenu());
        Scroller scrollable = new Scroller();
        scrollable.setSizeFull();
        dbTree = buildDbTree();
        scrollable.setContent(dbTree);
        leftLayout.addAndExpand(scrollable);
        VerticalLayout rightLayout = new VerticalLayout();
        rightLayout.setClassName("sqlexplorer-right");
        rightLayout.setMargin(false);
        rightLayout.setSpacing(false);
        rightLayout.setPadding(false);
        rightLayout.setSizeFull();
        HorizontalLayout rightMenuWrapper = new HorizontalLayout();
        rightMenuWrapper.setMargin(false);
        rightMenuWrapper.setSpacing(false);
        rightMenuWrapper.setPadding(false);
        rightMenuWrapper.setWidthFull();
        contentMenuBar = new MenuBar();
        contentMenuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY);
        contentMenuBar.setWidthFull();
        addShowButton(contentMenuBar);
        Span spacer = new Span();
        spacer.setWidth("0");
        spacer.setHeight("40px");
        rightMenuWrapper.add(contentMenuBar, spacer);
        rightLayout.add(rightMenuWrapper);
        contentTabs = new SqlExplorerTabPanel(this);
        contentTabs.addSelectedTabChangeListener(event -> {
            if (event.getSelectedTab() != null) {
                selectContentTab((IContentTab) ((EnhancedTab) event.getSelectedTab()).getComponent());
            }
        });
        rightLayout.addAndExpand(contentTabs);
        addToPrimary(leftLayout);
        addToSecondary(rightLayout);
        setSplitterPosition(savedSplitPosition);
    }

    protected MenuBar buildLeftMenu() {
        MenuBar leftMenu = new MenuBar();
        leftMenu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY);
        leftMenu.setWidthFull();
        MenuItem hideButton = leftMenu.addItem(CommonUiUtils.createMenuBarIcon(VaadinIcon.MENU), event -> {
            savedSplitPosition = this.getSplitterPosition() > 10 ? this.getSplitterPosition() : DEFAULT_SPLIT_POS;
            setSplitterPosition(0);
            setPrimaryStyle("max-width", "0%");
            showButton.setVisible(true);
            resetContentMenuBar();
            if (selected instanceof QueryPanel) {
                ((DefaultButtonBar) ((QueryPanel) selected).getButtonBar()).populate(contentMenuBar);
            }
        });
        hideButton.getElement().setAttribute("title", "Hide the database explorer");
        MenuItem refreshButton = leftMenu.addItem(CommonUiUtils.createMenuBarIcon(VaadinIcon.REFRESH), event -> {
            dbTree.refresh(true);
            Component tab = contentTabs.getSelectedTab();
            if (tab instanceof QueryPanel) {
                if (findQueryPanelForDb(((QueryPanel) tab).db).suggester != null) {
                    findQueryPanelForDb(((QueryPanel) tab).db).suggester.clearCaches();
                }
            }
        });
        refreshButton.getElement().setAttribute("title", "Refresh the database explorer");
        MenuItem selectionMode = leftMenu.addItem(CommonUiUtils.createMenuBarIcon(VaadinIcon.GRID_BIG_O), event -> {
            MenuItem source = event.getSource();
            source.removeAll();
            if (dbTree.getSelectionModel() instanceof GridMultiSelectionModel) {
                dbTree.setSelectionMode(SelectionMode.SINGLE);
                source.add(new Icon(VaadinIcon.GRID_BIG_O));
                source.getElement().setAttribute("title", "Switch to multi-select mode");
            } else {
                dbTree.setSelectionMode(SelectionMode.MULTI);
                source.add(new Icon(VaadinIcon.THIN_SQUARE));
                source.getElement().setAttribute("title", "Switch to single-select mode");
            }
            listenerRegistration.remove();
            listenerRegistration = dbTree.addSelectionListener(listener);
            dbTree.refresh(true);
        });
        selectionMode.getElement().setAttribute("title", "Switch to multi-select mode");
        MenuItem openQueryTab = leftMenu.addItem(CommonUiUtils.createMenuBarIcon(QUERY_ICON),
                event -> openQueryWindow(dbTree.getSelectedItems()));
        openQueryTab.getElement().setAttribute("title", "Open a query tab");
        MenuItem settings = leftMenu.addItem(CommonUiUtils.createMenuBarIcon(VaadinIcon.COG), event -> {
            SettingsDialog dialog = new SettingsDialog(SqlExplorer.this);
            dialog.show();
        });
        settings.getElement().setAttribute("title", "Modify sql explorer settings");
        return leftMenu;
    }

    protected void addShowButton(MenuBar contentMenuBar) {
        boolean visible = showButton != null ? showButton.isVisible() : false;
        showButton = contentMenuBar.addItem(new Icon(VaadinIcon.MENU), event -> {
            setSplitterPosition(savedSplitPosition);
            setPrimaryStyle("max-width", "100%");
            showButton.setVisible(false);
        });
        showButton.getElement().setAttribute("title", "Show the database explorer");
        showButton.setVisible(visible);
    }

    protected void selectContentTab(IContentTab tab) {
        if (tab != null) {
            if (selected != null) {
                selected.unselected();
            }
            contentTabs.setSelectedTab((Component) tab);
            resetContentMenuBar();
            if (tab instanceof QueryPanel) {
                ((DefaultButtonBar) ((QueryPanel) tab).getButtonBar()).populate(contentMenuBar);
            }
            tab.selected();
            selected = tab;
        }
    }

    public void resetContentMenuBar() {
        contentMenuBar.removeAll();
        addShowButton(contentMenuBar);
    }

    protected QueryPanel openQueryWindow(DbTreeNode node) {
        return openQueryWindow(dbTree.getDbForNode(node));
    }

    protected QueryPanel openQueryWindow(IDb db) {
        String dbName = db.getName();
        DefaultButtonBar buttonBar = new DefaultButtonBar();
        QueryPanel panel = new QueryPanel(db, settingsProvider, buttonBar, user);
        buttonBar.init(db, settingsProvider, panel, additionalMenuItems);
        EnhancedTab tab = contentTabs.add(panel, getTabName(dbName));
        tab.setCloseable(true);
        tab.setIcon(new Icon(QUERY_ICON));
        selectContentTab(panel);
        return panel;
    }

    protected void openQueryWindow(Set<DbTreeNode> nodes) {
        Set<String> dbNames = new HashSet<String>();
        for (DbTreeNode node : nodes) {
            IDb db = dbTree.getDbForNode(node);
            String dbName = db.getName();
            if (!dbNames.contains(dbName)) {
                dbNames.add(dbName);
                openQueryWindow(node);
            }
        }
    }

    public void refreshQueryPanels() {
        Iterator<Component> panelIterator = contentTabs.iterator();
        while (panelIterator.hasNext()) {
            Component panel = panelIterator.next();
            if (panel instanceof QueryPanel) {
                QueryPanel queryPanel = ((QueryPanel) panel);
                if (settingsProvider.get().getProperties().is(SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS)) {
                    queryPanel.removeGeneralResultsTab();
                } else if (!settingsProvider.get().getProperties().is(SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS)) {
                    queryPanel.createGeneralResultsTab();
                }
                boolean autoCompleteEnabled = settingsProvider.get().getProperties().is(SQL_EXPLORER_AUTO_COMPLETE);
                queryPanel.setAutoCompleteEnabled(autoCompleteEnabled);
            }
        }
    }

    public QueryPanel findQueryPanelForDb(IDb db) {
        QueryPanel panel = null;
        if (contentTabs.getComponentCount() > 0) {
            Component comp = contentTabs.getSelectedTab();
            if (comp instanceof QueryPanel) {
                QueryPanel prospectiveQueryPanel = (QueryPanel) comp;
                if (prospectiveQueryPanel.getDb().getName().equals(db.getName())) {
                    panel = prospectiveQueryPanel;
                }
            }
            if (panel == null) {
                Iterator<Component> i = contentTabs.iterator();
                while (i.hasNext()) {
                    comp = (Component) i.next();
                    if (comp instanceof QueryPanel) {
                        QueryPanel prospectiveQueryPanel = (QueryPanel) comp;
                        if (prospectiveQueryPanel.getDb().getName().equals(db.getName())) {
                            panel = prospectiveQueryPanel;
                            break;
                        }
                    }
                }
            }
            if (panel == null) {
                panel = openQueryWindow(db);
            }
        }
        return panel;
    }

    protected void generateSelectForSelectedTables() {
        Set<DbTreeNode> tableNodes = dbTree.getSelected(DbTree.NODE_TYPE_TABLE);
        for (DbTreeNode treeNode : tableNodes) {
            IDb db = dbTree.getDbForNode(treeNode);
            QueryPanel panel = findQueryPanelForDb(db);
            IDatabasePlatform platform = db.getPlatform();
            Table table = treeNode.getTableFor();
            DmlStatement dmlStatement = platform.createDmlStatement(DmlType.SELECT_ALL, table, null);
            panel.appendSql(dmlStatement.getSql());
            contentTabs.setSelectedTab(panel);
        }
    }

    protected void generateDmlForSelectedTables(DmlType dmlType) {
        Set<DbTreeNode> tableNodes = dbTree.getSelected(DbTree.NODE_TYPE_TABLE);
        for (DbTreeNode treeNode : tableNodes) {
            IDb db = dbTree.getDbForNode(treeNode);
            QueryPanel panel = findQueryPanelForDb(db);
            IDatabasePlatform platform = db.getPlatform();
            Table table = treeNode.getTableFor();
            DmlStatement dmlStatement = platform.createDmlStatement(dmlType, table, null);
            Row row = new Row(table.getColumnCount());
            Column[] columns = table.getColumns();
            for (Column column : columns) {
                String value = null;
                if (column.getParsedDefaultValue() == null) {
                    value = CommonUiUtils.getJdbcTypeValue(column.getJdbcTypeName());
                } else {
                    value = column.getParsedDefaultValue().toString();
                }
                row.put(column.getName(), value);
            }
            String sql = dmlStatement.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
            panel.appendSql(sql);
            contentTabs.setSelectedTab(panel);
        }
    }

    protected void dropSelectedTables() {
        Set<DbTreeNode> tableNodes = dbTree.getSelected(DbTree.NODE_TYPE_TABLE);
        List<Table> tables = new ArrayList<Table>();
        Map<Table, DbTreeNode> tableToTreeNode = new HashMap<Table, DbTreeNode>();
        for (DbTreeNode treeNode : tableNodes) {
            Table table = treeNode.getTableFor();
            tables.add(table);
            tableToTreeNode.put(table, treeNode);
        }
        tables = Database.sortByForeignKeys(new ArrayList<Relation>(tables))
                .stream().map(r -> (Table) r).collect(Collectors.toList());
        Collections.reverse(tables);
        dropTables(tables, tableToTreeNode);
    }

    private void dropTables(final List<Table> tables, final Map<Table, DbTreeNode> tableToTreeNode) {
        String msg = null;
        if (tables.size() > 1) {
            msg = "Do you want to drop " + tables.size() + " tables?";
        } else if (tables.size() == 1) {
            Table table = tables.get(0);
            msg = "Do you want to drop " + table.getFullyQualifiedName() + "?";
        }
        new ConfirmDialog("Drop Tables?", msg, "Ok", e -> {
            for (Table table : tables) {
                DbTreeNode treeNode = tableToTreeNode.get(table);
                IDb db = dbTree.getDbForNode(treeNode);
                try {
                    db.getPlatform().dropTables(false, table);
                } catch (Exception ex) {
                    String message = "Failed to drop " + table.getFullyQualifiedName() + ".  ";
                    CommonUiUtils.notify(message + "See log file for more details");
                    log.warn(message, ex);
                }
            }
            for (IContentTab panel : infoTabs) {
                contentTabs.remove(contentTabs.getTab((Component) panel));
            }
            infoTabs.clear();
            dbTree.refresh(true);
        }, "Cancel", e -> {
        }).open();
    }

    protected DbTree buildDbTree() {
        final DbTree tree = new DbTree(databaseProvider, settingsProvider);
        tree.addThemeVariants(GridVariant.LUMO_COMPACT);
        tree.setHeightFull();
        listener = event -> {
            MultiSelectionEvent<?, DbTreeNode> multiSelectEvent = null;
            if (event instanceof MultiSelectionEvent<?, ?>) {
                multiSelectEvent = (MultiSelectionEvent<?, DbTreeNode>) event;
            }
            Set<DbTreeNode> nodes = dbTree.getSelectedItems();
            if (nodes != null && (multiSelectEvent == null || !multiSelectEvent.getAddedSelection().isEmpty())) {
                String selectedTabCaption = null;
                for (IInfoPanel panel : infoTabs) {
                    selectedTabCaption = panel.getSelectedTabCaption();
                    EnhancedTab tab = contentTabs.getTab((Component) panel);
                    if (tab != null) {
                        contentTabs.remove(tab);
                    }
                }
                infoTabs.clear();
                if (nodes.size() > 0) {
                    DbTreeNode treeNode;
                    if (multiSelectEvent != null) {
                        treeNode = multiSelectEvent.getAddedSelection().iterator().next();
                    } else {
                        treeNode = nodes.iterator().next();
                    }
                    if (treeNode != null && treeNode.getType().equals(DbTree.NODE_TYPE_DATABASE)) {
                        IDb db = dbTree.getDbForNode(treeNode);
                        DatabaseInfoPanel databaseInfoTab = new DatabaseInfoPanel(db, settingsProvider.get(), selectedTabCaption);
                        EnhancedTab tab = contentTabs.add(databaseInfoTab, db.getName(), new Icon(VaadinIcon.DATABASE), 0);
                        tab.setCloseable(true);
                        infoTabs.add(databaseInfoTab);
                    }
                    if (treeNode != null && treeNode.getType().equals(DbTree.NODE_TYPE_TABLE)) {
                        Table table = treeNode.getTableFor();
                        if (table != null) {
                            IDb db = dbTree.getDbForNode(treeNode);
                            TableInfoPanel tableInfoTab = new TableInfoPanel(table, user, db, settingsProvider.get(), SqlExplorer.this,
                                    selectedTabCaption);
                            EnhancedTab tab = contentTabs.add(tableInfoTab, table.getFullyQualifiedName(),
                                    new Icon(VaadinIcon.TABLE), 0);
                            tab.setCloseable(true);
                            infoTabs.add(tableInfoTab);
                            selectContentTab(tableInfoTab);
                        }
                    } else if (treeNode != null && treeNode.getType().equals(DbTree.NODE_TYPE_TRIGGER)) {
                        Table table = treeNode.getParent().getTableFor();
                        IDdlReader reader = dbTree.getDbForNode(treeNode).getPlatform().getDdlReader();
                        Trigger trigger = reader.getTriggerFor(table, treeNode.getName());
                        if (trigger != null) {
                            IDb db = dbTree.getDbForNode(treeNode);
                            TriggerInfoPanel triggerInfoTab = new TriggerInfoPanel(trigger, db, settingsProvider.get(),
                                    selectedTabCaption);
                            EnhancedTab tab = contentTabs.add(triggerInfoTab, trigger.getName(), new Icon(VaadinIcon.CROSSHAIRS), 0);
                            tab.setCloseable(true);
                            infoTabs.add(triggerInfoTab);
                            selectContentTab(triggerInfoTab);
                        }
                    }
                }
                for (DbTreeNode treeNode : nodes) {
                    IDb db = dbTree.getDbForNode(treeNode);
                    QueryPanel panel = getQueryPanelForDb(db);
                    if (panel == null && db != null) {
                        openQueryWindow(db);
                    }
                    if (db != null && treeNode.getParent() == null) {
                        selectContentTab(getQueryPanelForDb(db));
                    }
                }
            }
        };
        listenerRegistration = tree.addSelectionListener(listener);
        GridContextMenu<DbTreeNode> contextMenu = new GridContextMenu<DbTreeNode>(tree);
        contextMenu.setDynamicContentHandler(clickedNode -> {
            contextMenu.removeAll();
            Set<DbTreeNode> selectedNodes = dbTree.getSelectedItems();
            if (clickedNode != null) {
                switch (clickedNode.getType()) {
                    case DbTree.NODE_TYPE_TABLE:
                        contextMenu.addItem(createItem("Query", QUERY_ICON), item -> openQueryWindow(selectedNodes));
                        contextMenu.addItem(createItem("Select", QUERY_ICON), item -> generateSelectForSelectedTables());
                        if (settingsProvider.get().isAllowDml()) {
                            contextMenu.addItem(createItem("Insert", QUERY_ICON), item -> generateDmlForSelectedTables(DmlType.INSERT));
                            contextMenu.addItem(createItem("Update", QUERY_ICON), item -> generateDmlForSelectedTables(DmlType.UPDATE));
                            contextMenu.addItem(createItem("Delete", QUERY_ICON), item -> generateDmlForSelectedTables(DmlType.DELETE));
                            contextMenu.addItem(createItem("Drop", VaadinIcon.ARROW_DOWN), item -> dropSelectedTables());
                        }
                        if (settingsProvider.get().isAllowImport()) {
                            contextMenu.addItem(createItem("Import", VaadinIcon.DOWNLOAD), item -> {
                                if (!selectedNodes.isEmpty()) {
                                    IDb db = dbTree.getDbForNode(selectedNodes.iterator().next());
                                    new DbImportDialog(db.getPlatform(), dbTree.getSelectedTables()).showAtSize(0.6);
                                }
                            });
                        }
                        if (settingsProvider.get().isAllowExport()) {
                            contextMenu.addItem(createItem("Export", VaadinIcon.UPLOAD), item -> {
                                if (!selectedNodes.isEmpty()) {
                                    IDb db = dbTree.getDbForNode(selectedNodes.iterator().next());
                                    String excludeTablesRegex = settingsProvider.get().getProperties()
                                            .get(Settings.SQL_EXPLORER_EXCLUDE_TABLES_REGEX);
                                    new DbExportDialog(db.getPlatform(), dbTree.getSelectedTables(), findQueryPanelForDb(db),
                                            excludeTablesRegex).showAtSize(0.6);
                                }
                            });
                        }
                        if (settingsProvider.get().isAllowFill()) {
                            contextMenu.addItem(createItem("Fill", VaadinIcon.FILL), item -> {
                                if (!selectedNodes.isEmpty()) {
                                    IDb db = dbTree.getDbForNode(selectedNodes.iterator().next());
                                    String excludeTablesRegex = settingsProvider.get().getProperties()
                                            .get(Settings.SQL_EXPLORER_EXCLUDE_TABLES_REGEX);
                                    new DbFillDialog(db.getPlatform(), dbTree.getSelectedTables(), findQueryPanelForDb(db),
                                            excludeTablesRegex).showAtSize(0.6);
                                }
                            });
                        }
                        contextMenu.addItem(createItem("Copy Name", VaadinIcon.COPY), item -> {
                            for (DbTreeNode treeNode : selectedNodes) {
                                IDb db = dbTree.getDbForNode(selectedNodes.iterator().next());
                                DatabaseInfo dbInfo = db.getPlatform().getDatabaseInfo();
                                final String quote = dbInfo.getDelimiterToken();
                                final String catalogSeparator = dbInfo.getCatalogSeparator();
                                final String schemaSeparator = dbInfo.getSchemaSeparator();
                                Table table = treeNode.getTableFor();
                                if (table != null) {
                                    QueryPanel panel = findQueryPanelForDb(db);
                                    panel.appendSql(table.getQualifiedName(quote, catalogSeparator, schemaSeparator));
                                    contentTabs.setSelectedTab(panel);
                                }
                            }
                        });
                        break;
                    case DbTree.NODE_TYPE_DATABASE:
                    case DbTree.NODE_TYPE_CATALOG:
                    case DbTree.NODE_TYPE_SCHEMA:
                        contextMenu.addItem(createItem("Query", QUERY_ICON), item -> openQueryWindow(selectedNodes));
                        break;
                    case DbTree.NODE_TYPE_TRIGGER:
                        contextMenu.addItem(createItem("Export", VaadinIcon.UPLOAD), item -> {
                            if (!selectedNodes.isEmpty()) {
                                IDb db = dbTree.getDbForNode(selectedNodes.iterator().next());
                                String excludeTablesRegex = settingsProvider.get().getProperties()
                                        .get(Settings.SQL_EXPLORER_EXCLUDE_TABLES_REGEX);
                                new DbExportDialog(db.getPlatform(), dbTree.getSelectedTables(), findQueryPanelForDb(db),
                                        excludeTablesRegex).showAtSize(0.6);
                            }
                        });
                }
                return true;
            }
            return false;
        });
        return tree;
    }

    protected Label createItem(String text, VaadinIcon icon) {
        return new Label(icon, text);
    }

    protected QueryPanel getQueryPanelForDb(IDb db) {
        if (db != null) {
            Iterator<Component> i = contentTabs.iterator();
            while (i.hasNext()) {
                Component c = i.next();
                if (c instanceof QueryPanel) {
                    QueryPanel panel = (QueryPanel) c;
                    if (panel.getDb().getName().equals(db.getName())) {
                        return panel;
                    }
                }
            }
        }
        return null;
    }

    protected String getTabName(String name) {
        int tabs = contentTabs.getTabCount();
        String tabName = tabs > 0 ? null : name;
        if (tabName == null) {
            for (int j = 0; j < 10; j++) {
                boolean alreadyUsed = false;
                String suffix = "";
                for (int i = 0; i < tabs; i++) {
                    EnhancedTab tab = contentTabs.getTab(i);
                    String currentTabName = tab.getName();
                    if (j > 0) {
                        suffix = "-" + j;
                    }
                    if (currentTabName.equals(name + suffix)) {
                        alreadyUsed = true;
                    }
                }
                if (!alreadyUsed) {
                    tabName = name + suffix;
                    break;
                }
            }
        }
        return tabName;
    }

    public ISettingsProvider getSettingsProvider() {
        return settingsProvider;
    }

    public IDbProvider getDatabaseProvider() {
        return databaseProvider;
    }

    public void refresh() {
        dbTree.refresh(false);
    }

    public void focus() {
        dbTree.focus();
    }

    public void addResultsTab(String caption, Icon icon, IContentTab panel) {
        EnhancedTab tab = contentTabs.add((Component) panel, caption, icon);
        tab.setCloseable(true);
        selectContentTab(panel);
    }

    public void putResultsInQueryTab(String value, IDb db) {
        openQueryWindow(db).appendSql(value);
    }
}
