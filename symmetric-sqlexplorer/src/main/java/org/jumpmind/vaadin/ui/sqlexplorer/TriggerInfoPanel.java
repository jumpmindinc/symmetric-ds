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

import java.util.Arrays;
import java.util.Iterator;

import org.jumpmind.db.model.Trigger;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.TabSheet;
import org.jumpmind.vaadin.ui.common.TabSheet.EnhancedTab;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMode;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class TriggerInfoPanel extends VerticalLayout implements IInfoPanel {
    private static final long serialVersionUID = 1L;
    TabSheet tabSheet;
    String selectedCaption;
    boolean wrapSourceText;

    public TriggerInfoPanel(Trigger trigger, IDb db, Settings settings, String selectedTabCaption) {
        setSizeFull();
        tabSheet = CommonUiUtils.createTabSheet();
        tabSheet.addSelectedTabChangeListener(event -> {
            EnhancedTab tab = tabSheet.getSelectedTab();
            if (tab != null) {
                selectedCaption = tab.getName();
            }
        });
        add(tabSheet);
        refreshSource(trigger);
        refreshDetails(trigger, db, settings);
        Iterator<Component> i = tabSheet.iterator();
        while (i.hasNext()) {
            Component component = i.next();
            EnhancedTab tab = tabSheet.getTab(component);
            if (tab.getName().equals(selectedTabCaption)) {
                tabSheet.setSelectedTab(component);
                break;
            }
        }
    }

    public String getSelectedTabCaption() {
        return selectedCaption;
    }

    protected void refreshSource(final Trigger trigger) {
        VerticalLayout source = new VerticalLayout();
        source.setSizeFull();
        source.setSpacing(false);
        String sourceText = trigger.getSource();
        if (wrapSourceText)
            sourceText = wrapSource(sourceText);
        AceEditor editor = CommonUiUtils.createAceEditor();
        editor.setMode(AceMode.sql);
        editor.setValue(sourceText);
        editor.setSizeFull();
        source.addAndExpand(editor);
        HorizontalLayout bar = new HorizontalLayout();
        bar.setWidthFull();
        bar.getStyle().set("margin", "0 16px");
        MenuBar wrapSelect = new MenuBar();
        wrapSelect.addThemeVariants(MenuBarVariant.LUMO_TERTIARY, MenuBarVariant.LUMO_SMALL);
        MenuItem wrapButton = wrapSelect.addItem("Wrap text", event -> {
            wrapSourceText = !wrapSourceText;
            tabSheet.remove(tabSheet.getTab(1));
            refreshSource(trigger);
        });
        wrapButton.addComponentAsFirst(new Icon(VaadinIcon.ALIGN_JUSTIFY));
        bar.add(wrapSelect);
        bar.setVerticalComponentAlignment(Alignment.START, wrapSelect);
        bar.setHeight("2.5rem");
        source.add(bar);
        tabSheet.add(source, "Source");
        tabSheet.setSelectedTab(source);
    }

    private String wrapSource(String source) {
        String wrappedText = source;
        wrappedText = " " + wrappedText.replace('\n', ' ').trim() + " ";
        while (true) {
            String moreWrapped = wrappedText.replace("  ", " ");
            if (moreWrapped.equals(wrappedText)) {
                break;
            }
            wrappedText = moreWrapped;
        }
        int indentCount = 0, lastNewLineIndex = 0;
        boolean singleQuote = false, doubleQuote = false;
        String[] incIndentBeforeTerms = {},
                incIndentAfterTerms = { "begin", "case when", "delete from", "drop", "else", "if",
                        "select" },
                decIndentBeforeTerms = { "end if", "end", "else" },
                decIndentAfterTerms = {},
                noIndentTerms = { "after", "before", "call", "create", "declare", "for each row",
                        "instead of", "return", "start", "then", "when", "where" },
                noNewLineAfterTerms = { "after", "before", "call", "create", "declare", "delete from",
                        "drop", "if", "instead of", "select", "start" },
                noNewLineBeforeTerms = { "then" },
                allTerms = concatAll(incIndentBeforeTerms, incIndentAfterTerms, decIndentBeforeTerms,
                        decIndentAfterTerms, noIndentTerms);
        indexLoop: for (int i = 0; i < wrappedText.length() - 1; i++) {
            if (wrappedText.charAt(i) == '\"') {
                if (singleQuote || (doubleQuote && wrappedText.charAt(i - 1) != '\\')) {
                    doubleQuote = false;
                } else {
                    doubleQuote = true;
                }
            } else if (wrappedText.charAt(i) == '\'') {
                if (doubleQuote || (singleQuote && wrappedText.charAt(i - 1) != '\\')) {
                    singleQuote = false;
                } else {
                    singleQuote = true;
                }
            }
            boolean isInQuotes = singleQuote || doubleQuote;
            if (!isInQuotes && wrappedText.charAt(i) == ';') {
                wrappedText = insertNewLine(wrappedText, i, indentCount);
                lastNewLineIndex = i;
                i += 4 * indentCount;
            } else if (!isInQuotes && wrappedText.charAt(i) == '(') {
                indentCount += 2;
            } else if (!isInQuotes && wrappedText.charAt(i) == ')') {
                indentCount -= 2;
            }
            if (i - lastNewLineIndex > 100) {
                if (isInQuotes) {
                    int lookBack = 0;
                    while (lookBack < i &&
                            wrappedText.charAt(i - lookBack) != '\"' &&
                            wrappedText.charAt(i - lookBack) != '\'') {
                        lookBack++;
                    }
                    if (i - lookBack <= lastNewLineIndex + 1) {
                        continue indexLoop;
                    } else {
                        wrappedText = insertNewLine(wrappedText, i - lookBack, indentCount);
                        lastNewLineIndex = i - lookBack;
                        i += 4 * indentCount + 1;
                        continue indexLoop;
                    }
                } else {
                    int lookBack = 0;
                    while (lookBack < i &&
                            wrappedText.charAt(i - lookBack) != ',' &&
                            wrappedText.charAt(i - lookBack) != '.' &&
                            wrappedText.charAt(i - lookBack) != ' ' &&
                            wrappedText.charAt(i - lookBack) != '\"' &&
                            wrappedText.charAt(i - lookBack) != '\'') {
                        lookBack++;
                    }
                    if (i - lookBack <= lastNewLineIndex + 1) {
                        continue indexLoop;
                    } else {
                        wrappedText = insertNewLine(wrappedText, i - lookBack + 1, indentCount);
                        lastNewLineIndex = i - lookBack + 1;
                        i += 4 * indentCount + 1;
                        continue indexLoop;
                    }
                }
            }
            for (String term : allTerms) {
                if (i + term.length() < wrappedText.length() && !isInQuotes &&
                        termAppearsIn(wrappedText, term, i)) {
                    if (Arrays.asList(incIndentBeforeTerms).contains(term)) {
                        indentCount++;
                    } else if (Arrays.asList(decIndentBeforeTerms).contains(term)) {
                        indentCount--;
                    }
                    if (!Arrays.asList(noNewLineBeforeTerms).contains(term)) {
                        wrappedText = insertNewLine(wrappedText, i, indentCount);
                        lastNewLineIndex = i;
                        i += 4 * indentCount;
                    }
                    i += term.length();
                    if (Arrays.asList(incIndentAfterTerms).contains(term)) {
                        indentCount++;
                    } else if (Arrays.asList(decIndentAfterTerms).contains(term)) {
                        indentCount--;
                    }
                    if (!Arrays.asList(noNewLineAfterTerms).contains(term)) {
                        wrappedText = insertNewLine(wrappedText, i + 1, indentCount);
                        lastNewLineIndex = i;
                        i += 4 * indentCount;
                    }
                    i--;
                    continue indexLoop;
                }
            }
        }
        return wrappedText.trim();
    }

    private String[] concatAll(String[]... arrays) {
        int size = 0, index = 0;
        for (String[] array : arrays) {
            size += array.length;
        }
        String[] all = new String[size];
        for (String[] array : arrays) {
            System.arraycopy(array, 0, all, index, array.length);
            index += array.length;
        }
        return all;
    }

    private boolean termAppearsIn(String str, String term, int index) {
        return str.regionMatches(true, index, term, 0, term.length()) &&
                (index == 0 || !(Character.isLetter(str.charAt(index - 1)) ||
                        str.charAt(index - 1) == '@' ||
                        str.charAt(index - 1) == '$' ||
                        str.charAt(index - 1) == '#' ||
                        str.charAt(index - 1) == '_')) &&
                (index + term.length() >= str.length() || !(Character.isLetter(str.charAt(index + term.length())) ||
                        str.charAt(index + term.length()) == '@' ||
                        str.charAt(index + term.length()) == '$' ||
                        str.charAt(index + term.length()) == '#' ||
                        str.charAt(index + term.length()) == '_'));
    }

    private String insertNewLine(String str, int index, int indentCount) {
        if (str.charAt(index) == ';')
            index++;
        String text = str.substring(0, index) + "\n";
        for (int j = 0; j < indentCount; j++) {
            text += "    ";
        }
        if (str.charAt(index) == ' ')
            index++;
        text += str.substring(index);
        return text;
    }

    protected void refreshDetails(final Trigger trigger, final IDb db, final Settings settings) {
        final HorizontalLayout executingLayout = new HorizontalLayout();
        executingLayout.setSizeFull();
        final ProgressBar p = new ProgressBar();
        p.setIndeterminate(true);
        executingLayout.add(p);
        tabSheet.add(executingLayout, "Details", new Icon(VaadinIcon.SPINNER), 0);
        tabSheet.setSelectedTab(executingLayout);
        TriggerTableLayout triggerTable = new TriggerTableLayout(trigger, settings, new Refresher() {
            public void refresh() {
                tabSheet.remove(tabSheet.getTab(0));
                refreshDetails(trigger, db, settings);
            }
        });
        boolean select = executingLayout.equals(tabSheet.getSelectedTab().getComponent());
        tabSheet.remove("Details");
        VerticalLayout layout = new VerticalLayout();
        layout.setMargin(true);
        layout.setSizeFull();
        layout.add(triggerTable);
        tabSheet.add(layout, "Details", null, 0);
        if (select) {
            tabSheet.setSelectedTab(layout);
        }
    }

    public class Refresher {
        public void refresh() {
        };
    }

    @Override
    public void selected() {
    }

    @Override
    public void unselected() {
    }
}
