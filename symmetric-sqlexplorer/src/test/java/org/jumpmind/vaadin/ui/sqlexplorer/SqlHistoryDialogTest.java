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

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.Query;

@ViewPackages(classes = { SqlHistoryDialog.class })
class SqlHistoryDialogTest extends BrowserlessTest {
    private ISettingsProvider settingsProvider;
    private QueryPanel queryPanel;

    @BeforeEach
    void setUp() {
        settingsProvider = mock(ISettingsProvider.class);
        queryPanel = mock(QueryPanel.class);
        when(settingsProvider.get()).thenReturn(new Settings());
    }

    private SqlHistoryDialog createAndOpen() {
        SqlHistoryDialog dialog = new SqlHistoryDialog(settingsProvider, queryPanel);
        UI.getCurrent().getElement().insertChild(0, dialog.getElement());
        dialog.open();
        return dialog;
    }

    @Test
    void init_gridIsPresent() {
        createAndOpen();
        assertTrue(find(Grid.class).exists());
    }

    @Test
    void init_selectButtonIsPresent() {
        createAndOpen();
        assertTrue(find(Button.class).exists());
    }

    @Test
    @SuppressWarnings("unchecked")
    void init_withEmptyHistory_gridIsEmpty() {
        createAndOpen();
        assertEquals(0, find(Grid.class).single().getDataProvider().size(new Query<>()));
    }

    @Test
    void init_dialogOpensWithoutException() {
        assertDoesNotThrow(this::createAndOpen);
    }

    @Test
    @SuppressWarnings("unchecked")
    void onRefresh_withHistoryItems_gridShowsItems() {
        Settings settings = new Settings();
        SqlHistory history = new SqlHistory();
        history.setSqlStatement("SELECT 1");
        history.setLastExecuteTime(new Date());
        settings.getSqlHistory().add(history);
        when(settingsProvider.get()).thenReturn(settings);
        createAndOpen();
        assertTrue(find(Grid.class).single().getDataProvider().size(new Query<>()) > 0);
    }

    @Test
    void selectButton_isPresentAndEnabled() {
        createAndOpen();
        Button selectButton = find(Button.class).all().stream()
                .filter(b -> "Select".equals(b.getText()))
                .findFirst()
                .orElse(null);
        assertNotNull(selectButton);
        assertTrue(selectButton.isEnabled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void multipleHistoryItems_gridShowsMultipleRows() {
        Settings settings = new Settings();
        for (int i = 1; i <= 3; i++) {
            SqlHistory history = new SqlHistory();
            history.setSqlStatement("SELECT " + i);
            history.setLastExecuteTime(new Date(System.currentTimeMillis() + i * 1000L));
            settings.getSqlHistory().add(history);
        }
        when(settingsProvider.get()).thenReturn(settings);
        createAndOpen();
        assertTrue(find(Grid.class).single().getDataProvider().size(new Query<>()) >= 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectButton_appendsSqlToPanel() {
        Settings settings = new Settings();
        SqlHistory history = new SqlHistory();
        history.setSqlStatement("SELECT 1");
        history.setLastExecuteTime(new Date());
        settings.getSqlHistory().add(history);
        when(settingsProvider.get()).thenReturn(settings);
        SqlHistoryDialog dialog = createAndOpen();
        Grid<SqlHistory> grid = find(Grid.class).single();
        grid.select(grid.getDataProvider()
                .fetch(new Query<>())
                .findFirst()
                .orElseThrow());
        dialog.select();
        verify(queryPanel).appendSql(contains("SELECT 1"));
    }
}
