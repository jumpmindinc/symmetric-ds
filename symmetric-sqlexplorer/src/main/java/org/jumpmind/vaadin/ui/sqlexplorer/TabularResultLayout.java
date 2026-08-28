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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_MAX_RESULTS;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_SHOW_ROW_NUMBERS;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.util.FormatUtils;
import org.jumpmind.vaadin.ui.common.ColumnVisibilityToggler;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.CsvExport;
import org.jumpmind.vaadin.ui.common.GridDataProvider;
import org.jumpmind.vaadin.ui.common.IDataProvider;
import org.jumpmind.vaadin.ui.common.Label;
import org.jumpmind.vaadin.ui.common.NotifyDialog;
import org.jumpmind.vaadin.ui.sqlexplorer.SqlRunner.ISqlRunnerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

public class TabularResultLayout extends VerticalLayout {
    private static final long serialVersionUID = 1L;
    final String ACTION_SELECT = "Select From";
    final String ACTION_INSERT = "Insert";
    final String ACTION_UPDATE = "Update";
    final String ACTION_DELETE = "Delete";
    final Logger log = LoggerFactory.getLogger(getClass());
    SqlExplorer explorer;
    QueryPanel queryPanel;
    String relationName;
    String catalogName;
    String schemaName;
    Grid<List<Object>> grid;
    Map<Integer, String> columnNameMap;
    Map<Grid.Column<List<Object>>, ValueProvider<List<Object>, Object>> valueProviderMap;
    Relation resultRelation;
    String sql;
    ResultSet rs;
    ResultSetMetaData meta;
    IDb db;
    ISqlRunnerListener listener;
    String user;
    Settings settings;
    boolean showSql = true;
    boolean isInQueryGeneralResults;
    boolean generateNewExport = true;
    MenuItem followToMenu;
    MenuItem toggleKeepResultsButton;
    ColumnVisibilityToggler columnVisibilityToggler;
    Label resultLabel;

    public TabularResultLayout(IDb db, String sql, ResultSet rs, ISqlRunnerListener listener, Settings settings, boolean showSql)
            throws SQLException {
        this(null, db, sql, rs, listener, null, settings, null, showSql, false);
    }

    public TabularResultLayout(SqlExplorer explorer, IDb db, String sql, ResultSet rs, ISqlRunnerListener listener, String user,
            Settings settings, QueryPanel queryPanel, boolean showSql, boolean isInQueryGeneralResults) throws SQLException {
        this.explorer = explorer;
        this.sql = sql;
        this.showSql = showSql;
        this.db = db;
        this.rs = rs;
        this.meta = rs.getMetaData();
        this.listener = listener;
        this.user = user;
        this.settings = settings;
        this.queryPanel = queryPanel;
        this.isInQueryGeneralResults = isInQueryGeneralResults;
        createTabularResultLayout();
    }

    public String getSql() {
        return sql;
    }

    public void setShowSql(boolean showSql) {
        this.showSql = showSql;
    }

