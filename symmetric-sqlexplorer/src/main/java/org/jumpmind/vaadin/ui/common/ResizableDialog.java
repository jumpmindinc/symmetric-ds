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
package org.jumpmind.vaadin.ui.common;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class ResizableDialog extends Dialog {
    private static final long serialVersionUID = 1L;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected VerticalLayout innerContent;
    protected ShortcutRegistration escapeShortcutRegistration;

    public ResizableDialog() {
        this("");
    }

    public ResizableDialog(String caption) {
        this(caption, true);
    }

    public ResizableDialog(String caption, boolean addEscapeShortcut) {
        this(caption, addEscapeShortcut, false);
    }

    public ResizableDialog(String caption, boolean addEscapeShortcut, boolean addCloseIcon) {
        setModality(ModalityMode.STRICT);
        setResizable(true);
        VerticalLayout content = new VerticalLayout();
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(false);
        super.add(content);
        if (caption != null) {
            setHeaderTitle(caption);
        }
        if (addCloseIcon) {
            getHeader().add(buildCloseIcon());
        }
        innerContent = new VerticalLayout();
        innerContent.setWidthFull();
        innerContent.setMargin(false);
        innerContent.setSpacing(false);
        content.addAndExpand(innerContent);
        UI.getCurrent().addShortcutListener(() -> {
            if (!"100%".equals(getWidth())) {
                setWidth("100%");
                setHeight("100%");
            } else {
                setWidth(null);
                setHeight(null);
            }
        }, Key.KEY_M, KeyModifier.CONTROL);
        if (addEscapeShortcut) {
            escapeShortcutRegistration = UI.getCurrent().addShortcutListener(() -> close(), Key.ESCAPE);
        }
    }

    protected void add(Component component, int expandRatio) {
        innerContent.add(component);
        innerContent.setFlexGrow(expandRatio, component);
    }

    protected void add(Component component) {
        innerContent.add(component);
    }

    protected void addComponents(Component... components) {
        for (Component component : components) {
            innerContent.add(component);
        }
    }

    protected Icon buildCloseIcon() {
        Icon closeIcon = new Icon(VaadinIcon.CLOSE);
        closeIcon.setSize("36px");
        closeIcon.getStyle().set("min-width", "36px");
        closeIcon.setClassName("mouse_pointer");
        closeIcon.addClickListener(event -> close());
        return closeIcon;
    }

    protected Button buildCloseButton() {
        Button closeButton = new Button("Close");
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClickListener(new CloseButtonListener());
        closeButton.focus();
        return closeButton;
    }

    protected void buildButtonFooter(Component... toTheRightButtons) {
        buildButtonFooter((Component[]) null, toTheRightButtons);
    }

    protected void buildButtonFooter(List<Component> toTheLeftButtons, Component... toTheRightButtons) {
        buildButtonFooter(toTheLeftButtons.toArray(new Component[toTheLeftButtons.size()]), toTheRightButtons);
    }

    protected void buildButtonFooter(Component[] toTheLeftButtons, Component... toTheRightButtons) {
        if (toTheLeftButtons != null) {
            int buttonCount = toTheLeftButtons.length;
            if (buttonCount > 0) {
                for (int i = 0; i < buttonCount - 1; i++) {
                    getFooter().add(toTheLeftButtons[i]);
                }
                toTheLeftButtons[buttonCount - 1].getStyle().set("margin-right", "auto");
                getFooter().add(toTheLeftButtons[buttonCount - 1]);
            }
        }
        if (toTheRightButtons != null) {
            getFooter().add(toTheRightButtons);
        }
    }

    protected boolean onClose() {
        return true;
    }

    public void show() {
        open();
    }

    public void showAtSize(double percentOfBrowserSize) {
        UI.getCurrent().getPage().getExtendedClientDetails().refresh(details -> {
            setHeight((details.getWindowInnerHeight() * percentOfBrowserSize) + "px");
            setWidth((details.getWindowInnerWidth() * percentOfBrowserSize) + "px");
        });
        show();
    }

    public void bringToFront() {
        if (getElement().getNode().isAttached()) {
            super.getElement().executeJs("$0._bringOverlayToFront()");
        }
    }

    protected void enableEscapeShortcut(boolean enable) {
        if (enable && escapeShortcutRegistration == null) {
            escapeShortcutRegistration = UI.getCurrent().addShortcutListener(() -> close(), Key.ESCAPE);
        } else if (!enable && escapeShortcutRegistration != null) {
            escapeShortcutRegistration.remove();
            escapeShortcutRegistration = null;
        }
    }

    public class CloseButtonListener implements ComponentEventListener<ClickEvent<Button>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void onComponentEvent(ClickEvent<Button> event) {
            if (onClose()) {
                close();
            }
        }
    }
}
