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

import java.lang.reflect.Field;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.jumpmind.vaadin.ui.common.NotifyDialog;
import org.jumpmind.vaadin.ui.common.TabSheet;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;

@ViewPackages(classes = { SqlExplorerTabPanel.class })
class SqlExplorerTabPanelTest extends BrowserlessTest {
    private int tabCount(SqlExplorerTabPanel panel) throws Exception {
        Field field = TabSheet.class.getDeclaredField("tabList");
        field.setAccessible(true);
        return ((List<?>) field.get(panel)).size();
    }

    private SqlExplorer explorer;

    private SqlExplorerTabPanel createAndAttach() {
        explorer = mock(SqlExplorer.class);
        SqlExplorerTabPanel panel = new SqlExplorerTabPanel(explorer);
        UI.getCurrent().getElement().insertChild(0, panel.getElement());
        return panel;
    }

    private QueryPanel createQueryPanel() {
        IDb db = mock(IDb.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(db.getPlatform()).thenReturn(platform);
        when(platform.getDdlReader()).thenReturn(mock(IDdlReader.class));
        ISettingsProvider sp = mock(ISettingsProvider.class);
        when(sp.get()).thenReturn(new Settings());
        return new QueryPanel(db, sp, mock(IButtonBar.class), "testuser");
    }

    @Test
    void constructor_doesNotThrow() {
        assertDoesNotThrow(this::createAndAttach);
    }

    @Test
    void constructor_initiallyEmpty() throws Exception {
        assertEquals(0, tabCount(createAndAttach()));
    }

    @Test
    void remove_withNonQueryPanelComponent_removesTab() throws Exception {
        SqlExplorerTabPanel panel = createAndAttach();
        panel.add(new Span("content"), "Tab1");
        assertEquals(1, tabCount(panel));
        panel.remove(panel.getTab("Tab1"));
        assertEquals(0, tabCount(panel));
    }

    @Test
    void remove_withQueryPanelCommitFalse_removesTab() throws Exception {
        SqlExplorerTabPanel panel = createAndAttach();
        QueryPanel queryPanel = createQueryPanel();
        queryPanel.commitButtonValue = false;
        panel.add(queryPanel, "Query1");
        assertEquals(1, tabCount(panel));
        panel.remove(panel.getTab("Query1"));
        assertEquals(0, tabCount(panel));
    }

    @Test
    void remove_withQueryPanelCommitTrue_blocksRemoval() throws Exception {
        SqlExplorerTabPanel panel = createAndAttach();
        QueryPanel queryPanel = createQueryPanel();
        queryPanel.commitButtonValue = true;
        panel.add(queryPanel, "Query1");
        assertEquals(1, tabCount(panel));
        panel.remove(panel.getTab("Query1"));
        assertEquals(1, tabCount(panel));
    }

    @Test
    void remove_withQueryPanelCommitTrue_opensNotifyDialog() {
        SqlExplorerTabPanel panel = createAndAttach();
        QueryPanel queryPanel = createQueryPanel();
        queryPanel.commitButtonValue = true;
        panel.add(queryPanel, "Query1");
        panel.remove(panel.getTab("Query1"));
        assertTrue(find(NotifyDialog.class).exists());
    }

    @Test
    void remove_lastTab_callsResetContentMenuBar() throws Exception {
        SqlExplorerTabPanel panel = createAndAttach();
        panel.add(new Span("content"), "OnlyTab");
        assertEquals(1, tabCount(panel));
        panel.remove(panel.getTab("OnlyTab"));
        verify(explorer).resetContentMenuBar();
    }

    @Test
    void remove_whenNotLastTab_doesNotCallResetContentMenuBar() throws Exception {
        SqlExplorerTabPanel panel = createAndAttach();
        panel.add(new Span("c1"), "Tab1");
        panel.add(new Span("c2"), "Tab2");
        panel.remove(panel.getTab("Tab1"));
        assertEquals(1, tabCount(panel));
        verify(explorer, never()).resetContentMenuBar();
    }

    @Test
    void remove_selectedTabWithNext_selectsRemainingTab() throws Exception {
        SqlExplorerTabPanel panel = createAndAttach();
        TabSheet.EnhancedTab tab1 = panel.add(new Span("c1"), "Tab1");
        panel.add(new Span("c2"), "Tab2");
        panel.add(new Span("c3"), "Tab3");
        panel.setSelectedTab("Tab1");
        panel.remove(tab1);
        assertEquals(2, tabCount(panel));
        assertNotNull(panel.getSelectedTab());
    }

    @Test
    void remove_selectedLastTab_selectsPreviousTab() throws Exception {
        SqlExplorerTabPanel panel = createAndAttach();
        panel.add(new Span("c1"), "Tab1");
        TabSheet.EnhancedTab tab2 = panel.add(new Span("c2"), "Tab2");
        panel.setSelectedTab("Tab2");
        panel.remove(tab2);
        assertEquals(1, tabCount(panel));
        assertNotNull(panel.getSelectedTab());
    }
}
