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

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMode;

public class SqlEntryDialog extends ResizableDialog {
    private static final long serialVersionUID = 1L;
    protected AceEditor editor;

    public SqlEntryDialog(String sql) {
        super("Edit SQL");
        editor = CommonUiUtils.createAceEditor();
        editor.setMode(AceMode.sql);
        editor.setValue(sql);
        editor.setSizeFull();
        innerContent.add(editor);
        innerContent.expand(editor);
        buildButtonFooter(buildCloseButton());
    }

    public String getSQL() {
        return editor.getValue();
    }

    @Override
    protected boolean onClose() {
        return super.onClose();
    }
}
