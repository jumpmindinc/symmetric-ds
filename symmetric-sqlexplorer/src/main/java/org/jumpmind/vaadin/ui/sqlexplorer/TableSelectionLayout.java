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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;

import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;

public class TableSelectionLayout extends VerticalLayout {
    private Set<org.jumpmind.db.model.Table> selectedTablesSet;
    private final Set<org.jumpmind.db.model.Table> originalSelectedTablesSet;
    private static final long serialVersionUID = 1L;
    public Grid<String> listOfTablesGrid;
    public ComboBox<String> catalogSelect;
    public ComboBox<String> schemaSelect;
    @SuppressWarnings("unused")
    private String filterCriteria = null;
    private TextField filterField;
    private IDatabasePlatform databasePlatform;
    private List<String> excludedTables;
    private String excludeTablesRegex;

    public TableSelectionLayout(IDatabasePlatform databasePlatform,
            Set<org.jumpmind.db.model.Table> selectedSet, String excludeTablesRegex) {
        this("Please select from the following tables", databasePlatform, selectedSet, null, excludeTablesRegex);
    }

    public TableSelectionLayout(String titleKey, IDatabasePlatform databasePlatform,
            Set<org.jumpmind.db.model.Table> selectedSet) {
        this(titleKey, databasePlatform, selectedSet, null, null);
    }

    public TableSelectionLayout(String titleKey, IDatabasePlatform databasePlatform,
            Set<org.jumpmind.db.model.Table> selectedSet, List<String> excludedTables, String excludeTablesRegex) {
        super();
        this.setSizeFull();
        this.setMargin(false);
        this.setSpacing(true);
        this.selectedTablesSet = selectedSet;
        this.originalSelectedTablesSet = selectedSet;
        this.databasePlatform = databasePlatform;
        this.excludedTables = excludedTables;
        this.excludeTablesRegex = excludeTablesRegex;
        createTableSelectionLayout(titleKey);
    }

    protected void createTableSelectionLayout(String titleKey) {
        this.add(new Span(titleKey));
        HorizontalLayout schemaChooserLayout = new HorizontalLayout();
        schemaChooserLayout.setSpacing(true);
        this.add(schemaChooserLayout);
        catalogSelect = new ComboBox<String>("Catalog");
        catalogSelect.setItems((catalog, filter) -> !CommonUiUtils.isFilteredOut(catalog, filter), getCatalogs());
        schemaChooserLayout.add(catalogSelect);
        if (selectedTablesSet.iterator().hasNext()) {
            catalogSelect.setValue(selectedTablesSet.iterator().next().getCatalog());
        } else {
            catalogSelect.setValue(databasePlatform.getDefaultCatalog());
        }
        schemaSelect = new ComboBox<String>("Schema");
        schemaChooserLayout.add(schemaSelect);
        schemaChooserLayout.addAndExpand(new Span());
        filterField = new TextField();
        filterField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        filterField.setPlaceholder("Filter Tables");
        filterField.setValueChangeMode(ValueChangeMode.LAZY);
        filterField.setValueChangeTimeout(200);
        filterField.addValueChangeListener(event -> {
            filterField.setValue(event.getValue());
            refreshTableOfTables();
        });
        schemaChooserLayout.add(filterField);
        schemaChooserLayout.setVerticalComponentAlignment(Alignment.END, filterField);
        listOfTablesGrid = new Grid<String>();
        listOfTablesGrid.setSizeFull();
        listOfTablesGrid.setSelectionMode(SelectionMode.MULTI);
        listOfTablesGrid.addItemClickListener(event -> {
            if (event.getColumn() != null) {
                listOfTablesGrid.deselectAll();
                listOfTablesGrid.select(event.getItem());
            }
        });
        listOfTablesGrid.addSelectionListener(event -> {
            selectedTablesSet.clear();
            selectedTablesSet.addAll(originalSelectedTablesSet);
            for (String table : listOfTablesGrid.getSelectedItems()) {
                selectedTablesSet.add(new org.jumpmind.db.model.Table(table));
            }
            selectionChanged();
        });
        listOfTablesGrid.addColumn(table -> table);
        this.addAndExpand(listOfTablesGrid);
        refreshSchemas();
        schemaSelect.addValueChangeListener(event -> refreshTableOfTables());
        catalogSelect.addValueChangeListener(event -> {
            refreshSchemas();
            refreshTableOfTables();
        });
        Button selectAllLink = new Button("Select All");
        selectAllLink.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
        selectAllLink.addClickListener((event) -> selectAll());
        Button selectNoneLink = new Button("Select None");
        selectNoneLink.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
        selectNoneLink.addClickListener((event) -> selectNone());
        HorizontalLayout selectAllFooter = new HorizontalLayout();
        selectAllFooter.setWidth("100%");
        selectAllFooter.setSpacing(true);
        selectAllFooter.add(selectAllLink, selectNoneLink);
        this.add(selectAllFooter);
        refreshTableOfTables();
    }

