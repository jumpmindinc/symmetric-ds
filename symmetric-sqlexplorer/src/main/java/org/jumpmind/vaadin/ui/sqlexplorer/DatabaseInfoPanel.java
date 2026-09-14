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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.vaadin.ui.common.ColumnVisibilityToggler;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.TabSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.grid.Grid.SelectionMode;

public class DatabaseInfoPanel extends VerticalLayout implements IInfoPanel {
    private static final long serialVersionUID = 1L;
    Logger log = LoggerFactory.getLogger(DatabaseInfoPanel.class);
    IDb db;
    Settings settings;
    TabSheet tabSheet;
    String selectedCaption;

    public DatabaseInfoPanel(IDb db, Settings settings, String selectedTabCaption) {
        this.db = db;
        this.settings = settings;
        setSizeFull();
        tabSheet = CommonUiUtils.createTabSheet();
        tabSheet.addSelectedTabChangeListener(event -> selectedCaption = tabSheet.getSelectedTab().getName());
        add(tabSheet);
        Connection c = null;
        try {
            if (db.getPlatform().getDataSource() != null) {
                c = ((DataSource) db.getPlatform().getDataSource()).getConnection();
                DatabaseMetaData metaData = c.getMetaData();
                tabSheet.add(createTabData(createGridWithReflection(DatabaseMetaData.class, metaData), null), "Meta Data");
                tabSheet.add(createTabData(createGridWithReflection(Connection.class, c), null), "Connection");
                try {
                    ResultSet rs = null;
                    try {
                        rs = metaData.getClientInfoProperties();
                    } catch (SQLException e) {
                        log.debug("Could not create Client Info Properties tab", e.getMessage());
                    }
                    ColumnVisibilityToggler clientInfoPropertiesToggler = new ColumnVisibilityToggler();
                    Grid<List<Object>> clientInfoProperties = CommonUiUtils.putResultsInGrid(clientInfoPropertiesToggler,
                            rs, Integer.MAX_VALUE, false);
                    clientInfoProperties.setSizeFull();
                    tabSheet.add(createTabData(clientInfoProperties, clientInfoPropertiesToggler), "Client Info Properties");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create Client Info Properties tab", e);
                }
                try {
                    ColumnVisibilityToggler catalogsToggler = new ColumnVisibilityToggler();
                    Grid<List<Object>> catalogs = CommonUiUtils.putResultsInGrid(catalogsToggler, metaData.getCatalogs(),
                            Integer.MAX_VALUE, false);
                    catalogs.setSizeFull();
                    tabSheet.add(createTabData(catalogs, catalogsToggler), "Catalogs");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create Catalogs tab", e);
                }
                try {
                    Grid<List<Object>> schemas;
                    ColumnVisibilityToggler schemasToggler = new ColumnVisibilityToggler();
                    try {
                        schemas = CommonUiUtils.putResultsInGrid(schemasToggler, metaData.getSchemas(), Integer.MAX_VALUE, false);
                    } catch (SQLException e) {
                        schemas = CommonUiUtils.putResultsInGrid(schemasToggler, metaData.getSchemas("", null), Integer.MAX_VALUE, false);
                    }
                    schemas.setSizeFull();
                    tabSheet.add(createTabData(schemas, schemasToggler), "Schemas");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create Schemas tab", e);
                }
                try {
                    ColumnVisibilityToggler tableTypesToggler = new ColumnVisibilityToggler();
                    Grid<List<Object>> tableTypes = CommonUiUtils.putResultsInGrid(tableTypesToggler,
                            metaData.getTableTypes(), Integer.MAX_VALUE, false);
                    tableTypes.setSizeFull();
                    tabSheet.add(createTabData(tableTypes, tableTypesToggler), "Table Types");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create Table Types tab", e);
                }
                try {
                    ColumnVisibilityToggler dataTypesToggler = new ColumnVisibilityToggler();
                    Grid<List<Object>> dataTypes = CommonUiUtils.putResultsInGrid(dataTypesToggler, metaData.getTypeInfo(),
                            Integer.MAX_VALUE, false);
                    dataTypes.setSizeFull();
                    tabSheet.add(createTabData(dataTypes, dataTypesToggler), "Data Types");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create Data Types tab", e);
                }
                try {
                    tabSheet.add(
                            createTabData(createGridFromString(metaData.getNumericFunctions(), "Numeric Functions"), null),
                            "Numeric Functions");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create Numeric Functions tab", e);
                }
                try {
                    tabSheet.add(
                            createTabData(createGridFromString(metaData.getStringFunctions(), "String Functions"), null),
                            "String Functions");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create String Functions tab", e);
                }
                try {
                    tabSheet.add(
                            createTabData(createGridFromString(metaData.getSystemFunctions(), "System Functions"), null),
                            "System Functions");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create System Functions tab", e);
                }
                try {
                    tabSheet.add(createTabData(createGridFromString(metaData.getTimeDateFunctions(), "Date/Time Functions"),
                            null), "Date/Time Functions");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create Date/Time Functions tab", e);
                }
                try {
                    tabSheet.add(createTabData(createGridFromString(metaData.getSQLKeywords(), "Keywords"), null), "Keywords");
                } catch (AbstractMethodError e) {
                    log.debug("Could not create Keywords tab", e);
                }
            }
        } catch (SQLException e) {
            log.error("", e);
        } finally {
            JdbcSqlTemplate.close(c);
        }
        Iterator<Component> i = tabSheet.iterator();
        while (i.hasNext()) {
            Component component = i.next();
            Tab tab = tabSheet.getTab(component);
            if (tab.getLabel().equals(selectedTabCaption)) {
                tabSheet.setSelectedTab(component);
                break;
            }
        }
    }

