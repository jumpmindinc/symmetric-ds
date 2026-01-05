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

import static org.apache.commons.lang3.StringUtils.abbreviate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.util.FormatUtils;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.theme.lumo.Lumo;

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceTheme;

public final class CommonUiUtils {
    static final FastDateFormat DATETIMEFORMAT = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");
    static final FastDateFormat TIMEFORMAT = FastDateFormat.getInstance("HH:mm:ss.SSS");
    public static final String NULL_TEXT = "<null>";

    private CommonUiUtils() {
    }

    public static void styleTabSheet(TabSheet tabSheet) {
        tabSheet.setSizeFull();
        tabSheet.addThemeVariants(TabsVariant.LUMO_SMALL);
    }

    public static TabSheet createTabSheet() {
        TabSheet tabSheet = new TabSheet();
        styleTabSheet(tabSheet);
        return tabSheet;
    }

    public static Button createPrimaryButton(String name) {
        return createPrimaryButton(name, null);
    }

    public static Button createPrimaryButton(String name, ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(name);
        if (listener != null) {
            button.addClickListener(listener);
        }
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return button;
    }

    public static AceEditor createAceEditor() {
        AceEditor editor = new AceEditor();
        editor.setSizeFull();
        editor.setHighlightActiveLine(false);
        editor.setHighlightSelectedWord(false);
        editor.setShowPrintMargin(false);
        editor.setBaseUrl("../ace-builds/src-min-noconflict/");
        UI ui = UI.getCurrent();
        if (ui.getElement().getThemeList().contains(Lumo.DARK)) {
            editor.setTheme(AceTheme.nord_dark);
        } else {
            editor.setTheme(AceTheme.eclipse);
        }
        ComponentUtil.addListener(ui, ThemeChangedEvent.class, (event) -> {
            if (event.getTheme().equals(Lumo.DARK)) {
                editor.setTheme(AceTheme.nord_dark);
            } else {
                editor.setTheme(AceTheme.eclipse);
            }
        });
        return editor;
    }

    public static Notification notify(String message) {
        return notify("", message);
    }

    public static Notification notify(String message, Consumer<Boolean> shortcutToggler) {
        return notify("", message, shortcutToggler);
    }

    public static Notification notify(String caption, String message) {
        return notify(caption, message, null);
    }

    public static Notification notify(String caption, String message, Consumer<Boolean> shortcutToggler) {
        Page page = UI.getCurrent().getPage();
        if (page != null) {
            HorizontalLayout layout = new HorizontalLayout();
            Notification notification = new CenteredNotification(layout);
            if (message != null && message.length() <= 250) {
                layout.getStyle().set("max-width", "400px");
                Label label;
                if (!StringUtils.isBlank(caption)) {
                    label = new Label(caption + "<hr>" + contactWithLineFeed(FormatUtils.wordWrap(message, 150)));
                } else {
                    label = new Label(contactWithLineFeed(FormatUtils.wordWrap(message, 150)));
                }
                layout.add(label);
            } else {
                layout.setWidth("700px");
                VerticalLayout vLayout = new VerticalLayout();
                vLayout.setWidthFull();
                if (!StringUtils.isBlank(caption)) {
                    Label label = new Label(caption + "<hr>");
                    label.setWidthFull();
                    vLayout.add(label);
                }
                if (message != null) {
                    TextArea textArea = new TextArea();
                    textArea.setWidthFull();
                    textArea.setHeight("400px");
                    textArea.setValue(message);
                    textArea.setReadOnly(true);
                    vLayout.add(textArea);
                    layout.add(vLayout);
                }
            }
            Icon closeIcon = new Icon(VaadinIcon.CLOSE_SMALL);
            closeIcon.setSize("16px");
            closeIcon.getStyle().set("position", "absolute").set("top", "50%").set("left", "50%").set("transform",
                    "translate(-50%, -50%)");
            Div closeDiv = new Div(closeIcon);
            closeDiv.setHeight("24px");
            closeDiv.setWidth("24px");
            closeDiv.getStyle().set("min-height", "24px").set("min-width", "24px").set("position", "relative")
                    .set("-webkit-border-radius", "50%").set("-moz-border-radius", "50%").set("border-radius", "50%")
                    .set("background-color", "var(--lumo-contrast-10pct)").set("cursor", "pointer");
            closeDiv.addClickListener(event -> notification.close());
            layout.add(closeDiv);
            layout.setVerticalComponentAlignment(Alignment.START, closeDiv);
            if (shortcutToggler != null) {
                notification.addOpenedChangeListener(event -> shortcutToggler.accept(event.isOpened()));
            }
            notification.setDuration(30000);
            Shortcuts.addShortcutListener(notification, () -> notification.close(), Key.ESCAPE);
            notification.open();
            return notification;
        }
        return null;
    }