    public String getSelectedSchema() {
        String schemaName = (String) schemaSelect.getValue();
        if (schemaName != null && schemaName.equals(databasePlatform.getDefaultSchema())) {
            schemaName = null;
        }
        return StringUtils.isBlank(schemaName) ? null : schemaName;
    }

    public String getSelectedCatalog() {
        String catalogName = (String) catalogSelect.getValue();
        if (catalogName != null && catalogName.equals(databasePlatform.getDefaultCatalog())) {
            catalogName = null;
        }
        return StringUtils.isBlank(catalogName) ? null : catalogName;
    }

    protected void refreshSchemas() {
        List<String> schemas = getSchemas();
        if (schemas != null && schemas.size() == 0) {
            refreshTableOfTables();
            return;
        }
        schemaSelect.setItems((schema, filter) -> !CommonUiUtils.isFilteredOut(schema, filter), schemas);
        if (selectedTablesSet.iterator().hasNext()) {
            schemaSelect.setValue(selectedTablesSet.iterator().next().getSchema());
        } else {
            schemaSelect.setValue(databasePlatform.getDefaultSchema());
        }
    }

    protected void refreshTableOfTables() {
        List<String> tables = getTables();
        String filter = filterField == null ? "" : filterField.getValue();
        List<String> filteredTables = new ArrayList<String>();
        for (String table : tables) {
            if ((excludedTables == null || !excludedTables.contains(table.toLowerCase())) && display(getSelectedCatalog(), getSelectedSchema(), table)
                    && !CommonUiUtils.isFilteredOut(table, filter)) {
                filteredTables.add(table);
            }
        }
        listOfTablesGrid.setItems(filteredTables);
    }

    protected void selectionChanged() {
    }

    public List<String> getSelectedTables() {
        return new ArrayList<String>(listOfTablesGrid.getSelectedItems());
    }

    public void selectAll() {
        for (String table : listOfTablesGrid.getDataProvider().fetch(new Query<>()).collect(Collectors.toList())) {
            listOfTablesGrid.select(table);
        }
        selectionChanged();
    }

    public void selectNone() {
        listOfTablesGrid.deselectAll();
        selectionChanged();
    }

    public List<String> getSchemas() {
        return databasePlatform.getDdlReader().getSchemaNames(catalogSelect.getValue());
    }

    public List<String> getCatalogs() {
        return databasePlatform.getDdlReader().getCatalogNames();
    }

    public List<String> getTables() {
        List<String> tableNames = databasePlatform.getDdlReader().getTableNames((String) catalogSelect.getValue(),
                (String) schemaSelect.getValue(), new String[] { "TABLE" });
        Iterator<String> iter = tableNames.iterator();
        while (iter.hasNext()) {
            String tableName = iter.next();
            if (tableName.matches(excludeTablesRegex)
                    || tableName.toUpperCase().matches(excludeTablesRegex)
                    || tableName.toLowerCase().matches(excludeTablesRegex)) {
                iter.remove();
            }
        }
        return tableNames;
    }

    public List<String> getExcludedTables() {
        return excludedTables;
    }

    public void setExcludedTables(List<String> excludedTables) {
        this.excludedTables = excludedTables;
    }

    protected boolean display(String catalog, String schema, String table) {
        return true;
    }

    protected boolean select(String catalog, String schema, String table) {
        for (org.jumpmind.db.model.Table t : this.selectedTablesSet) {
            if (table.equals(t.getName())) {
                return true;
            }
        }
        return false;
    }
}