    public VerticalLayout createTabData(Grid<?> grid, ColumnVisibilityToggler columnVisibilityToggler) {
        VerticalLayout layout = new VerticalLayout();
        layout.setMargin(false);
        layout.setSizeFull();
        if (columnVisibilityToggler != null && !columnVisibilityToggler.isEmpty()) {
            layout.add(columnVisibilityToggler);
            layout.setHorizontalComponentAlignment(Alignment.END, columnVisibilityToggler);
        }
        layout.addAndExpand(grid);
        return layout;
    }

    private Grid<List<Object>> createGridWithReflection(Class<?> reflectionClass, Object instance) {
        Grid<List<Object>> grid = new Grid<List<Object>>();
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.setSizeFull();
        grid.addItemClickListener(event -> {
            if (event.getColumn() != null) {
                grid.deselectAll();
                grid.select(event.getItem());
            }
        });
        grid.addColumn(row -> row.get(0)).setHeader("Property").setWidth("400px").setResizable(true);
        grid.addColumn(row -> row.get(1)).setHeader("Value").setResizable(true);
        List<List<Object>> outerList = new ArrayList<List<Object>>();
        Method[] methods = reflectionClass.getMethods();
        for (Method method : methods) {
            if ((method.getReturnType().equals(Integer.TYPE) || method.getReturnType().equals(String.class)
                    || method.getReturnType().equals(Boolean.TYPE)) && method.getParameterTypes().length == 0) {
                try {
                    List<Object> innerList = new ArrayList<Object>();
                    Object value = method.invoke(instance);
                    innerList.add(cleanMethodName(method.getName()));
                    innerList.add(value);
                    outerList.add(innerList);
                } catch (Exception e) {
                    log.debug("Could not invoke method " + method.getName(), e);
                }
            }
        }
        grid.setItems(outerList);
        return grid;
    }

    private String cleanMethodName(String methodName) {
        if (methodName.startsWith("get")) {
            methodName = methodName.substring(3);
        } else if (methodName.startsWith("is")) {
            methodName = methodName.substring(2);
        }
        if (!methodName.isEmpty()) {
            methodName = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
            for (int i = 0; i < methodName.length() - 1; i++) {
                if (Character.isUpperCase(methodName.charAt(i)) && (Character.isLowerCase(methodName.charAt(i + 1))
                        || (i > 0 && Character.isLowerCase(methodName.charAt(i - 1))))) {
                    methodName = methodName.substring(0, i) + " " + methodName.substring(i);
                    i++;
                }
            }
        }
        return methodName;
    }

    private Grid<String> createGridFromString(String data, String columnName) {
        Grid<String> grid = new Grid<String>();
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.setSizeFull();
        grid.addItemClickListener(event -> {
            if (event.getColumn() != null) {
                grid.deselectAll();
                grid.select(event.getItem());
            }
        });
        grid.addColumn(row -> row).setHeader(columnName);
        List<String> values = new ArrayList<String>();
        int lastComma = 0;
        for (int i = 0; i < data.length(); i++) {
            if (data.charAt(i) == ',') {
                values.add(data.substring(lastComma, i).trim());
                lastComma = i + 1;
            }
        }
        grid.setItems(values);
        return grid;
    }

    public String getSelectedTabCaption() {
        return selectedCaption;
    }

    @Override
    public void selected() {
    }

    @Override
    public void unselected() {
    }
}
