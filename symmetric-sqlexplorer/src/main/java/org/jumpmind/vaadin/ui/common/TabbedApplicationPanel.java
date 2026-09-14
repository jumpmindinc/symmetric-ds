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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.tabs.TabsVariant;

public class TabbedApplicationPanel extends TabSheet {
    private static final long serialVersionUID = 1L;
    protected EnhancedTab mainTab;

    public TabbedApplicationPanel() {
        super();
        setSizeFull();
        addThemeVariants(TabsVariant.LUMO_SMALL);
        addSelectedTabChangeListener(event -> {
            if (tabs.getSelectedTab() != null) {
                Component selected = ((EnhancedTab) tabs.getSelectedTab()).getComponent();
                if (selected instanceof IUiPanel) {
                    ((IUiPanel) selected).selected();
                }
            }
        });
    }

    public void setMainTab(String caption, Icon icon, Component component) {
        if (component instanceof HasSize) {
            ((HasSize) component).setSizeFull();
        }
        this.mainTab = add(component, caption, icon, 0);
    }

    public void addCloseableTab(String caption, Icon icon, Component component, boolean setSizeFull) {
        EnhancedTab tab = getTab(caption);
        if (tab != null) {
            tabs.setSelectedTab(tab);
            return;
        }
        if (setSizeFull && component instanceof HasSize) {
            ((HasSize) component).setSizeFull();
        }
        tab = add(component, caption, icon, mainTab == null ? 0 : 1);
        tab.setCloseable(true);
        tabs.setSelectedTab(tab);
    }

    public void addCloseableTab(String caption, Icon icon, Component component) {
        addCloseableTab(caption, icon, component, true);
    }

    @Override
    public void remove(EnhancedTab tab) {
        Component component = tab.getComponent();
        if (!(component instanceof IUiPanel) || ((IUiPanel) component).closing()) {
            int tabCount = tabList.size();
            if (tab.isSelected() && tabCount > 1) {
                int index = tabList.indexOf(tab);
                if (index < tabCount - 1) {
                    tabs.setSelectedIndex(index + 1);
                } else {
                    tabs.setSelectedIndex(index - 1);
                }
            }
            tabs.remove(tab);
            tabList.remove(tab);
            if (tabCount <= 1) {
                content.removeAll();
            }
        }
    }
}
