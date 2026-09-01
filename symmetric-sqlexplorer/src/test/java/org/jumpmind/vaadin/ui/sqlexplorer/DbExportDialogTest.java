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
import org.jumpmind.symmetric.io.data.DbExport;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;

@ViewPackages(classes = { DbExportDialog.class })
class DbExportDialogTest extends BrowserlessTest {
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

    private DbExportDialog createAndOpen() {
        DbExportDialog dialog = new DbExportDialog(databasePlatform, queryPanel, null);
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
        assertTrue(find(Button.class).exists());
    }

    @Test
    void init_tableSelectionAreaIsPresent() {
        DbExportDialog dialog = createAndOpen();
        assertNotNull(dialog.nextButton);
    }

    @Test
    void init_nextButtonIsPresentAndVisible() {
        DbExportDialog dialog = createAndOpen();
        assertTrue(dialog.nextButton.isVisible());
    }

    @Test
    void init_formatSelectionIsPresent() {
        createAndOpen();
        assertTrue(find(ComboBox.class).exists());
    }

    @Test
    void init_doesNotThrowWithPreselectedTables() {
        assertDoesNotThrow(() -> {
            HashSet<Table> tables = new HashSet<>();
            tables.add(new Table("orders"));
            DbExportDialog dialog = new DbExportDialog(databasePlatform, tables, queryPanel, null);
            UI.getCurrent().getElement().insertChild(0, dialog.getElement());
            dialog.open();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void formatSelect_csvValue_disablesDataCheckbox() {
        DbExportDialog dialog = createAndOpen();
        dialog.next();
        ComboBox<DbExportDialog.DbExportFormat> formatSelect = find(ComboBox.class).all().stream()
                .filter(c -> "Format".equals(c.getLabel()))
                .map(c -> (ComboBox<DbExportDialog.DbExportFormat>) c)
                .findFirst().orElseThrow();
        formatSelect.setValue(DbExportDialog.DbExportFormat.CSV);
        Checkbox dataCheckbox = find(Checkbox.class).all().stream()
                .filter(c -> "Insert Data".equals(c.getLabel()))
                .findFirst().orElseThrow();
        assertFalse(dataCheckbox.isEnabled());
    }

    @Test
    void next_showsPreviousButtonAndHidesNextButton() throws Exception {
        DbExportDialog dialog = createAndOpen();
        dialog.next();
        assertFalse(dialog.nextButton.isVisible());
        Field prevField = DbExportDialog.class.getDeclaredField("previousButton");
        prevField.setAccessible(true);
        assertTrue(((Button) prevField.get(dialog)).isVisible());
    }

    @Test
    void previous_afterNext_showsNextButtonAndHidesPreviousButton() throws Exception {
        DbExportDialog dialog = createAndOpen();
        dialog.next();
        dialog.previous();
        assertTrue(dialog.nextButton.isVisible());
        Field prevField = DbExportDialog.class.getDeclaredField("previousButton");
        prevField.setAccessible(true);
        assertFalse(((Button) prevField.get(dialog)).isVisible());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setExportButtonsEnabled_editorMode_showsEditorButtonHidesFileDownloader() throws Exception {
        DbExportDialog dialog = createAndOpen();
        dialog.next();
        RadioButtonGroup<String> group = find(RadioButtonGroup.class).single();
        group.setValue("Export to the SQL Editor");
        Field editorButtonField = DbExportDialog.class.getDeclaredField("exportEditorButton");
        editorButtonField.setAccessible(true);
        assertTrue(((Button) editorButtonField.get(dialog)).isVisible());
        Field fileDownloaderField = DbExportDialog.class.getDeclaredField("fileDownloader");
        fileDownloaderField.setAccessible(true);
        assertFalse(((Anchor) fileDownloaderField.get(dialog)).isVisible());
    }

    @Test
    void createDbExport_setsFormatAndNoDataFromFieldValues() throws Exception {
        when(databasePlatform.getName()).thenReturn("H2");
        DbExportDialog dialog = createAndOpen();
        dialog.createDbExport();
        Field dbExportField = DbExportDialog.class.getDeclaredField("dbExport");
        dbExportField.setAccessible(true);
        DbExport dbExport = (DbExport) dbExportField.get(dialog);
        assertNotNull(dbExport);
        assertEquals(DbExport.Format.SQL, dbExport.getFormat());
        assertFalse(dbExport.isNoData());
    }
}
