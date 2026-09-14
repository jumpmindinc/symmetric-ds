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
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

public class Label extends Span {
    private static final long serialVersionUID = 1L;
    private Html html;
    private Component icon;

    public Label() {
        super();
        getElement().getStyle().set("display", "flex");
    }

    public Label(String text) {
        html = new Html("<div style='width: 100%'>" + text + "</div>");
        getElement().getStyle().set("display", "flex");
        add(html);
    }

    public Label(VaadinIcon icon, String text) {
        this(new Icon(icon), text);
    }

    public Label(String text, VaadinIcon icon) {
        this(text, new Icon(icon));
    }

    public Label(Icon icon, String text) {
        this(text);
        setLeftIcon(icon);
    }

    public Label(String text, Icon icon) {
        this(text);
        setRightIcon(icon);
    }

    public Label(Component component) {
        setText(component.getElement().getOuterHTML());
        getElement().getStyle().set("display", "flex");
    }

    public String getText() {
        return getElement().getProperty("innerHTML");
    }

    public void setText(String text) {
        if (html != null) {
            remove(html);
        }
        html = new Html("<div style='width: 100%'>" + text + "</div>");
        add(html);
    }

    public void setRightIcon(VaadinIcon icon) {
        setRightIcon(new Icon(icon));
    }

    public void setLeftIcon(VaadinIcon icon) {
        setLeftIcon(new Icon(icon));
    }

    public void setLeftIcon(Component icon) {
        if (this.icon != null) {
            remove(this.icon);
        }
        icon.getElement().getStyle().set("align-self", "flex-start");
        icon.getElement().getStyle().set("min-width", "24px");
        icon.getElement().getStyle().set("margin-right", "5px");
        this.icon = icon;
        addComponentAsFirst(icon);
    }

    public void setRightIcon(Component icon) {
        if (this.icon != null) {
            remove(this.icon);
        }
        icon.getElement().getStyle().set("align-self", "flex-end");
        icon.getElement().getStyle().set("min-width", "24px");
        icon.getElement().getStyle().set("margin-left", "5px");
        this.icon = icon;
        add(icon);
    }
}
