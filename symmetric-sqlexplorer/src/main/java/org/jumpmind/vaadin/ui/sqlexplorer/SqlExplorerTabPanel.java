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

import org.jumpmind.vaadin.ui.common.NotifyDialog;
import org.jumpmind.vaadin.ui.common.TabSheet;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.notification.NotificationVariant;

public class SqlExplorerTabPanel extends TabSheet {
    private static final long serialVersionUID = 1L;
    private SqlExplorer sqlExplorer;

    public SqlExplorerTabPanel(SqlExplorer sqlExplorer) {
        super();
        this.sqlExplorer = sqlExplorer;
        setSizeFull();
    }

    @Override
    public void remove(EnhancedTab tab) {
        Component component = tab.getComponent();
        if (component != null && component instanceof QueryPanel && ((QueryPanel) component).commitButtonValue) {
            NotifyDialog.show("Cannot Close Tab", "You must commit or rollback queries before closing this tab.", null,
                    NotificationVariant.LUMO_CONTRAST);
        } else {
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
                sqlExplorer.resetContentMenuBar();
            }
        }
    }
}
