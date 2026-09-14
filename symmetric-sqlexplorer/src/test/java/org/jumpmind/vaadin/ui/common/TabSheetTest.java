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
package org.jumpmind.vaadin.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

@ViewPackages(classes = { TabSheet.class })
class TabSheetTest extends BrowserlessTest {
    private TabSheet createAndAttach() {
        TabSheet tabSheet = new TabSheet();
        UI.getCurrent().getElement().insertChild(0, tabSheet.getElement());
        return tabSheet;
    }

    @Test
    void newTabSheet_hasNoTabs() {
        TabSheet tabSheet = createAndAttach();
        assertTrue(tabSheet.tabList.isEmpty());
    }

    @Test
    void addTab_tabCountIsOne() {
        TabSheet tabSheet = createAndAttach();
        tabSheet.add(new Span("content"), "Tab 1");
        assertEquals(1, tabSheet.tabList.size());
    }

    @Test
    void addTwoTabs_tabCountIsTwo() {
        TabSheet tabSheet = createAndAttach();
        tabSheet.add(new Span("content1"), "Tab 1");
        tabSheet.add(new Span("content2"), "Tab 2");
        assertEquals(2, tabSheet.tabList.size());
    }

    @Test
    void removeTab_tabCountDecreases() {
        TabSheet tabSheet = createAndAttach();
        tabSheet.add(new Span("content1"), "Tab 1");
        tabSheet.add(new Span("content2"), "Tab 2");
        tabSheet.remove("Tab 1");
        assertEquals(1, tabSheet.tabList.size());
    }

    @Test
    void setCloseable_flagIsSet() {
        TabSheet tabSheet = createAndAttach();
        tabSheet.setCloseable(true);
        assertTrue(tabSheet.closeable);
    }

    @Test
    void addTabs_firstTabIsSelected() {
        TabSheet tabSheet = createAndAttach();
        TabSheet.EnhancedTab tab1 = tabSheet.add(new Span("content1"), "Tab 1");
        tabSheet.add(new Span("content2"), "Tab 2");
        assertTrue(tab1.isSelected());
    }

    @Test
    void removeSelectedTab_nextTabBecomesSelected() {
        TabSheet tabSheet = createAndAttach();
        TabSheet.EnhancedTab tab1 = tabSheet.add(new Span("c1"), "Tab 1");
        tabSheet.add(new Span("c2"), "Tab 2");
        tabSheet.add(new Span("c3"), "Tab 3");
        tabSheet.tabs.setSelectedTab(tab1);
        tabSheet.remove(tab1);
        assertEquals(2, tabSheet.tabList.size());
        assertNotNull(tabSheet.getSelectedTab());
    }

    @Test
    void setCloseable_trueAndFalse_setsFlag() {
        TabSheet tabSheet = createAndAttach();
        tabSheet.setCloseable(true);
        assertTrue(tabSheet.closeable);
        tabSheet.setCloseable(false);
        assertFalse(tabSheet.closeable);
    }

    @Test
    void addTab_withIcon_tabIsAdded() {
        TabSheet tabSheet = createAndAttach();
        tabSheet.add(new Span("content"), "Icon Tab", new Icon(VaadinIcon.STAR));
        assertEquals(1, tabSheet.tabList.size());
    }

    @Test
    void addTwoTabs_removeFirstTab_countIsOne() {
        TabSheet tabSheet = createAndAttach();
        tabSheet.add(new Span("c1"), "Tab A");
        tabSheet.add(new Span("c2"), "Tab B");
        tabSheet.remove("Tab A");
        assertEquals(1, tabSheet.tabList.size());
    }

    @Test
    void constructorSelectedChangeListener_onTabSwitch_hidesOldContentAndShowsNew() {
        TabSheet tabSheet = createAndAttach();
        Span c1 = new Span("content1");
        Span c2 = new Span("content2");
        tabSheet.add(c1, "Tab 1");
        tabSheet.add(c2, "Tab 2");
        assertTrue(c1.isVisible());
        tabSheet.setSelectedTab("Tab 2");
        assertFalse(c1.isVisible());
        assertTrue(c2.isVisible());
    }

    @Test
    void getTab_byName_returnsTabWithMatchingName() {
        TabSheet tabSheet = createAndAttach();
        tabSheet.add(new Span("c1"), "Alpha");
        tabSheet.add(new Span("c2"), "Beta");
        assertNotNull(tabSheet.getTab("Alpha"));
        assertEquals("Alpha", tabSheet.getTab("Alpha").getName());
        assertNull(tabSheet.getTab("Nonexistent"));
    }

    @Test
    void getTabIndex_byComponent_returnsCorrectIndex() {
        TabSheet tabSheet = createAndAttach();
        Span c1 = new Span("c1");
        Span c2 = new Span("c2");
        tabSheet.add(c1, "Tab 1");
        tabSheet.add(c2, "Tab 2");
        assertEquals(0, tabSheet.getTabIndex(c1));
        assertEquals(1, tabSheet.getTabIndex(c2));
        assertEquals(-1, tabSheet.getTabIndex(new Span("unknown")));
    }

    @Test
    void addSelectedTabChangeListener_firesWhenTabSelectionChanges() {
        TabSheet tabSheet = createAndAttach();
        tabSheet.add(new Span("c1"), "Tab 1");
        tabSheet.add(new Span("c2"), "Tab 2");
        AtomicBoolean listenerFired = new AtomicBoolean(false);
        tabSheet.addSelectedTabChangeListener(event -> listenerFired.set(true));
        tabSheet.setSelectedTab("Tab 2");
        assertTrue(listenerFired.get());
    }
}
