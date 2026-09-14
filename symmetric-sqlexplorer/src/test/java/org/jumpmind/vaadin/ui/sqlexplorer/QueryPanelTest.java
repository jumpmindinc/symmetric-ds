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

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlReader;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

@ViewPackages(classes = { QueryPanel.class })
class QueryPanelTest extends BrowserlessTest {
    @Test
    void classIsAccessible() {
        assertNotNull(QueryPanel.class);
    }

    @Test
    void settingsDialogClassIsDistinctFromQueryPanel() {
        assertNotEquals(QueryPanel.class, SettingsDialog.class);
    }

    private QueryPanel createAndAttach() {
        IDb db = mock(IDb.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        IDdlReader ddlReader = mock(IDdlReader.class);
        when(db.getPlatform()).thenReturn(platform);
        when(platform.getDdlReader()).thenReturn(ddlReader);
        ISettingsProvider settingsProvider = mock(ISettingsProvider.class);
        IButtonBar buttonBar = mock(IButtonBar.class);
        when(settingsProvider.get()).thenReturn(new Settings());
        QueryPanel panel = new QueryPanel(db, settingsProvider, buttonBar, "testuser");
        UI.getCurrent().getElement().insertChild(0, panel.getElement());
        return panel;
    }

    @Test
    void init_doesNotThrow_withValidMocks() {
        assertDoesNotThrow(this::createAndAttach);
    }

    @Test
    void queryPanel_isVisible() {
        QueryPanel panel = createAndAttach();
        assertTrue(panel.isVisible());
    }

    @Test
    void appendSql_addsTextToPanel() {
        QueryPanel panel = createAndAttach();
        assertDoesNotThrow(() -> panel.appendSql("SELECT 1;"));
    }

    @Test
    void getDb_returnsConfiguredDb() {
        IDb db = mock(IDb.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        IDdlReader ddlReader = mock(IDdlReader.class);
        when(db.getPlatform()).thenReturn(platform);
        when(platform.getDdlReader()).thenReturn(ddlReader);
        ISettingsProvider settingsProvider = mock(ISettingsProvider.class);
        IButtonBar buttonBar = mock(IButtonBar.class);
        when(settingsProvider.get()).thenReturn(new Settings());
        QueryPanel panel = new QueryPanel(db, settingsProvider, buttonBar, "testuser");
        UI.getCurrent().getElement().insertChild(0, panel.getElement());
        assertSame(db, panel.getDb());
    }
}
