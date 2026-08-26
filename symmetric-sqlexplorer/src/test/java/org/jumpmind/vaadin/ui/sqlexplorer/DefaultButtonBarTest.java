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
import static org.mockito.Mockito.*;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.menubar.MenuBar;

@ViewPackages(classes = { DefaultButtonBar.class })
class DefaultButtonBarTest extends BrowserlessTest {
    private ISettingsProvider settingsProvider;
    private IDb db;
    private QueryPanel queryPanel;
    private DefaultButtonBar buttonBar;
    private MenuBar menuBar;

    @BeforeEach
    void setUp() {
        settingsProvider = mock(ISettingsProvider.class);
        db = mock(IDb.class);
        queryPanel = mock(QueryPanel.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class, RETURNS_DEEP_STUBS);
        when(db.getPlatform()).thenReturn(platform);
        when(settingsProvider.get()).thenReturn(new Settings());
        buttonBar = new DefaultButtonBar();
        buttonBar.init(db, settingsProvider, queryPanel);
        menuBar = new MenuBar();
        UI.getCurrent().getElement().insertChild(0, menuBar.getElement());
        buttonBar.populate(menuBar);
    }

    @Test
    void populate_commitButtonStartsDisabled() {
        assertFalse(buttonBar.commitButton.isEnabled());
    }

    @Test
    void populate_rollbackButtonStartsDisabled() {
        assertFalse(buttonBar.rollbackButton.isEnabled());
    }

    @Test
    void populate_executeAtCursorButtonStartsEnabled() {
        assertTrue(buttonBar.executeAtCursorButton.isEnabled());
    }

    @Test
    void populate_executeScriptButtonStartsEnabled() {
        assertTrue(buttonBar.executeScriptButton.isEnabled());
    }

    @Test
    void populate_historyButtonStartsEnabled() {
        assertTrue(buttonBar.historyButton.isEnabled());
    }

    @Test
    void setCommitButtonEnabled_true_enablesButton() {
        buttonBar.setCommitButtonEnabled(true);
        assertTrue(buttonBar.commitButton.isEnabled());
    }

    @Test
    void setCommitButtonEnabled_false_disablesButton() {
        buttonBar.setCommitButtonEnabled(true);
        buttonBar.setCommitButtonEnabled(false);
        assertFalse(buttonBar.commitButton.isEnabled());
    }

    @Test
    void setRollbackButtonEnabled_true_enablesButton() {
        buttonBar.setRollbackButtonEnabled(true);
        assertTrue(buttonBar.rollbackButton.isEnabled());
    }

    @Test
    void setExecuteAtCursorButtonEnabled_false_disablesButton() {
        buttonBar.setExecuteAtCursorButtonEnabled(false);
        assertFalse(buttonBar.executeAtCursorButton.isEnabled());
    }

    @Test
    void setExecuteScriptButtonEnabled_false_disablesButton() {
        buttonBar.setExecuteScriptButtonEnabled(false);
        assertFalse(buttonBar.executeScriptButton.isEnabled());
    }

    @Test
    void populate_withImportAllowed_importButtonEnabled() {
        assertTrue(buttonBar.importButton.isEnabled());
    }

    @Test
    void populate_withImportDisallowed_importButtonDisabled() {
        Settings noImport = new Settings();
        noImport.setAllowImport(false);
        when(settingsProvider.get()).thenReturn(noImport);
        DefaultButtonBar bar = new DefaultButtonBar();
        bar.init(db, settingsProvider, queryPanel);
        MenuBar mb = new MenuBar();
        UI.getCurrent().getElement().insertChild(0, mb.getElement());
        bar.populate(mb);
        assertFalse(bar.importButton.isEnabled());
    }

    @Test
    void populate_withExportDisallowed_exportButtonDisabled() {
        Settings noExport = new Settings();
        noExport.setAllowExport(false);
        when(settingsProvider.get()).thenReturn(noExport);
        DefaultButtonBar bar = new DefaultButtonBar();
        bar.init(db, settingsProvider, queryPanel);
        MenuBar mb = new MenuBar();
        UI.getCurrent().getElement().insertChild(0, mb.getElement());
        bar.populate(mb);
        assertFalse(bar.exportButton.isEnabled());
    }

    @Test
    void populate_withFillDisallowed_fillButtonDisabled() {
        Settings noFill = new Settings();
        noFill.setAllowFill(false);
        when(settingsProvider.get()).thenReturn(noFill);
        DefaultButtonBar bar = new DefaultButtonBar();
        bar.init(db, settingsProvider, queryPanel);
        MenuBar mb = new MenuBar();
        UI.getCurrent().getElement().insertChild(0, mb.getElement());
        bar.populate(mb);
        assertFalse(bar.fillButton.isEnabled());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void fireClick(MenuItem item) {
        ComponentUtil.fireEvent(item, new ClickEvent(item, false, 0, 0, 0, 0, 1, 0, false, false, false, false));
    }

    @Test
    void executeAtCursorButton_click_callsRequestExecutionAtCursor() {
        fireClick(buttonBar.executeAtCursorButton);
        verify(queryPanel).requestExecutionAtCursor();
    }

    @Test
    void executeScriptButton_click_callsRequestScriptExecution() {
        fireClick(buttonBar.executeScriptButton);
        verify(queryPanel).requestScriptExecution();
    }

    @Test
    void commitButton_click_callsCommit() {
        fireClick(buttonBar.commitButton);
        verify(queryPanel).commit();
    }

    @Test
    void rollbackButton_click_callsRollback() {
        fireClick(buttonBar.rollbackButton);
        verify(queryPanel).rollback();
    }

    @Test
    void historyButton_click_opensSqlHistoryDialog() {
        fireClick(buttonBar.historyButton);
        assertTrue(find(SqlHistoryDialog.class).exists());
    }

    @Test
    void importButton_click_opensDbImportDialog() {
        fireClick(buttonBar.importButton);
        assertTrue(find(DbImportDialog.class).exists());
    }

    @Test
    void exportButton_click_opensDbExportDialog() {
        fireClick(buttonBar.exportButton);
        assertTrue(find(DbExportDialog.class).exists());
    }

    @Test
    void fillButton_click_opensDbFillDialog() {
        fireClick(buttonBar.fillButton);
        assertTrue(find(DbFillDialog.class).exists());
    }
}
