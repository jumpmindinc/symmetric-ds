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

import java.util.Map;

import org.jumpmind.db.model.Trigger;
import org.jumpmind.vaadin.ui.common.Label;
import org.jumpmind.vaadin.ui.common.ReadOnlyTextAreaDialog;
import org.jumpmind.vaadin.ui.sqlexplorer.TriggerInfoPanel.Refresher;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class TriggerTableLayout extends VerticalLayout {
    private static final long serialVersionUID = 1L;
    private Trigger trigger;
    private Grid<String> grid;
    private Refresher refresher;

    public TriggerTableLayout(Trigger trigger, Settings settings, Refresher refresher) {
        this.trigger = trigger;
        this.refresher = refresher;
        createTabularLayout();
    }

    public void createTabularLayout() {
        this.setSizeFull();
        this.setSpacing(false);
        HorizontalLayout bar = new HorizontalLayout();
        bar.getStyle().set("margin", "0 16px");
        HorizontalLayout leftBar = new HorizontalLayout();
        leftBar.setSpacing(true);
        leftBar.add(new Label(trigger.getFullyQualifiedName()));
        bar.addAndExpand(leftBar);
        bar.setVerticalComponentAlignment(Alignment.CENTER, leftBar);
        MenuBar rightBar = new MenuBar();
        rightBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY, MenuBarVariant.LUMO_SMALL);
        rightBar.addItem(new Icon(VaadinIcon.REFRESH), event -> refresher.refresh());
        bar.add(rightBar);
        bar.setVerticalComponentAlignment(Alignment.CENTER, rightBar);
        this.add(bar);
        grid = fillGrid();
        grid.setSizeFull();
        grid.addItemClickListener(event -> {
            if (event.getButton() == 0 && event.getColumn() != null) {
                if (event.getClickCount() == 2) {
                    String colKey = event.getColumn().getKey();
                    if (colKey.equals("property")) {
                        ReadOnlyTextAreaDialog.show("Property", event.getItem(), false);
                    } else if (colKey.equals("value")) {
                        ReadOnlyTextAreaDialog.show("Value", (String) trigger.getMetaData().get(event.getItem()), false);
                    }
                } else {
                    grid.deselectAll();
                    grid.select(event.getItem());
                }
            }
        });
        this.addAndExpand(grid);
    }

    private Grid<String> fillGrid() {
        Grid<String> grid = new Grid<String>();
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.setColumnReorderingAllowed(false);
        Map<String, Object> metaData = trigger.getMetaData();
        grid.addColumn(property -> property).setKey("property").setHeader("Property").setWidth("250px");
        grid.addColumn(property -> String.valueOf(metaData.get(property))).setKey("value").setHeader("Value");
        for (Column<String> column : grid.getColumns()) {
            column.setResizable(true);
        }
        grid.setItems(metaData.keySet());
        return grid;
    }
}
