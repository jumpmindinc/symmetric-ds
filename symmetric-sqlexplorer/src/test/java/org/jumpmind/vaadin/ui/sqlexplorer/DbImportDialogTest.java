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
import org.jumpmind.symmetric.io.data.DbImport;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;

@ViewPackages(classes = { DbImportDialog.class })
class DbImportDialogTest extends BrowserlessTest {
    private IDatabasePlatform databasePlatform;

    @BeforeEach
    void setUp() {
        databasePlatform = mock(IDatabasePlatform.class);
        IDdlReader ddlReader = mock(IDdlReader.class);
        when(databasePlatform.getDdlReader()).thenReturn(ddlReader);
        when(databasePlatform.getDefaultCatalog()).thenReturn(null);
        when(databasePlatform.getDefaultSchema()).thenReturn(null);
        when(ddlReader.getCatalogNames()).thenReturn(Collections.emptyList());
        when(ddlReader.getSchemaNames(any())).thenReturn(Collections.emptyList());
        when(ddlReader.getRelationNames(any(), any(), any())).thenReturn(Collections.emptyList());
    }

    private DbImportDialog createAndOpen() {
        DbImportDialog dialog = new DbImportDialog(databasePlatform);
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
    void init_checkboxesArePresent() {
        createAndOpen();
        assertTrue($(Checkbox.class).exists());
    }

    @Test
    void init_formatSelectionIsPresent() {
        createAndOpen();
        assertTrue($(ComboBox.class).exists());
    }

    @Test
    void init_importButtonIsPresent() {
        createAndOpen();
        Button importButton = $(Button.class).all().stream()
                .filter(b -> "Import".equals(b.getText()))
                .findFirst()
                .orElse(null);
        assertNotNull(importButton);
    }

    @Test
    void init_doesNotThrowWithPreselectedTables() {
        assertDoesNotThrow(() -> {
            HashSet<Table> tables = new HashSet<>();
            tables.add(new Table("orders"));
            DbImportDialog dialog = new DbImportDialog(databasePlatform, tables);
            UI.getCurrent().getElement().insertChild(0, dialog.getElement());
            dialog.open();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void formatSelection_xml_disablesTableSelect() {
        createAndOpen();
        ComboBox<DbImportDialog.DbImportFormat> formatSelect = $(ComboBox.class).all().get(0);
        formatSelect.setValue(DbImportDialog.DbImportFormat.XML);
        ComboBox<String> tablesSelect = $(ComboBox.class).all().stream()
                .filter(c -> !c.isEnabled() && c != formatSelect)
                .findFirst()
                .orElse(null);
        assertNotNull(tablesSelect);
        assertFalse(tablesSelect.isEnabled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void formatSelect_sqlAfterXml_enablesIgnoreConflictsCheckbox() {
        createAndOpen();
        ComboBox<DbImportDialog.DbImportFormat> formatSelect = $(ComboBox.class).all().get(0);
        formatSelect.setValue(DbImportDialog.DbImportFormat.XML);
        formatSelect.setValue(DbImportDialog.DbImportFormat.SQL);
        Checkbox ignoreConflicts = $(Checkbox.class).all().stream()
                .filter(c -> "Skip rows with conflicts".equals(c.getLabel()))
                .findFirst().orElseThrow();
        assertTrue(ignoreConflicts.isEnabled());
    }

    @Test
    void importButtonEnable_sqlFormatWithDefaultCommitValue_returnsTrue() {
        DbImportDialog dialog = createAndOpen();
        assertTrue(dialog.importButtonEnable());
    }

    @Test
    @SuppressWarnings("unchecked")
    void importButtonEnable_csvFormatWithNoTableSelected_returnsFalse() {
        DbImportDialog dialog = createAndOpen();
        ComboBox<DbImportDialog.DbImportFormat> formatSelect = $(ComboBox.class).all().get(0);
        formatSelect.setValue(DbImportDialog.DbImportFormat.CSV);
        assertFalse(dialog.importButtonEnable());
    }

    @Test
    void importButtonEnable_emptyCommitField_returnsFalse() {
        DbImportDialog dialog = createAndOpen();
        TextField commitField = $(TextField.class).all().stream()
                .filter(f -> "Rows to Commit".equals(f.getLabel()))
                .findFirst().orElseThrow();
        commitField.setValue("");
        assertFalse(dialog.importButtonEnable());
    }

    @Test
    void createDbImport_setsFormatAndCommitRateFromFields() throws Exception {
        DbImportDialog dialog = createAndOpen();
        dialog.createDbImport();
        Field dbImportField = DbImportDialog.class.getDeclaredField("dbImport");
        dbImportField.setAccessible(true);
        DbImport dbImport = (DbImport) dbImportField.get(dialog);
        assertNotNull(dbImport);
        assertEquals(DbImport.Format.SQL, dbImport.getFormat());
        assertEquals(10000L, dbImport.getCommitRate());
    }
}