    private static String contactWithLineFeed(String[] lines) {
        StringBuilder line = new StringBuilder();
        for (String l : lines) {
            line.append(l).append("\n");
        }
        return line.toString();
    }

    public static Notification notifyError() {
        return notifyError((Consumer<Boolean>) null);
    }

    public static Notification notifyError(Consumer<Boolean> shortcutToggler) {
        return notify("An unexpected error occurred", "See the log file for additional details", shortcutToggler);
    }

    public static Notification notifyError(String message) {
        return notifyError(message, null);
    }

    public static Notification notifyError(String message, Consumer<Boolean> shortcutToggler) {
        return notify("An error occurred", message, shortcutToggler);
    }

    public static Object getObject(ResultSet rs, int i) throws SQLException {
        Object obj = JdbcSqlTemplate.getResultSetValue(rs, rs.getMetaData(), i, false, false);
        if (obj instanceof byte[]) {
            obj = new String(Hex.encodeHex((byte[]) obj));
        }
        if (obj instanceof String) {
            obj = abbreviate((String) obj, 1024 * 4);
        }
        return obj;
    }

    public static String[] getHeaderCaptions(Grid<?> grid) {
        List<String> headers = new ArrayList<String>();
        for (Column<?> column : grid.getColumns()) {
            headers.add(column.getKey());
        }
        return headers.toArray(new String[headers.size()]);
    }

