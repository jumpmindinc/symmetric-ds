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
package org.jumpmind.vaadin.ui.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class ColumnVisibilityToggler extends Button {
    private VerticalLayout checkboxLayout;
    private Map<Column<?>, Checkbox> columnMap;
    private static final long serialVersionUID = 1L;

    public ColumnVisibilityToggler() {
        super();
        ContextMenu menu = new ContextMenu(this);
        columnMap = new HashMap<Column<?>, Checkbox>();
        menu.setOpenOnClick(true);
        menu.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                for (Column<?> column : columnMap.keySet()) {
                    columnMap.get(column).setValue(column.isVisible());
                }
            }
        });
        checkboxLayout = new VerticalLayout();
        checkboxLayout.setPadding(false);
        checkboxLayout.setSpacing(false);
        checkboxLayout.getStyle().set("margin", "0").set("padding-bottom", "2px");
        menu.addComponent(checkboxLayout);
        setIcon(new Icon(VaadinIcon.COG));
        getElement().setAttribute("title", "Hide/show columns");
    }

    public Column<?> addColumn(Column<?> column, String header) {
        if (!columnMap.containsKey(column)) {
            Checkbox checkbox = new Checkbox(header);
            checkbox.addValueChangeListener(event -> column.setVisible(event.getValue()));
            columnMap.put(column, checkbox);
            rebuildMenuItems();
        }
        return column;
    }

    public void removeColumn(Column<?> column) {
        columnMap.remove(column);
        rebuildMenuItems();
    }

    public boolean isEmpty() {
        return columnMap.isEmpty();
    }

    private void rebuildMenuItems() {
        checkboxLayout.removeAll();
        List<Checkbox> checkboxes = columnMap.values().stream().collect(Collectors.toList());
        checkboxes.sort((box0, box1) -> box0.getLabel().compareTo(box1.getLabel()));
        for (Checkbox checkbox : checkboxes) {
            checkboxLayout.add(checkbox);
        }
    }
}
