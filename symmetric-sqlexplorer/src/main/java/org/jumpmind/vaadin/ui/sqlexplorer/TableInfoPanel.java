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
package org.jumpmind.vaadin.ui.sqlexplorer;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.symmetric.io.data.DbExport;
import org.jumpmind.symmetric.io.data.DbExport.Format;
import org.jumpmind.vaadin.ui.common.ColumnVisibilityToggler;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.TabSheet;
import org.jumpmind.vaadin.ui.common.TabSheet.EnhancedTab;
import org.jumpmind.vaadin.ui.sqlexplorer.SqlRunner.ISqlRunnerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMode;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class TableInfoPanel extends VerticalLayout implements IInfoPanel {
    private static final Logger log = LoggerFactory.getLogger(TableInfoPanel.class);
    private static final long serialVersionUID = 1L;
    SqlExplorer explorer;
    TabSheet tabSheet;
    String selectedCaption;

    public TableInfoPanel(final org.jumpmind.db.model.Table table, final String user, final IDb db,
            final Settings settings, String selectedTabCaption) {
        this(table, user, db, settings, null, selectedTabCaption);
    }

    public TableInfoPanel(final org.jumpmind.db.model.Table table, final String user, final IDb db,
            final Settings settings, SqlExplorer explorer, String selectedTabCaption) {
        this.explorer = explorer;
        setSizeFull();
        tabSheet = CommonUiUtils.createTabSheet();
        tabSheet.setAutoselect(false);
        tabSheet.addSelectedTabChangeListener(event -> {
            EnhancedTab selectedTab = tabSheet.getSelectedTab();
            if (selectedTab != null) {
                selectedCaption = selectedTab.getName();
                Component selectedComponent = selectedTab.getComponent();
                if (selectedComponent != null) {
                    if (!(selectedComponent instanceof AceEditor)) {
                        boolean isInit = Boolean.TRUE.equals(ComponentUtil.getData(selectedComponent, "isInit"));
                        AbstractMetaDataGridCreator creator = ((AbstractMetaDataGridCreator) ComponentUtil
                                .getData(selectedComponent, "creator"));
                        if (selectedCaption.equals("Data") && isInit == true) {
                            refreshData(table, user, db, settings, false);
                        } else if (creator != null) {
                            populate((VerticalLayout) selectedComponent);
                        }
                    } else if (((boolean) ComponentUtil.getData(selectedComponent, "data")) == true) {
                        populateSource(table, db, (AceEditor) selectedComponent);
                    }
                }
            }
        });
        add(tabSheet);
        JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) db.getPlatform().getSqlTemplate();
        tabSheet.add(create(new ColumnMetaDataTableCreator(sqlTemplate, table, settings)),
                "Columns");
        tabSheet.add(create(new PrimaryKeyMetaDataTableCreator(sqlTemplate, table, settings)),
                "Primary Keys");
        tabSheet.add(create(new IndexMetaDataTableCreator(sqlTemplate, table, settings)),
                "Indexes");
        if (db.getPlatform().getDatabaseInfo().isForeignKeysSupported()) {
            tabSheet.add(create(new ImportedKeysMetaDataTableCreator(sqlTemplate, table,
                    settings)), "Imported Keys");
            tabSheet.add(create(new ExportedKeysMetaDataTableCreator(sqlTemplate, table,
                    settings)), "Exported Keys");
        }
        refreshData(table, user, db, settings, true);
        AceEditor editor = CommonUiUtils.createAceEditor();
        ComponentUtil.setData(editor, "data", true);
        tabSheet.add(editor, "Source");
        Iterator<Component> i = tabSheet.iterator();
        while (i.hasNext()) {
            Component component = i.next();
            EnhancedTab tab = tabSheet.getTab(component);
            if (tab.getName().equals(selectedTabCaption)) {
                tabSheet.setSelectedTab(component);
                break;
            }
        }
        if (tabSheet.getSelectedTab() == null) {
            tabSheet.setSelectedTab("Columns");
        }
    }

    public String getSelectedTabCaption() {
        return selectedCaption;
    }

    protected void refreshData(final org.jumpmind.db.model.Table table, final String user, final IDb db,
            final Settings settings, boolean isInit) {
        if (!isInit && tabSheet.getTabCount() >= 2) {
            tabSheet.remove(tabSheet.getTab(1).getName());
        }
        IDatabasePlatform platform = db.getPlatform();
        DmlStatement dml = platform.createDmlStatement(DmlType.SELECT_ALL, table, null);
        final HorizontalLayout executingLayout = new HorizontalLayout();
        executingLayout.setSizeFull();
        final ProgressBar p = new ProgressBar();
        p.setIndeterminate(true);
        executingLayout.add(p);
        ComponentUtil.setData(executingLayout, "isInit", isInit);
        tabSheet.add(executingLayout, "Data", isInit ? null : new Icon(VaadinIcon.SPINNER), 1);
        if (!isInit) {
            tabSheet.setSelectedTab(executingLayout);
        }
        SqlRunner runner = new SqlRunner(dml.getSql(), false, user, db, settings, explorer,
                new ISqlRunnerListener() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void writeSql(String sql) {
                        explorer.openQueryWindow(db).appendSql(sql);
                    }

                    @Override
                    public void reExecute(String sql) {
                        refreshData(table, user, db, settings, false);
                    }

                    @Override
                    public void finished(final VaadinIcon icon, final List<Component> results,
                            long executionTimeInMs, boolean transactionStarted,
                            boolean transactionEnded) {
                        UI ui = TableInfoPanel.this.getUI().orElse(null);
                        if (ui != null) {
                            ui.access(() -> {
                                tabSheet.remove("Data");
                                VerticalLayout layout = new VerticalLayout();
                                layout.setMargin(true);
                                layout.setSizeFull();
                                if (results.size() > 0) {
                                    layout.add(results.get(0));
                                }
                                tabSheet.add(layout, "Data", null, 1);
                                tabSheet.setSelectedTab(layout);
                            });
                        }
                    }
                });
        runner.setShowSqlOnResults(false);
        runner.setLogAtDebug(true);
        if (!isInit) {
            runner.start();
        }
    }

    protected VerticalLayout create(AbstractMetaDataGridCreator creator) {
        VerticalLayout layout = new VerticalLayout();
        layout.setMargin(false);
        layout.setSizeFull();
        ComponentUtil.setData(layout, "creator", creator);
        return layout;
    }

    protected void populate(VerticalLayout layout) {
        AbstractMetaDataGridCreator creator = (AbstractMetaDataGridCreator) ComponentUtil.getData(layout, "creator");
        ColumnVisibilityToggler columnVisibilityToggler = new ColumnVisibilityToggler();
        Grid<List<Object>> grid = creator.create(columnVisibilityToggler);
        layout.add(columnVisibilityToggler);
        layout.setHorizontalComponentAlignment(Alignment.END, columnVisibilityToggler);
        layout.add(grid);
        layout.expand(grid);
        ComponentUtil.setData(layout, "creator", null);
    }

    protected void populateSource(org.jumpmind.db.model.Table table, IDb db, AceEditor oldTab) {
        try {
            if (tabSheet.getTab(oldTab) != null) {
                tabSheet.remove(tabSheet.getTab(oldTab).getName());
            }
            DbExport export = new DbExport(db.getPlatform());
            export.setNoCreateInfo(false);
            export.setNoData(true);
            export.setCatalog(table.getCatalog());
            export.setSchema(table.getSchema());
            export.setFormat(Format.SQL);
            AceEditor editor = CommonUiUtils.createAceEditor();
            editor.setMode(AceMode.sql);
            editor.setValue(export.exportTables(new org.jumpmind.db.model.Table[] { table }));
            ComponentUtil.setData(editor, "data", false);
            tabSheet.add(editor, "Source");
            tabSheet.setSelectedTab(editor);
        } catch (IOException e) {
            log.warn("Failed to export the create information", e);
        }
    }

    @Override
    public void selected() {
    }

    @Override
    public void unselected() {
    }
}