    public static Grid<List<Object>> putResultsInGrid(ColumnVisibilityToggler columnVisibilityToggler, final ResultSet rs,
            int maxResultSize, final boolean showRowNumbers, String... excludeValues) throws SQLException {
        final Grid<List<Object>> grid = new Grid<List<Object>>();
        grid.setSelectionMode(SelectionMode.MULTI);
        grid.setColumnReorderingAllowed(true);
        grid.addItemClickListener(event -> {
            if (event.getColumn() != null) {
                grid.deselectAll();
                grid.select(event.getItem());
            }
        });
        List<List<Object>> outerList = new ArrayList<List<Object>>();
        if (rs != null) {
            grid.addColumn(row -> {
                return outerList.indexOf(row) + 1;
            }).setHeader("#").setKey("#").setFrozen(true).setResizable(true).setVisible(showRowNumbers);
            grid.addAttachListener(e -> {
                grid.getElement().executeJs("this.querySelector('vaadin-grid-flow-selection-column').frozen = true");
            });
            final ResultSetMetaData meta = rs.getMetaData();
            int totalColumns = meta.getColumnCount();
            Set<Integer> skipColumnIndexes = new HashSet<Integer>();
            Set<String> columnNames = new HashSet<String>();
            int[] types = new int[totalColumns];
            final int[] columnCounter = { 1 };
            while (columnCounter[0] <= totalColumns) {
                String realColumnName = meta.getColumnName(columnCounter[0]);
                String columnName = realColumnName;
                if (!Arrays.asList(excludeValues).contains(columnName)) {
                    int index = 1;
                    while (columnNames.contains(columnName)) {
                        columnName = realColumnName + "_" + index++;
                    }
                    columnNames.add(columnName);
                    int colNum = columnCounter[0] - 1 - skipColumnIndexes.size();
                    columnVisibilityToggler.addColumn(grid.addColumn(row -> row.get(colNum)).setKey(columnName)
                            .setHeader(columnName).setPartNameGenerator(row -> {
                                if (row.get(colNum) == null) {
                                    return "italics";
                                }
                                return null;
                            }).setResizable(true), columnName);
                    types[columnCounter[0] - 1] = meta.getColumnType(columnCounter[0]);
                } else {
                    skipColumnIndexes.add(columnCounter[0] - 1);
                }
                columnCounter[0]++;
            }
            for (int rowNumber = 1; rs.next() && rowNumber <= maxResultSize; rowNumber++) {
                List<Object> innerList = new ArrayList<Object>();
                for (int i = 0; i < totalColumns; i++) {
                    if (!skipColumnIndexes.contains(i)) {
                        Object o = getObject(rs, i + 1);
                        int type = types[i];
                        switch (type) {
                            case Types.FLOAT:
                            case Types.DOUBLE:
                            case Types.REAL:
                            case Types.NUMERIC:
                            case Types.DECIMAL:
                                if (o != null && !(o instanceof BigDecimal)) {
                                    o = new BigDecimal(castToNumber(o.toString()));
                                }
                                break;
                            case Types.TINYINT:
                            case Types.SMALLINT:
                            case Types.BIGINT:
                            case Types.INTEGER:
                                if (o != null && !(o instanceof Long)) {
                                    o = Long.parseLong(castToNumber(o.toString()));
                                }
                                break;
                            default:
                                break;
                        }
                        innerList.add(o == null ? NULL_TEXT : o);
                    }
                }
                outerList.add(innerList);
                if (rowNumber < 100) {
                    grid.getColumnByKey("#").setWidth("75px");
                } else if (rowNumber < 1000) {
                    grid.getColumnByKey("#").setWidth("95px");
                } else {
                    grid.getColumnByKey("#").setWidth("115px");
                }
            }
        } else {
            grid.addColumn(row -> row.get(0)).setHeader("Status").setKey("Status").setResizable(true);
            List<Object> innerList = new ArrayList<Object>();
            innerList.add("Metadata unavailable");
            outerList.add(innerList);
        }
        grid.setItems(outerList);
        return grid;
    }

    public static String castToNumber(String value) {
        if ("NO".equalsIgnoreCase(value) || "FALSE".equalsIgnoreCase(value)) {
            return "0";
        } else if ("YES".equalsIgnoreCase(value) || "TRUE".equalsIgnoreCase(value)) {
            return "1";
        } else {
            return value.replace(",", ".");
        }
    }

    public static String formatDuration(long timeInMs) {
        if (timeInMs > 60000) {
            long minutes = timeInMs / 60000;
            long seconds = (timeInMs - (minutes * 60000)) / 1000;
            return minutes + " m " + seconds + " s";
        } else if (timeInMs > 1000) {
            long seconds = timeInMs / 1000;
            return seconds + " s";
        } else {
            return timeInMs + " ms";
        }
    }

    public static String formatDateTime(Date dateTime) {
        if (dateTime != null) {
            Calendar cal = Calendar.getInstance();
            Calendar ref = Calendar.getInstance();
            ref.setTime(dateTime);
            if (ref.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && ref.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) {
                return TIMEFORMAT.format(dateTime);
            } else {
                return DATETIMEFORMAT.format(dateTime);
            }
        } else {
            return null;
        }
    }

