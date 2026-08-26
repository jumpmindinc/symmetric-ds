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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;

@ViewPackages(classes = { ColumnVisibilityToggler.class })
class ColumnVisibilityTogglerTest extends BrowserlessTest {
    private ColumnVisibilityToggler createAndAttach() {
        ColumnVisibilityToggler toggler = new ColumnVisibilityToggler();
        UI.getCurrent().getElement().insertChild(0, toggler.getElement());
        return toggler;
    }

    @Test
    void constructor_buttonIsPresent() {
        createAndAttach();
        assertTrue(find(Button.class).exists());
    }

    @Test
    void addColumn_registersColumn() {
        ColumnVisibilityToggler toggler = createAndAttach();
        Grid<Span> grid = new Grid<>();
        Grid.Column<Span> col = grid.addColumn(Span::getText).setHeader("Test");
        toggler.addColumn(col, "Test");
        assertFalse(toggler.isEmpty());
    }

    @Test
    void noColumnsAdded_isEmpty() {
        ColumnVisibilityToggler toggler = createAndAttach();
        assertTrue(toggler.isEmpty());
    }

    @Test
    void removeColumn_columnIsRemoved() {
        ColumnVisibilityToggler toggler = createAndAttach();
        Grid<Span> grid = new Grid<>();
        Grid.Column<Span> col = grid.addColumn(Span::getText).setHeader("Test");
        toggler.addColumn(col, "Test");
        toggler.removeColumn(col);
        assertTrue(toggler.isEmpty());
    }

    @Test
    void addColumn_isNoLongerEmpty() {
        ColumnVisibilityToggler toggler = createAndAttach();
        Grid<String> grid = new Grid<>();
        Grid.Column<String> col = grid.addColumn(s -> s).setHeader("Name");
        toggler.addColumn(col, "Name");
        assertFalse(toggler.isEmpty());
    }

    @Test
    void addColumn_sameColumnTwice_doesNotDuplicate() {
        ColumnVisibilityToggler toggler = createAndAttach();
        Grid<String> grid = new Grid<>();
        Grid.Column<String> col = grid.addColumn(s -> s).setHeader("Name");
        toggler.addColumn(col, "Name");
        toggler.addColumn(col, "Name");
        toggler.removeColumn(col);
        assertTrue(toggler.isEmpty());
    }

    @Test
    void columnVisibility_canBeSetDirectly() {
        ColumnVisibilityToggler toggler = createAndAttach();
        Grid<String> grid = new Grid<>();
        Grid.Column<String> col = grid.addColumn(s -> s).setHeader("Name");
        col.setVisible(true);
        toggler.addColumn(col, "Name");
        col.setVisible(false);
        assertFalse(col.isVisible());
    }

    @Test
    void multipleColumns_allRegistered() {
        ColumnVisibilityToggler toggler = createAndAttach();
        Grid<String> grid = new Grid<>();
        Grid.Column<String> col1 = grid.addColumn(s -> s).setHeader("Alpha");
        Grid.Column<String> col2 = grid.addColumn(s -> s).setHeader("Beta");
        Grid.Column<String> col3 = grid.addColumn(s -> s).setHeader("Gamma");
        toggler.addColumn(col1, "Alpha");
        toggler.addColumn(col2, "Beta");
        toggler.addColumn(col3, "Gamma");
        toggler.removeColumn(col1);
        assertFalse(toggler.isEmpty());
        toggler.removeColumn(col2);
        toggler.removeColumn(col3);
        assertTrue(toggler.isEmpty());
    }
}
