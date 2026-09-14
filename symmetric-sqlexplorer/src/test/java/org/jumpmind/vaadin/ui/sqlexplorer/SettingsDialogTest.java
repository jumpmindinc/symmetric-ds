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
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.TextField;

@ViewPackages(classes = { SettingsDialog.class })
class SettingsDialogTest extends BrowserlessTest {
    private SqlExplorer explorer;
    private ISettingsProvider settingsProvider;

    @BeforeEach
    void setUp() {
        explorer = mock(SqlExplorer.class);
        settingsProvider = mock(ISettingsProvider.class);
        when(explorer.getSettingsProvider()).thenReturn(settingsProvider);
        when(settingsProvider.get()).thenReturn(new Settings());
    }

    private SettingsDialog createAndOpen() {
        SettingsDialog dialog = new SettingsDialog(explorer);
        UI.getCurrent().getElement().insertChild(0, dialog.getElement());
        dialog.open();
        return dialog;
    }

    @Test
    void init_textFieldsArePresent() {
        createAndOpen();
        assertTrue(find(TextField.class).exists());
    }

    @Test
    void init_checkboxesArePresent() {
        createAndOpen();
        assertTrue(find(Checkbox.class).exists());
    }

    @Test
    void init_createsWithoutException() {
        assertDoesNotThrow(this::createAndOpen);
    }

    @Test
    void init_doesNotThrowOnOpen() {
        assertDoesNotThrow(this::createAndOpen);
    }

    @Test
    void init_fieldsLoadedFromSettings() {
        Settings settings = new Settings();
        settings.getProperties().put(Settings.SQL_EXPLORER_DELIMITER, ";");
        when(settingsProvider.get()).thenReturn(settings);
        createAndOpen();
        TextField delimiterField = find(TextField.class).all().stream()
                .filter(f -> ";".equals(f.getValue()))
                .findFirst()
                .orElse(null);
        assertNotNull(delimiterField);
        assertEquals(";", delimiterField.getValue());
    }

    @Test
    void init_checkboxesReflectSettings() {
        Settings settings = new Settings();
        settings.getProperties().put(Settings.SQL_EXPLORER_SHOW_ROW_NUMBERS, "true");
        when(settingsProvider.get()).thenReturn(settings);
        createAndOpen();
        boolean anyChecked = find(Checkbox.class).all().stream().anyMatch(Checkbox::getValue);
        assertTrue(anyChecked);
    }

    @Test
    void saveButton_isPresentAndVisible() {
        createAndOpen();
        Button saveButton = find(Button.class).all().stream()
                .filter(b -> "Save".equals(b.getText()))
                .findFirst()
                .orElse(null);
        assertNotNull(saveButton);
        assertTrue(saveButton.isVisible());
    }

    @Test
    void init_defaultSettingsPopulateFields() {
        createAndOpen();
        boolean anyNonEmpty = find(TextField.class).all().stream()
                .anyMatch(f -> !f.getValue().isEmpty());
        assertTrue(anyNonEmpty);
    }

    @Test
    void save_writesDelimiterValueToSettings() {
        SettingsDialog dialog = createAndOpen();
        TextField delimiterField = find(TextField.class).all().stream()
                .filter(f -> "Delimiter".equals(f.getLabel()))
                .findFirst().orElseThrow();
        delimiterField.setValue(",");
        dialog.save();
        ArgumentCaptor<Settings> captor = ArgumentCaptor.forClass(Settings.class);
        verify(settingsProvider).save(captor.capture());
        assertEquals(",", captor.getValue().getProperties().getProperty(Settings.SQL_EXPLORER_DELIMITER));
    }
}
