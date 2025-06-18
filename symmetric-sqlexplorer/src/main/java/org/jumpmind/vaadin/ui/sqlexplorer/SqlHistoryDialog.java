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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.ResizableDialog;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.grid.HeaderRow.HeaderCell;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

public class SqlHistoryDialog extends ResizableDialog {
    private static final long serialVersionUID = 1L;
    private final Grid<SqlHistory> grid;
    private final TreeSet<SqlHistory> sqlHistories;
    private QueryPanel queryPanel;
    private ISettingsProvider settingsProvider;

    public SqlHistoryDialog(ISettingsProvider settingsProvider, QueryPanel queryPanel) {
        super("Sql History");
        this.settingsProvider = settingsProvider;
        this.queryPanel = queryPanel;
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setMargin(false);
        mainLayout.setSpacing(true);
        add(mainLayout, 1);
        sqlHistories = new TreeSet<SqlHistory>(settingsProvider.get().getSqlHistory());
        grid = new Grid<SqlHistory>();
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.addColumn(history -> StringUtils.abbreviate(history.getSqlStatement(), 50)).setKey("sqlStatement").setHeader("SQL");
        grid.addColumn(history -> String.format("%1$tY-%1$tm-%1$td %1$tk:%1$tM:%1$tS", history.getLastExecuteTime()))
                .setHeader("Time").setWidth("150px");
        grid.addColumn(history -> CommonUiUtils.formatDuration(history.getLastExecuteDuration())).setHeader("Duration")
                .setWidth("120px").setFlexGrow(0);
        grid.addColumn(history -> history.getExecuteCount()).setHeader("Count").setWidth("120px").setFlexGrow(0);
        for (Column<SqlHistory> column : grid.getColumns()) {
            column.setResizable(true);
        }
        HeaderRow filteringHeader = grid.appendHeaderRow();
        HeaderCell logTextFilterCell = filteringHeader.getCell(grid.getColumnByKey("sqlStatement"));
        TextField filterField = new TextField();
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setPlaceholder("Filter");
        filterField.getElement().setAttribute("theme", "font-size-xs");
        filterField.setWidth("100%");
        filterField.addThemeName("small");
        // Update filter When the filter input is changed
        filterField.addValueChangeListener(event -> filter(event.getValue()));
        logTextFilterCell.setComponent(filterField);
        grid.addItemClickListener(event -> {
            if (event.getColumn() != null) {
                grid.deselectAll();
                grid.select(event.getItem());
            }
            if (event.getClickCount() == 2) {
                select();
            }
        });
        grid.setHeight("120px");
        grid.setWidthFull();
        mainLayout.addAndExpand(grid);
        grid.setItems(sqlHistories);
        Button cancelButton = new Button("Cancel");
        cancelButton.addClickListener(event -> close());
        Button applyButton = CommonUiUtils.createPrimaryButton("Select");
        applyButton.addClickShortcut(Key.ENTER);
        applyButton.addClickListener(event -> select());
        buildButtonFooter(cancelButton, applyButton);
    }

    private void filter(String filter) {
        final Set<SqlHistory> histories = new TreeSet<SqlHistory>(settingsProvider.get().getSqlHistory());
        List<SqlHistory> filteredHistories = new ArrayList<SqlHistory>();
        for (SqlHistory history : histories) {
            if (!CommonUiUtils.isFilteredOut(history.getSqlStatement(), filter)) {
                filteredHistories.add(history);
            }
        }
        grid.setItems(filteredHistories);
    }

    protected void select() {
        Set<SqlHistory> histories = grid.getSelectedItems();
        if (histories != null && histories.size() > 0) {
            String delimiter = settingsProvider.get().getProperties().get(Settings.SQL_EXPLORER_DELIMITER);
            for (SqlHistory history : sqlHistories.descendingSet()) {
                if (histories.contains(history)) {
                    String sql = history.getSqlStatement();
                    queryPanel.appendSql(sql + (sql.trim().endsWith(delimiter) ? "" : delimiter));
                }
            }
            close();
        }
    }
}
