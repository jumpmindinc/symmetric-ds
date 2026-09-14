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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.Serializable;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class PromptDialog extends Dialog {
    private static final long serialVersionUID = 1L;

    public PromptDialog(String caption, String text, final IPromptListener iPromptListener) {
        this(caption, text, null, iPromptListener);
    }

    public PromptDialog(String caption, String text, String defaultValue,
            final IPromptListener promptListener) {
        setModality(ModalityMode.STRICT);
        setResizable(false);
        setSizeUndefined();
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
        if (caption != null) {
            setHeaderTitle(caption);
        }
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setMargin(true);
        add(layout);
        if (isNotBlank(text)) {
            layout.add(new Span(text));
        }
        final TextField field = new TextField();
        field.setWidthFull();
        field.setValue(defaultValue);
        if (defaultValue != null) {
            UI.getCurrent().getPage().executeJs("$0.select();", field.getElement());
            field.focus();
        }
        layout.add(field);
        Button cancelButton = new Button("Cancel");
        cancelButton.addClickShortcut(Key.ESCAPE);
        cancelButton.addClickListener(event -> close());
        Button okButton = new Button("Ok");
        okButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        okButton.addClickShortcut(Key.ENTER);
        okButton.addClickListener(event -> {
            if (promptListener.onOk(field.getValue())) {
                close();
            }
        });
        getFooter().add(cancelButton, okButton);
        field.focus();
    }

    public static void prompt(String caption, String message, IPromptListener listener) {
        PromptDialog prompt = new PromptDialog(caption, message, listener);
        prompt.open();
    }

    public static interface IPromptListener extends Serializable {
        public boolean onOk(String content);
    }
}
