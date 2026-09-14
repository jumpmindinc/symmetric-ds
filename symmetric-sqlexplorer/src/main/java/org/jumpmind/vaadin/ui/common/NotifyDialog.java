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

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.NotificationVariant;

public class NotifyDialog extends ResizableDialog {
    private static final long serialVersionUID = 1L;
    boolean detailsMode = false;

    public NotifyDialog(String text, Throwable ex) {
        this("Error", text, ex, NotificationVariant.LUMO_ERROR);
    }

    public NotifyDialog(String caption, String text, final Throwable ex, NotificationVariant type) {
        super(caption);
        setWidth("400px");
        setHeight("320px");
        final Scroller messageArea = new Scroller();
        messageArea.setSizeFull();
        text = isNotBlank(text) ? text
                : (ex != null ? ex.getMessage()
                        : "");
        if (type == NotificationVariant.LUMO_ERROR) {
            setHeaderTitle(null);
            Span captionSpan = new Span(caption);
            captionSpan.addComponentAsFirst(new Icon(VaadinIcon.BAN));
            getHeader().add(captionSpan);
        }
        final String message = text;
        final Label textLabel = new Label(message);
        messageArea.setContent(textLabel);
        innerContent.add(messageArea);
        innerContent.expand(messageArea);
        final Button detailsButton = new Button("Details");
        detailsButton.setVisible(ex != null);
        detailsButton.addClickListener(event -> {
            detailsMode = !detailsMode;
            if (detailsMode) {
                String msg = "<pre>" + ExceptionUtils.getStackTrace(ex).trim() + "</pre>";
                msg = msg.replace("\t", "    ");
                textLabel.setText(msg);
                detailsButton.setText("Message");
                messageArea.getStyle().set("margin", "0 0 0 16px");
                setHeight("600px");
                setWidth("1000px");
            } else {
                textLabel.setText(message);
                detailsButton.setText("Details");
                setWidth("400px");
                setHeight("320px");
            }
        });
        buildButtonFooter(detailsButton, buildCloseButton());
    }

    public static void show(String caption, String text, Throwable throwable, NotificationVariant type) {
        new NotifyDialog(caption, text, throwable, type).open();
    }
}