    public static String getJdbcTypeValue(String type) {
        String value = null;
        if (type.equalsIgnoreCase("CHAR")) {
            value = "";
        } else if (type.equalsIgnoreCase("VARCHAR")) {
            value = "";
        } else if (type.equalsIgnoreCase("LONGVARCHAR")) {
            value = "";
        } else if (type.equalsIgnoreCase("NUMERIC")) {
            value = "0";
        } else if (type.equalsIgnoreCase("DECIMAL")) {
            value = "0.00";
        } else if (type.equalsIgnoreCase("BIT")) {
            value = "0";
        } else if (type.equalsIgnoreCase("BOOLEAN")) {
            value = "0";
        } else if (type.equalsIgnoreCase("TINYINT")) {
            value = "0";
        } else if (type.equalsIgnoreCase("SMALLINT")) {
            value = "0";
        } else if (type.equalsIgnoreCase("INTEGER")) {
            value = "0";
        } else if (type.equalsIgnoreCase("BIGINT")) {
            value = "0";
        } else if (type.equalsIgnoreCase("REAL")) {
            value = "0";
        } else if (type.equalsIgnoreCase("DOUBLE")) {
            value = "0.0";
        } else if (type.equalsIgnoreCase("BINARY")) {
            value = null;
        } else if (type.equalsIgnoreCase("VARBINARY")) {
            value = null;
        } else if (type.equalsIgnoreCase("LONGBINARY")) {
            value = null;
        } else if (type.equalsIgnoreCase("DATE")) {
            value = "'2014-07-08'";
        } else if (type.equalsIgnoreCase("TIME")) {
            value = "'12:00:00'";
        } else if (type.equalsIgnoreCase("TIMESTAMP")) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date();
            value = dateFormat.format(date);
        } else if (type.equalsIgnoreCase("CLOB")) {
            value = "''";
        } else if (type.equalsIgnoreCase("BLOB")) {
            value = "''";
        } else if (type.equalsIgnoreCase("ARRAY")) {
            value = "[]";
        } else if (type.equalsIgnoreCase("DISTINCT")) {
            value = null;
        } else if (type.equalsIgnoreCase("STRUCT")) {
            value = null;
        } else if (type.equalsIgnoreCase("REF")) {
            value = null;
        } else if (type.equalsIgnoreCase("DATALINK")) {
            value = null;
        } else if (type.equalsIgnoreCase("JAVA_OBJECT")) {
            value = null;
        } else {
            value = null;
        }
        return value;
    }

    public static Span createSeparator() {
        Span separator = new Span(" ");
        separator.setClassName("vrule");
        separator.setHeightFull();
        separator.setWidth(null);
        return separator;
    }

    public static void configureEditor(Grid<?> grid) {
        @SuppressWarnings("unchecked")
        Editor<Object> editor = (Editor<Object>) grid.getEditor();
        Collection<Button> editButtons = Collections.newSetFromMap(new WeakHashMap<>());
        editor.setBuffered(true);
        Column<?> editorColumn = grid.addComponentColumn(item -> {
            Button edit = new Button("Edit");
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL);
            edit.addClassName("edit");
            edit.addClickListener(event -> editor.editItem(item));
            edit.setEnabled(!editor.isOpen());
            editButtons.add(edit);
            return edit;
        }).setWidth("175px").setFlexGrow(0);
        editor.addOpenListener(event -> editButtons.stream().forEach(button -> button.setEnabled(!editor.isOpen())));
        editor.addCloseListener(event -> editButtons.stream().forEach(button -> button.setEnabled(!editor.isOpen())));
        Button save = new Button("Save", event -> editor.save());
        Button cancel = new Button("Cancel", event -> editor.cancel());
        cancel.getStyle().set("margin-left", "8px");
        grid.getElement().addEventListener("keyup", event -> editor.cancel())
                .setFilter("event.key === 'Escape' || event.key === 'Esc'");
        Div buttons = new Div(save, cancel);
        editorColumn.setEditorComponent(buttons);
    }

    public static Icon createMenuBarIcon(VaadinIcon icon) {
        Icon menuBarIcon = new Icon(icon);
        menuBarIcon.getStyle().set("padding", "var(--lumo-space-xs)");
        menuBarIcon.getStyle().set("box-sizing", "border-box");
        return menuBarIcon;
    }

    public static boolean isFilteredOut(String text, String filter) {
        if (StringUtils.isEmpty(filter)) {
            return false;
        }
        return filter.length() > 2 ? !Strings.CI.contains(text, filter) : !Strings.CI.startsWith(text, filter);
    }
}
