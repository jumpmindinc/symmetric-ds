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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import org.jumpmind.symmetric.io.data.DbFill;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;

@ViewPackages(classes = { DbFillDialog.class })
class DbFillDialogTest extends BrowserlessTest {
    private IDatabasePlatform databasePlatform;
    private QueryPanel queryPanel;

    @BeforeEach
    void setUp() {
        databasePlatform = mock(IDatabasePlatform.class);
        queryPanel = mock(QueryPanel.class);
        IDdlReader ddlReader = mock(IDdlReader.class);
        when(databasePlatform.getDdlReader()).thenReturn(ddlReader);
        when(databasePlatform.getDefaultCatalog()).thenReturn(null);
        when(databasePlatform.getDefaultSchema()).thenReturn(null);
        when(ddlReader.getCatalogNames()).thenReturn(Collections.emptyList());
        when(ddlReader.getSchemaNames(any())).thenReturn(Collections.emptyList());
        when(ddlReader.getRelationNames(any(), any(), any())).thenReturn(Collections.emptyList());
    }

    private DbFillDialog createAndOpen() {
        DbFillDialog dialog = new DbFillDialog(databasePlatform, queryPanel, null);
        UI.getCurrent().getElement().insertChild(0, dialog.getElement());
        dialog.open();
        return dialog;
    }

    @Test
    void constructor_doesNotThrow() {
        assertDoesNotThrow(this::createAndOpen);
    }

    @Test
    void init_buttonsArePresent() {
        createAndOpen();
        assertTrue($(Button.class).exists());
    }

    @Test
    void init_tableSelectionIsPresent() {
        createAndOpen();
        assertTrue($(Grid.class).exists());
    }

    @Test
    void init_nextButtonIsPresentAndDisabled() {
        createAndOpen();
        Button nextButton = $(Button.class).all().stream()
                .filter(b -> "Next".equals(b.getText()))
                .findFirst()
                .orElse(null);
        assertNotNull(nextButton);
        assertFalse(nextButton.isEnabled());
    }

    @Test
    void init_doesNotThrowWithPreselectedTables() {
        assertDoesNotThrow(() -> {
            HashSet<Table> tables = new HashSet<>();
            tables.add(new Table("orders"));
            DbFillDialog dialog = new DbFillDialog(databasePlatform, tables, queryPanel, null);
            UI.getCurrent().getElement().insertChild(0, dialog.getElement());
            dialog.open();
        });
    }

    @Test
    void init_cancelButtonIsPresent() {
        createAndOpen();
        Button cancelButton = $(Button.class).all().stream()
                .filter(b -> "Close".equals(b.getText()))
                .findFirst()
                .orElse(null);
        assertNotNull(cancelButton);
    }

    @Test
    void enableFillButton_allFieldsPopulated_returnsTrue() {
        DbFillDialog dialog = createAndOpen();
        assertTrue(dialog.enableFillButton());
    }

    @Test
    void enableFillButton_emptyCountField_returnsFalse() throws Exception {
        DbFillDialog dialog = createAndOpen();
        Field countField = DbFillDialog.class.getDeclaredField("countField");
        countField.setAccessible(true);
        ((TextField) countField.get(dialog)).setValue("");
        assertFalse(dialog.enableFillButton());
    }

    @Test
    void next_showsFillButtonAndPreviousButton() throws Exception {
        DbFillDialog dialog = createAndOpen();
        dialog.next();
        Field fillButtonField = DbFillDialog.class.getDeclaredField("fillButton");
        fillButtonField.setAccessible(true);
        assertTrue(((Button) fillButtonField.get(dialog)).isVisible());
        Field prevButtonField = DbFillDialog.class.getDeclaredField("previousButton");
        prevButtonField.setAccessible(true);
        assertTrue(((Button) prevButtonField.get(dialog)).isVisible());
    }

    @Test
    void previous_afterNext_hidesFillButtonAndShowsNextButton() throws Exception {
        DbFillDialog dialog = createAndOpen();
        dialog.next();
        dialog.previous();
        Field fillButtonField = DbFillDialog.class.getDeclaredField("fillButton");
        fillButtonField.setAccessible(true);
        assertFalse(((Button) fillButtonField.get(dialog)).isVisible());
        Button nextButton = $(Button.class).all().stream()
                .filter(b -> "Next".equals(b.getText()))
                .findFirst().orElseThrow();
        assertTrue(nextButton.isVisible());
    }

    @Test
    void createDbFill_setsCountAndContinueFromFieldValues() throws Exception {
        DbFillDialog dialog = createAndOpen();
        Field countField = DbFillDialog.class.getDeclaredField("countField");
        countField.setAccessible(true);
        ((TextField) countField.get(dialog)).setValue("3");
        dialog.createDbFill();
        Field dbFillField = DbFillDialog.class.getDeclaredField("dbFill");
        dbFillField.setAccessible(true);
        DbFill dbFill = (DbFill) dbFillField.get(dialog);
        assertNotNull(dbFill);
        assertEquals(3, dbFill.getRecordCount());
    }

    @Test
    void enableShortcuts_afterNext_canToggleRegistrations() throws Exception {
        DbFillDialog dialog = createAndOpen();
        dialog.next();
        Field cancelReg = DbFillDialog.class.getDeclaredField("cancelShortcutRegistration");
        cancelReg.setAccessible(true);
        Field fillReg = DbFillDialog.class.getDeclaredField("fillShortcutRegistration");
        fillReg.setAccessible(true);
        assertNotNull(cancelReg.get(dialog));
        assertNotNull(fillReg.get(dialog));
        dialog.enableShortcuts(false);
        assertNull(cancelReg.get(dialog));
        assertNull(fillReg.get(dialog));
        dialog.enableShortcuts(true);
        assertNotNull(cancelReg.get(dialog));
        assertNotNull(fillReg.get(dialog));
    }
}