    @SuppressWarnings("unchecked")
    protected void createTabularResultLayout() {
        this.setSizeFull();
        this.setSpacing(false);
        this.setMargin(false);
        getStyle().set("padding-bottom", "0");
        createMenuBar();
        try {
            columnNameMap = new HashMap<Integer, String>();
            for (int i = 0; i < meta.getColumnCount(); i++) {
                String realColumnName = meta.getColumnName(i + 1);
                String columnName = realColumnName;
                int j = 1;
                while (columnNameMap.containsValue(columnName)) {
                    columnName = realColumnName + "_" + j++;
                }
                columnNameMap.put(i, columnName);
            }
            grid = putResultsInGrid(settings.getProperties().getInt(SQL_EXPLORER_MAX_RESULTS));
            grid.setSizeFull();
            ContextMenu menu = new ContextMenu(grid);
            menu.addItem(ACTION_SELECT, event -> handleAction(ACTION_SELECT));
            menu.addItem(ACTION_INSERT, event -> handleAction(ACTION_INSERT));
            menu.addItem(ACTION_UPDATE, event -> handleAction(ACTION_UPDATE));
            menu.addItem(ACTION_DELETE, event -> handleAction(ACTION_DELETE));
            if (resultRelation instanceof Table table && table.getForeignKeyCount() > 0) {
                followToMenu = menu.addItem("Follow to", (ComponentEventListener<ClickEvent<MenuItem>>) null);
                buildFollowToMenu();
            }
            Editor<List<Object>> editor = grid.getEditor();
            Binder<List<Object>> binder = new Binder<List<Object>>();
            int i = 0;
            for (Grid.Column<List<Object>> col : grid.getColumns()) {
                String colId = col.getKey();
                if (colId == null || !colId.equals("#")) {
                    Integer index = Integer.valueOf(i);
                    TextField field = new TextField();
                    binder.bind(field, list -> list.get(index).toString(), (list, value) -> list.set(index, value));
                    col.setEditorComponent(field);
                    i++;
                }
            }
            editor.setBinder(binder);
            if (resultRelation instanceof Table) {
                List<Object>[] unchangedValue = (List<Object>[]) new List[1];
                Object[] pkParams = new Object[resultRelation.getPrimaryKeyColumnCount()];
                int[] pkTypes = new int[pkParams.length];
                editor.addOpenListener(event -> {
                    unchangedValue[0] = new ArrayList<Object>(event.getItem());
                    int paramCount = 0;
                    for (int j = 0; j < unchangedValue[0].size(); j++) {
                        if (resultRelation.getPrimaryKeyColumnIndex(columnNameMap.get(j)) >= 0) {
                            pkParams[paramCount] = unchangedValue[0].get(j);
                            pkTypes[paramCount] = resultRelation.getColumnWithName(columnNameMap.get(j)).getMappedTypeCode();
                            paramCount++;
                        }
                    }
                });
                editor.addSaveListener(event -> {
                    grid.setItems((DataProvider<List<Object>, Void>) grid.getDataProvider());
                    List<Object> row = event.getItem();
                    List<String> colNames = new ArrayList<String>();
                    List<Object> params = new ArrayList<Object>();
                    List<Integer> types = new ArrayList<Integer>();
                    for (int j = 0; j < row.size(); j++) {
                        String colName = columnNameMap.get(j);
                        Object param = row.get(j);
                        Object originalValue = unchangedValue[0].get(j).toString();
                        if ((param == null && originalValue == null) || (param != null && param.equals(originalValue))) {
                            continue;
                        }
                        colNames.add(colName);
                        params.add(param);
                        types.add(resultRelation.getColumnWithName(colName).getMappedTypeCode());
                    }
                    String sql = buildUpdate((Table) resultRelation, colNames, unchangedValue[0], resultRelation.getPrimaryKeyColumnNames());
                    log.warn(sql);
                    Object[] allParams;
                    int[] allTypes;
                    if (pkParams.length > 0) {
                        allParams = ArrayUtils.addAll(params.toArray(), pkParams);
                        allTypes = ArrayUtils.addAll(types.stream().mapToInt(val -> val).toArray(), pkTypes);
                        for (int k = 0; k < allTypes.length; k++) {
                            if (allTypes[k] == Types.DATE && db.getPlatform().getDdlBuilder().getDatabaseInfo()
                                    .isDateOverridesToTimestamp()) {
                                allTypes[k] = Types.TIMESTAMP;
                            }
                        }
                    } else {
                        List<Object> requiredColParams = new ArrayList<Object>();
                        List<Integer> requiredColTypes = new ArrayList<Integer>();
                        for (int k = 0; k < unchangedValue[0].size(); k++) {
                            Object val = unchangedValue[0].get(k);
                            Column col = resultRelation.getColumn(k);
                            if (db.getPlatform().canColumnBeUsedInWhereClause(col)) {
                                if (!val.equals("<null>")) {
                                    requiredColParams.add(val);
                                    requiredColTypes.add(col.getMappedTypeCode());
                                }
                            }
                        }
                        allParams = ArrayUtils.addAll(params.toArray(), requiredColParams.toArray());
                        allTypes = ArrayUtils.addAll(types.stream().mapToInt(val -> val).toArray(),
                                requiredColTypes.stream().mapToInt(l -> l).toArray());
                    }
                    try {
                        db.getPlatform().getSqlTemplate().update(sql, allParams, allTypes);
                    } catch (SqlException e) {
                        NotifyDialog.show("Error",
                                "<b>The table could not be updated.</b><br>"
                                        + "Cause: the sql update statement failed to execute.<br><br>"
                                        + "To view the <b>Stack Trace</b>, click <b>\"Details\"</b>.",
                                e, NotificationVariant.LUMO_ERROR);
                    }
                });
                if (settings.isAllowDml()) {
                    CommonUiUtils.configureEditor(grid);
                }
            }
            Shortcuts.addShortcutListener(grid, () -> {
                Component parent = grid.getParent().orElse(null);
                if (parent != null && parent instanceof TabularResultLayout) {
                    TabularResultLayout layout = (TabularResultLayout) parent;
                    queryPanel.reExecute(layout.getSql());
                }
            }, Key.ENTER, KeyModifier.CONTROL).listenOn(grid);
            Shortcuts.addShortcutListener(grid, () -> {
                TabularResultLayout layout = (TabularResultLayout) grid.getParent().orElse(null);
                if (layout != null) {
                    queryPanel.reExecute(layout.getSql());
                }
            }, Key.ENTER, KeyModifier.CONTROL, KeyModifier.SHIFT).listenOn(grid);
            this.addAndExpand(grid);
            long count = (grid.getDataProvider().fetch(new Query<>()).count());
            int maxResultsSize = settings.getProperties().getInt(SQL_EXPLORER_MAX_RESULTS);
            if (count >= maxResultsSize) {
                resultLabel.setText("Limited to <span style='color: red'>" + maxResultsSize + "</span> rows;");
            } else {
                resultLabel.setText(count + " rows returned;");
            }
        } catch (SQLException ex) {
            log.error(ex.getMessage(), ex);
            CommonUiUtils.notifyError();
        }
    }

