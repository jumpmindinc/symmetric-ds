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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.Tabs.SelectedChangeEvent;
import com.vaadin.flow.component.tabs.TabsVariant;

public class TabSheet extends Div {
    private static final long serialVersionUID = 1L;
    protected VerticalLayout layout;
    protected Tabs tabs;
    protected List<EnhancedTab> tabList;
    protected Div content;
    protected boolean closeable = false;

    public TabSheet() {
        super();
        layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setSpacing(false);
        layout.getThemeList().remove("padding");
        tabs = new Tabs();
        tabs.setWidthFull();
        tabList = new ArrayList<EnhancedTab>();
        content = new Div();
        content.setSizeFull();
        tabs.addSelectedChangeListener(event -> {
            if (event.getSelectedTab() != null) {
                content.getChildren().forEach(element -> element.setVisible(false));
                Component component = ((EnhancedTab) event.getSelectedTab()).getComponent();
                if (component != null) {
                    List<Component> l = content.getChildren().filter(e -> e.equals(component)).collect(Collectors.toList());
                    if (l != null && l.size() > 0) {
                        l.get(0).setVisible(true);
                    } else {
                        component.setVisible(true);
                        content.add(component);
                    }
                }
            }
        });
        layout.add(tabs, content);
        add(layout);
    }

    public EnhancedTab add(Component component, String name) {
        EnhancedTab newTab = new EnhancedTab(name, component);
        tabs.add(newTab);
        tabList.add(newTab);
        return newTab;
    }

    public EnhancedTab add(Component component, String name, Icon icon) {
        EnhancedTab newTab = new EnhancedTab(name, icon, component);
        tabs.add(newTab);
        tabList.add(newTab);
        return newTab;
    }

    public EnhancedTab add(Component component, String name, int index) {
        EnhancedTab newTab = new EnhancedTab(name, component);
        add(newTab, index);
        return newTab;
    }

    public EnhancedTab add(Component component, String name, Icon icon, int index) {
        EnhancedTab newTab = new EnhancedTab(name, icon, component);
        add(newTab, index);
        return newTab;
    }

    private void add(EnhancedTab tab, int index) {
        List<EnhancedTab> oldList = new ArrayList<EnhancedTab>(tabList);
        tabList.clear();
        tabs.removeAll();
        Iterator<EnhancedTab> tabIterator = oldList.iterator();
        for (int i = 0; i <= index || tabIterator.hasNext(); i++) {
            if (i == index || !tabIterator.hasNext()) {
                tabs.add(tab);
                tabList.add(tab);
                if (!tabIterator.hasNext()) {
                    break;
                }
            } else {
                EnhancedTab nextTab = tabIterator.next();
                tabs.add(nextTab);
                tabList.add(nextTab);
            }
        }
    }

    public void remove(String name) {
        EnhancedTab tab = getTab(name);
        if (tab != null) {
            remove(tab);
        }
    }

    public void remove(EnhancedTab tab) {
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

    public void setCloseable(boolean closeable) {
        this.closeable = closeable;
        for (EnhancedTab tab : tabList) {
            tab.setCloseable(closeable);
        }
    }

    public void addThemeVariants(TabsVariant... variants) {
        tabs.addThemeVariants(variants);
    }

    public void addSelectedTabChangeListener(ComponentEventListener<SelectedChangeEvent> listener) {
        tabs.addSelectedChangeListener(listener);
    }

    public EnhancedTab getSelectedTab() {
        return (EnhancedTab) tabs.getSelectedTab();
    }

    public EnhancedTab getTab(Component component) {
        if (component != null) {
            for (EnhancedTab tab : tabList) {
                if (component.equals(tab.getComponent())) {
                    return tab;
                }
            }
        }
        return null;
    }

    public EnhancedTab getTab(String name) {
        for (EnhancedTab tab : tabList) {
            if (tab.getName().equals(name)) {
                return tab;
            }
        }
        return null;
    }

    public EnhancedTab getTab(int index) {
        return tabList.get(index);
    }

    public int getTabIndex(Component component) {
        if (component != null) {
            for (int i = 0; i < tabList.size(); i++) {
                if (component.equals(tabList.get(i).getComponent())) {
                    return i;
                }
            }
        }
        return -1;
    }

    public int getTabCount() {
        return tabList.size();
    }

    public void setSelectedTab(String name) {
        EnhancedTab tab = getTab(name);
        if (tab != null) {
            tabs.setSelectedTab(tab);
        }
    }

    public void setSelectedTab(int index) {
        EnhancedTab tab = getTab(index);
        if (tab != null) {
            tabs.setSelectedTab(tab);
        }
    }

    public void setSelectedTab(Component component) {
        EnhancedTab tab = getTab(component);
        if (tab != null) {
            tabs.setSelectedTab(tab);
        }
    }

    public Iterator<Component> iterator() {
        return tabList.stream().map(EnhancedTab::getComponent).iterator();
    }

    public void setAutoselect(boolean autoselect) {
        tabs.setAutoselect(autoselect);
    }

    public class EnhancedTab extends Tab {
        private static final long serialVersionUID = 1L;
        private String name;
        private Icon icon;
        private Component component;
        private Icon closeIcon;

        public EnhancedTab(String name, Component component) {
            this(name, null, component);
        }

        public EnhancedTab(String name, Icon icon, Component component) {
            this.name = name;
            this.icon = icon;
            this.component = component;
            HorizontalLayout tabHeader = new HorizontalLayout();
            if (icon != null) {
                icon.setSize("16px");
                tabHeader.add(icon);
                tabHeader.setVerticalComponentAlignment(Alignment.CENTER, icon);
            }
            Span nameSpan = new Span(name);
            tabHeader.add(nameSpan);
            tabHeader.setVerticalComponentAlignment(Alignment.END, nameSpan);
            closeIcon = new Icon(VaadinIcon.CLOSE);
            closeIcon.setSize("16px");
            closeIcon.addClickListener(event -> TabSheet.this.remove(EnhancedTab.this));
            closeIcon.setVisible(closeable);
            tabHeader.add(closeIcon);
            tabHeader.setVerticalComponentAlignment(Alignment.CENTER, closeIcon);
            add(tabHeader);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Icon getIcon() {
            return icon;
        }

        public void setIcon(Icon icon) {
            this.icon = icon;
        }

        public Component getComponent() {
            return component;
        }

        public void setComponent(Component component) {
            this.component = component;
        }

        public void setCloseable(boolean closeable) {
            closeIcon.setVisible(closeable);
        }
    }
}