    private void createMenuBar() {
        HorizontalLayout resultBar = new HorizontalLayout();
        resultBar.getStyle().set("margin", "0 16px");
        HorizontalLayout leftBar = new HorizontalLayout();
        leftBar.setSpacing(true);
        resultLabel = new Label("");
        leftBar.add(resultLabel);
        final Span sqlSpan = new Span("");
        sqlSpan.setWidth("800px");
        leftBar.add(sqlSpan);
        resultBar.addAndExpand(leftBar);
        resultBar.setVerticalComponentAlignment(Alignment.CENTER, leftBar);
        MenuBar rightBar = new MenuBar();
        rightBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY, MenuBarVariant.LUMO_SMALL);
        MenuItem refreshButton = rightBar.addItem(new Icon(VaadinIcon.REFRESH), event -> listener.reExecute(sql));
        refreshButton.getElement().setAttribute("title", "Refresh");
        Anchor downloadAnchor = new Anchor();
        downloadAnchor.setTarget("_blank");
        downloadAnchor.getElement().setAttribute("download", true);
        Icon downloadIcon = new Icon(VaadinIcon.UPLOAD);
        downloadIcon.setSize("16px");
        downloadIcon.addClickListener(event -> {
            if (generateNewExport) {
                IDataProvider<List<Object>> target = new GridDataProvider<List<Object>>(grid, valueProviderMap);
                CsvExport<List<Object>> csvExport = null;
                if (target instanceof IDataProvider) {
                    csvExport = new CsvExport<List<Object>>(target);
                    csvExport.setFileName(db.getName() + "-export.csv");
                    csvExport.setTitle(sql);
                    downloadAnchor.setHref(csvExport.getDownloadHandler());
                }
                generateNewExport = false;
                UI.getCurrent().getPage().executeJs("$0.click();", downloadAnchor.getElement());
            } else {
                downloadAnchor.removeHref();
                generateNewExport = true;
            }
        });
        downloadAnchor.add(downloadIcon);
        MenuItem exportButton = rightBar.addItem(downloadAnchor);
        exportButton.getElement().setAttribute("title", "Export Results");
        if (isInQueryGeneralResults) {
            MenuItem keepResultsButton = rightBar.addItem(new Icon(VaadinIcon.COPY), event -> {
                queryPanel.addResultsTab(refreshWithoutSaveButton(), StringUtils.abbreviate(sql, 20),
                        queryPanel.getGeneralResultsTab().getIcon());
                queryPanel.resetGeneralResultsTab();
            });
            keepResultsButton.getElement().setAttribute("title", "Save these results to a new tab");
        }
        if (showSql) {
            sqlSpan.setText(StringUtils.abbreviate(sql, 200));
        }
        resultBar.add(rightBar);
        resultBar.setVerticalComponentAlignment(Alignment.CENTER, rightBar);
        columnVisibilityToggler = new ColumnVisibilityToggler();
        resultBar.add(columnVisibilityToggler);
        resultBar.setVerticalComponentAlignment(Alignment.END, columnVisibilityToggler);
        this.addComponentAsFirst(resultBar);
    }

    protected TabularResultLayout refreshWithoutSaveButton() {
        isInQueryGeneralResults = false;
        this.remove(this.getComponentAt(0));
        createMenuBar();
        return this;
    }

    protected void handleAction(String action) {
        try {
            DatabaseInfo dbInfo = db.getPlatform().getDatabaseInfo();
            final String quote = db.getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn() ? dbInfo.getDelimiterToken() : "";
            final String catalogSeparator = dbInfo.getCatalogSeparator();
            final String schemaSeparator = dbInfo.getSchemaSeparator();
            String[] columnHeaders = ArrayUtils.removeElement(CommonUiUtils.getHeaderCaptions(grid), null);
            Set<List<Object>> selectedRowsSet = grid.getSelectedItems();
            Iterator<List<Object>> setIterator = selectedRowsSet.iterator();
            while (setIterator.hasNext()) {
                List<Object> typeValueList = new ArrayList<Object>();
                List<Object> item = setIterator.next();
                for (int i = 1; i < columnHeaders.length; i++) {
                    Object typeValue = item.get(i - 1);
                    if (typeValue instanceof String) {
                        if ("<null>".equals(typeValue) || "".equals(typeValue)) {
                            typeValue = "null";
                        } else {
                            typeValue = "'" + typeValue + "'";
                        }
                    } else if (typeValue instanceof java.util.Date) {
                        typeValue = "{ts " + "'" + FormatUtils.TIMESTAMP_FORMATTER.format(typeValue) + "'" + "}";
                    }
                    typeValueList.add(typeValue);
                }
                if (action.equals(ACTION_SELECT)) {
                    StringBuilder sql = new StringBuilder("SELECT ");
                    for (int i = 1; i < columnHeaders.length; i++) {
                        if (i == 1) {
                            sql.append(quote).append(columnHeaders[i]).append(quote);
                        } else {
                            sql.append(", ").append(quote).append(columnHeaders[i]).append(quote);
                        }
                    }
                    sql.append(" FROM " + Table.getFullyQualifiedName(catalogName, schemaName, relationName, quote,
                            catalogSeparator, schemaSeparator));
                    sql.append(" WHERE ");
                    int track = 0;
                    for (int i = 0; i < resultRelation.getColumnCount(); i++) {
                        Column col = resultRelation.getColumn(i);
                        if (col.isPrimaryKey()) {
                            if (track == 0) {
                                sql.append(col.getName() + "=" + typeValueList.get(i));
                            } else {
                                sql.append(" and ").append(quote).append(col.getName()).append(quote).append("=")
                                        .append(typeValueList.get(i));
                            }
                            track++;
                        }
                    }
                    sql.append(";");
                    listener.writeSql(sql.toString());
                } else if (action.equals(ACTION_INSERT)) {
                    StringBuilder sql = new StringBuilder();
                    sql.append("INSERT INTO ").append(Table.getFullyQualifiedName(catalogName, schemaName,
                            relationName, quote, catalogSeparator, schemaSeparator)).append(" (");
                    for (int i = 1; i < columnHeaders.length; i++) {
                        if (i == 1) {
                            sql.append(quote + columnHeaders[i] + quote);
                        } else {
                            sql.append(", " + quote + columnHeaders[i] + quote);
                        }
                    }
                    sql.append(") VALUES (");
                    boolean first = true;
                    for (int i = 1; i < columnHeaders.length; i++) {
                        if (first) {
                            first = false;
                        } else {
                            sql.append(", ");
                        }
                        sql.append(typeValueList.get(i - 1));
                    }
                    sql.append(");");
                    listener.writeSql(sql.toString());
                } else if (action.equals(ACTION_UPDATE)) {
                    StringBuilder sql = new StringBuilder("UPDATE ");
                    sql.append(Table.getFullyQualifiedName(catalogName, schemaName, relationName, quote,
                            catalogSeparator, schemaSeparator) + " SET ");
                    for (int i = 1; i < columnHeaders.length; i++) {
                        if (i == 1) {
                            sql.append(quote).append(columnHeaders[i]).append(quote).append("=");
                        } else {
                            sql.append(", ").append(quote).append(columnHeaders[i]).append(quote).append("=");
                        }
                        sql.append(typeValueList.get(i - 1));
                    }
                    sql.append(" WHERE ");
                    int track = 0;
                    for (int i = 0; i < resultRelation.getColumnCount(); i++) {
                        Column col = resultRelation.getColumn(i);
                        if (col.isPrimaryKey()) {
                            if (track == 0) {
                                sql.append(quote).append(col.getName()).append(quote).append("=").append(typeValueList.get(i));
                            } else {
                                sql.append(" and ").append(quote).append(col.getName()).append(quote).append("=")
                                        .append(typeValueList.get(i));
                            }
                            track++;
                        }
                    }
                    sql.append(";");
                    listener.writeSql(sql.toString());
                } else if (action.equals(ACTION_DELETE)) {
                    StringBuilder sql = new StringBuilder("DELETE FROM ");
                    sql.append(Table.getFullyQualifiedName(catalogName, schemaName, relationName, quote,
                            catalogSeparator, schemaSeparator)).append(" WHERE ");
                    int track = 0;
                    for (int i = 0; i < resultRelation.getColumnCount(); i++) {
                        Column col = resultRelation.getColumn(i);
                        if (col.isPrimaryKey()) {
                            if (track == 0) {
                                sql.append(quote).append(col.getName()).append(quote).append("=").append(typeValueList.get(i));
                            } else {
                                sql.append(" and ").append(quote).append(col.getName()).append(quote).append("=")
                                        .append(typeValueList.get(i));
                            }
                            track++;
                        }
                    }
                    sql.append(";");
                    listener.writeSql(sql.toString());
                }
            }
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            Notification.show("There are an error while attempting to perform the action.  Please check the log file for further details.");
        }
    }

    protected static String getTypeValue(String type) {
        String value = null;
        if (type.equalsIgnoreCase("CHAR")) {
            value = "''";
        } else if (type.equalsIgnoreCase("VARCHAR")) {
            value = "''";
        } else if (type.equalsIgnoreCase("LONGVARCHAR")) {
            value = "''";
        } else if (type.equalsIgnoreCase("DATE")) {
            value = "''";
        } else if (type.equalsIgnoreCase("TIME")) {
            value = "''";
        } else if (type.equalsIgnoreCase("TIMESTAMP")) {
            value = "{ts ''}";
        } else if (type.equalsIgnoreCase("CLOB")) {
            value = "''";
        } else if (type.equalsIgnoreCase("BLOB")) {
            value = "''";
        } else if (type.equalsIgnoreCase("ARRAY")) {
            value = "[]";
        } else {
            value = "";
        }
        return value;
    }

    protected void buildFollowToMenu() {
        ForeignKey[] foreignKeys = resultRelation instanceof Table table ? table.getForeignKeys() : new ForeignKey[] {};
        for (final ForeignKey foreignKey : foreignKeys) {
            String optionTitle = foreignKey.getForeignTableName() + " (";
            for (Reference ref : foreignKey.getReferences()) {
                optionTitle += ref.getLocalColumnName() + ", ";
            }
            optionTitle = optionTitle.substring(0, optionTitle.length() - 2) + ")";
            followToMenu.getSubMenu().addItem(optionTitle, event -> followTo(foreignKey));
        }
    }

    protected void followTo(ForeignKey foreignKey) {
        Set<List<Object>> selectedRows = grid.getSelectedItems();
        if (selectedRows.size() > 0) {
            log.info("Following foreign key to " + foreignKey.getForeignTableName());
            if (queryPanel == null) {
                if (explorer != null) {
                    queryPanel = explorer.openQueryWindow(db);
                } else {
                    log.error("Failed to find current or create new query tab");
                }
            }
            Table foreignTable = foreignKey.getForeignTable();
            if (foreignTable == null) {
                foreignTable = (Table) db.getPlatform().getRelationFromCache(foreignKey.getForeignTableName(), false);
            }
            Reference[] references = foreignKey.getReferences();
            for (Reference ref : references) {
                if (ref.getForeignColumn() == null) {
                    ref.setForeignColumn(foreignTable.getColumnWithName(ref.getForeignColumnName()));
                }
            }
            String sql = createFollowSql(foreignTable, references, selectedRows.size());
            try {
                PreparedStatement ps = ((DataSource) db.getPlatform().getDataSource()).getConnection().prepareStatement(sql);
                int i = 1;
                for (List<Object> row : selectedRows) {
                    for (Reference ref : references) {
                        int colNum = 0;
                        for (Grid.Column<List<Object>> col : grid.getColumns()) {
                            if (col.getKey().equals(ref.getLocalColumnName())) {
                                break;
                            }
                        }
                        int targetType = ref.getForeignColumn().getMappedTypeCode();
                        ps.setObject(i, row.get(colNum - 1), targetType);
                        i++;
                    }
                }
                sql = ps.toString().substring(ps.toString().indexOf("select "));
                queryPanel.executeSql(sql, false);
            } catch (SQLException e) {
                log.error("Failed to follow foreign key", e);
            }
        }
    }

    protected String createFollowSql(Table foreignTable, Reference[] references, int selectedRowCount) {
        DatabaseInfo dbInfo = db.getPlatform().getDatabaseInfo();
        String quote = db.getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn() ? dbInfo.getDelimiterToken() : "";
        StringBuilder sql = new StringBuilder("select ");
        for (Column col : foreignTable.getColumns()) {
            sql.append(quote);
            sql.append(col.getName());
            sql.append(quote);
            sql.append(", ");
        }
        sql.delete(sql.length() - 2, sql.length());
        sql.append(" from ");
        sql.append(foreignTable.getQualifiedName(quote, dbInfo.getCatalogSeparator(), dbInfo.getSchemaSeparator()));
        sql.append(" where ");
        StringBuilder whereClause = new StringBuilder("(");
        for (Reference ref : references) {
            whereClause.append(ref.getForeignColumnName());
            whereClause.append("=? and ");
        }
        whereClause.delete(whereClause.length() - 5, whereClause.length());
        whereClause.append(") or ");
        for (int i = 0; i < selectedRowCount; i++) {
            sql.append(whereClause.toString());
        }
        sql.delete(sql.length() - 4, sql.length());
        return sql.toString();
    }

    protected Grid<List<Object>> putResultsInGrid(int maxResultSize) throws SQLException {
        String parsedSql = sql;
        String first = "";
        String second = "";
        String third = "";
        parsedSql = parsedSql.substring(parsedSql.toUpperCase().indexOf("FROM ") + 5, parsedSql.length());
        parsedSql = parsedSql.trim();
        String separator = ".";
        if (parsedSql.contains(separator)) {
            first = parsedSql.substring(0, parsedSql.indexOf(separator) + separator.length() - 1);
            parsedSql = parsedSql.substring(parsedSql.indexOf(separator) + separator.length(), parsedSql.length());
            if (parsedSql.contains(separator)) {
                second = parsedSql.substring(0, parsedSql.indexOf(separator) + separator.length() - 1);
                parsedSql = parsedSql.substring(parsedSql.indexOf(separator) + separator.length(), parsedSql.length());
                if (parsedSql.contains(separator)) {
                    third = parsedSql.substring(0, parsedSql.indexOf(separator) + separator.length() - 1);
                    parsedSql = parsedSql.substring(parsedSql.indexOf(separator) + separator.length(), parsedSql.length());
                } else {
                    third = parsedSql;
                }
            } else {
                second = parsedSql;
            }
        } else {
            first = parsedSql;
        }
        if (!third.equals("")) {
            relationName = third;
            schemaName = second;
            catalogName = first;
        } else if (!second.equals("")) {
            if (db.getPlatform().getDefaultCatalog() != null) {
                IDdlReader reader = db.getPlatform().getDdlReader();
                List<String> catalogs = reader.getCatalogNames();
                if (catalogs.contains(first)) {
                    catalogName = first;
                } else if (db.getPlatform().getDefaultSchema() != null) {
                    Iterator<String> iterator = catalogs.iterator();
                    while (iterator.hasNext()) {
                        List<String> schemas = reader.getSchemaNames(iterator.next());
                        if (schemas.contains(first)) {
                            schemaName = first;
                        }
                    }
                }
            } else if (db.getPlatform().getDefaultSchema() != null) {
                schemaName = first;
            }
            relationName = second;
        } else if (!first.equals("")) {
            relationName = parsedSql;
        }
        if (isNotBlank(relationName)) {
            if (relationName.contains(" ")) {
                relationName = relationName.substring(0, relationName.indexOf(" "));
            }
            if (isBlank(schemaName)) {
                schemaName = null;
            }
            if (isBlank(catalogName)) {
                catalogName = null;
            }
            String quote = "\"";
            if (catalogName != null && catalogName.contains(quote)) {
                catalogName = catalogName.replaceAll(quote, "");
                catalogName = catalogName.trim();
            }
            if (schemaName != null && schemaName.contains(quote)) {
                schemaName = schemaName.replaceAll(quote, "");
                schemaName = schemaName.trim();
            }
            if (relationName != null && relationName.contains(quote)) {
                relationName = relationName.replaceAll(quote, "");
                relationName = relationName.trim();
            }
            try {
                resultRelation = db.getPlatform().getRelationFromCache(catalogName, schemaName, relationName, false);
                if (resultRelation != null) {
                    relationName = resultRelation.getName();
                    if (isNotBlank(catalogName) && isNotBlank(resultRelation.getCatalog())) {
                        catalogName = resultRelation.getCatalog();
                    }
                    if (isNotBlank(schemaName) && isNotBlank(resultRelation.getSchema())) {
                        schemaName = resultRelation.getSchema();
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to lookup table: " + relationName, e);
            }
        }
        TypedProperties properties = settings.getProperties();
        boolean showRowNumbers = properties.is(SQL_EXPLORER_SHOW_ROW_NUMBERS);
        String[] excludeValues = getColumnsToExclude();
        final Grid<List<Object>> grid = new Grid<List<Object>>();
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.setColumnReorderingAllowed(true);
        grid.addItemClickListener(event -> {
            if (event.getColumn() != null) {
                grid.deselectAll();
                grid.select(event.getItem());
            }
        });
        List<List<Object>> outerList = new ArrayList<List<Object>>();
        if (rs != null) {
            grid.addColumn(row -> {
                return outerList.indexOf(row) + 1;
            }).setHeader("#").setKey("#").setFrozen(true).setFlexGrow(0).setResizable(true).setVisible(showRowNumbers);
            grid.addAttachListener(e -> {
                grid.getElement().executeJs("this.querySelector('vaadin-grid-flow-selection-column').frozen = true");
            });
            if (valueProviderMap == null) {
                valueProviderMap = new HashMap<Grid.Column<List<Object>>, ValueProvider<List<Object>, Object>>();
            }
            valueProviderMap.put(grid.getColumnByKey("#"), row -> outerList.indexOf(row) + 1);
            final ResultSetMetaData meta = rs.getMetaData();
            int totalColumns = meta.getColumnCount();
            Set<Integer> skipColumnIndexes = new HashSet<Integer>();
            Set<String> columnNames = new HashSet<String>();
            int[] types = new int[totalColumns];
            final int[] columnCounter = { 1 };
            while (columnCounter[0] <= totalColumns) {
                String realColumnName = meta.getColumnName(columnCounter[0]);
                String columnName = realColumnName;
                if (!Arrays.asList(excludeValues).contains(columnName)) {
                    int index = 1;
                    while (columnNames.contains(columnName)) {
                        columnName = realColumnName + "_" + index++;
                    }
                    columnNames.add(columnName);
                    Integer colNum = Integer.valueOf(columnCounter[0] - 1 - skipColumnIndexes.size());
                    columnVisibilityToggler.addColumn(grid.addColumn(row -> row.get(colNum)).setKey(columnName)
                            .setHeader(columnName).setPartNameGenerator(row -> {
                                if (row.get(colNum) == null) {
                                    return "italics";
                                }
                                return null;
                            }).setResizable(true).setAutoWidth(true), columnName);
                    valueProviderMap.put(grid.getColumnByKey(columnName), row -> row.get(colNum));
                    types[columnCounter[0] - 1] = meta.getColumnType(columnCounter[0]);
                } else {
                    skipColumnIndexes.add(columnCounter[0] - 1);
                }
                columnCounter[0]++;
            }
            for (int rowNumber = 1; rs.next() && rowNumber <= maxResultSize; rowNumber++) {
                List<Object> innerList = new ArrayList<Object>();
                for (int i = 0; i < totalColumns; i++) {
                    if (!skipColumnIndexes.contains(i)) {
                        Object o = CommonUiUtils.getObject(rs, i + 1);
                        int type = types[i];
                        switch (type) {
                            case Types.FLOAT:
                            case Types.DOUBLE:
                            case Types.REAL:
                            case Types.NUMERIC:
                            case Types.DECIMAL:
                                if (o != null && !(o instanceof BigDecimal)) {
                                    o = new BigDecimal(CommonUiUtils.castToNumber(o.toString()));
                                }
                                break;
                            case Types.TINYINT:
                            case Types.SMALLINT:
                            case Types.BIGINT:
                            case Types.INTEGER:
                                if (o != null && !(o instanceof Long) && !(o instanceof BigInteger)) {
                                    o = Long.parseLong(CommonUiUtils.castToNumber(o.toString()));
                                }
                                break;
                            default:
                                break;
                        }
                        innerList.add(o == null ? CommonUiUtils.NULL_TEXT : o);
                    }
                }
                outerList.add(innerList);
                if (rowNumber < 100) {
                    grid.getColumnByKey("#").setWidth("75px");
                } else if (rowNumber < 1000) {
                    grid.getColumnByKey("#").setWidth("95px");
                } else {
                    grid.getColumnByKey("#").setWidth("115px");
                }
            }
        } else {
            grid.addColumn(row -> row.get(0)).setHeader("Status").setKey("Status").setResizable(true);
            valueProviderMap.put(grid.getColumnByKey("Status"), row -> row.get(0));
            List<Object> innerList = new ArrayList<Object>();
            innerList.add("Metadata unavailable");
            outerList.add(innerList);
        }
        grid.setItems(outerList);
        return grid;
    }

    protected String[] getColumnsToExclude() {
        return new String[0];
    }

    protected String buildUpdate(Table table, List<String> columnNames, List<Object> originalValues, String[] pkColumnNames) {
        StringBuilder sql = new StringBuilder("update ");
        IDatabasePlatform platform = db.getPlatform();
        DatabaseInfo dbInfo = platform.getDatabaseInfo();
        String quote = platform.getDdlBuilder().isDelimitedIdentifierModeOn() ? dbInfo.getDelimiterToken() : "";
        sql.append(table.getQualifiedName(quote, dbInfo.getCatalogSeparator(), dbInfo.getSchemaSeparator()));
        sql.append(" set ");
        for (String col : columnNames) {
            sql.append(quote);
            sql.append(col);
            sql.append(quote);
            sql.append("=?, ");
        }
        sql.delete(sql.length() - 2, sql.length());
        sql.append(" where ");
        if (pkColumnNames.length > 0) {
            for (String col : pkColumnNames) {
                sql.append(quote);
                sql.append(col);
                sql.append(quote);
                sql.append("=? and ");
            }
        } else {
            Column[] cols = table.getColumns();
            for (int i = 0; i < originalValues.size(); i++) {
                Column col = cols[i];
                if (platform.canColumnBeUsedInWhereClause(col)) {
                    sql.append(quote);
                    sql.append(col.getName());
                    sql.append(quote);
                    if (!originalValues.get(i).equals("<null>")) {
                        sql.append("=? and ");
                    } else {
                        sql.append(" is null and ");
                    }
                }
            }
        }
        sql.delete(sql.length() - 5, sql.length());
        return sql.toString();
    }
}
