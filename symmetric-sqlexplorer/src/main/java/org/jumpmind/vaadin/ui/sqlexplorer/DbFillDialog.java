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
package org.jumpmind.vaadin.ui.sqlexplorer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DbFill;
import org.jumpmind.symmetric.io.data.DmlWeight;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.ResizableDialog;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.orderedlayout.Scroller.ScrollDirection;

public class DbFillDialog extends ResizableDialog {
    private static final long serialVersionUID = 1L;
    private Button cancelButton;
    private ShortcutRegistration cancelShortcutRegistration;
    private Button nextButton;
    private ShortcutRegistration nextShortcutRegistration;
    private Button previousButton;
    private Button fillButton;
    private ShortcutRegistration fillShortcutRegistration;
    private TableSelectionLayout tableSelectionLayout;
    private Scroller optionLayout;
    private Checkbox continueBox;
    private Checkbox cascadeBox;
    private Checkbox cascadeSelectBox;
    private Checkbox truncateBox;
    private Checkbox verboseBox;
    private TextField countField;
    private TextField intervalField;
    private TextField insertWeightField;
    private TextField updateWeightField;
    private TextField deleteWeightField;
    private TextField commitRowSizeField;
    private DbFill dbFill;
    private RadioButtonGroup<String> oGroup;
    private QueryPanel queryPanel;
    private IDatabasePlatform databasePlatform;

    public DbFillDialog(IDatabasePlatform databasePlatform, QueryPanel queryPanel, String excludeTablesRegex) {
        this(databasePlatform, new HashSet<Table>(0), queryPanel, excludeTablesRegex);
    }

    public DbFillDialog(IDatabasePlatform databasePlatform, Set<Table> selectedTableSet,
            QueryPanel queryPanel, String excludeTablesRegex) {
        super("Database Fill", false);
        setModality(ModalityMode.STRICT);
        setHeight("500px");
        setWidth("605px");
        this.databasePlatform = databasePlatform;
        this.queryPanel = queryPanel;
        tableSelectionLayout = new TableSelectionLayout(databasePlatform, selectedTableSet, excludeTablesRegex) {
            private static final long serialVersionUID = 1L;

            protected void selectionChanged() {
                nextButton.setEnabled(tableSelectionLayout.getSelectedTables().size() > 0);
            };
        };
        tableSelectionLayout.setHeight("318px");
        createOptionLayout();
        add(tableSelectionLayout, 1);
        addButtons();
        nextShortcutRegistration = nextButton.addClickShortcut(Key.ENTER);
    }

    protected void addButtons() {
        nextButton = CommonUiUtils.createPrimaryButton("Next", event -> next());
        nextButton.setEnabled(tableSelectionLayout.getSelectedTables().size() > 0);
        cancelButton = new Button("Close", event -> close());
        cancelShortcutRegistration = cancelButton.addClickShortcut(Key.ESCAPE);
        previousButton = new Button("Previous", event -> previous());
        previousButton.setVisible(false);
        fillButton = CommonUiUtils.createPrimaryButton("Fill...", event -> {
            createDbFill();
            if (dbFill.getPrint() == false) {
                confirm();
            } else {
                List<String> tables = getSelectedTables();
                for (String tableName : tables) {
                    Table table = (Table) databasePlatform.getRelationFromCache(
                            tableSelectionLayout.catalogSelect.getValue() != null ? tableSelectionLayout.catalogSelect
                                    .getValue().toString() : null,
                            tableSelectionLayout.schemaSelect.getValue() != null ? tableSelectionLayout.schemaSelect
                                    .getValue().toString() : null, tableName, false);
                    if (table != null) {
                        for (int i = 0; i < dbFill.getRecordCount(); i++) {
                            for (int j = 0; j < dbFill.getInsertWeight(); j++) {
                                String sql = dbFill.createDynamicRandomInsertSql(table);
                                queryPanel.appendSql(sql);
                            }
                            for (int j = 0; j < dbFill.getUpdateWeight(); j++) {
                                String sql = dbFill.createDynamicRandomUpdateSql(table);
                                queryPanel.appendSql(sql);
                            }
                            for (int j = 0; j < dbFill.getDeleteWeight(); j++) {
                                String sql = dbFill.createDynamicRandomDeleteSql(table);
                                queryPanel.appendSql(sql);
                            }
                        }
                    }
                }
                close();
            }
        });
        fillButton.setVisible(false);
        buildButtonFooter(cancelButton, previousButton, nextButton, fillButton);
    }

    protected void createOptionLayout() {
        VerticalLayout optionContent = new VerticalLayout();
        optionContent.setMargin(false);
        optionContent.setSpacing(true);
        optionContent.setSizeFull();
        optionContent.add(new Span("Please choose from the following options"));
        FormLayout formLayout = new FormLayout();
        formLayout.setSizeFull();
        optionContent.addAndExpand(formLayout);
        countField = new TextField("Count (# of rows to fill)");
        countField.setValue("1");
        countField.setValueChangeMode(ValueChangeMode.EAGER);
        countField.addValueChangeListener(event -> fillButton.setEnabled(enableFillButton()));
        formLayout.add(countField);
        intervalField = new TextField("Interval (ms)");
        intervalField.setValue("0");
        intervalField.setValueChangeMode(ValueChangeMode.EAGER);
        intervalField.addValueChangeListener(event -> fillButton.setEnabled(enableFillButton()));
        formLayout.add(intervalField);
        insertWeightField = new TextField("Insert Weight");
        insertWeightField.setValue("1");
        insertWeightField.setValueChangeMode(ValueChangeMode.EAGER);
        insertWeightField.addValueChangeListener(event -> fillButton.setEnabled(enableFillButton()));
        formLayout.add(insertWeightField);
        updateWeightField = new TextField("Update Weight");
        updateWeightField.setValue("0");
        updateWeightField.setValueChangeMode(ValueChangeMode.EAGER);
        updateWeightField.addValueChangeListener(event -> fillButton.setEnabled(enableFillButton()));
        formLayout.add(updateWeightField);
        deleteWeightField = new TextField("Delete Weight");
        deleteWeightField.setValue("0");
        deleteWeightField.setValueChangeMode(ValueChangeMode.EAGER);
        deleteWeightField.addValueChangeListener(event -> fillButton.setEnabled(enableFillButton()));
        formLayout.add(deleteWeightField);
        continueBox = new Checkbox("Continue (ignore ANY errors and continue to modify)");
        continueBox.setValue(true);
        formLayout.add(continueBox);
        cascadeBox = new Checkbox("Fill dependent tables also.");
        cascadeBox.setValue(false);
        formLayout.add(cascadeBox);
        cascadeSelectBox = new Checkbox("Fill dependent tables by selecting existing data.");
        cascadeSelectBox.setValue(false);
        formLayout.add(cascadeSelectBox);
        verboseBox = new Checkbox("Turn on verbose logging during fill.");
        verboseBox.setValue(false);
        formLayout.add(verboseBox);
        truncateBox = new Checkbox("Truncate table(s) before filling.");
        truncateBox.setValue(false);
        formLayout.add(truncateBox);
        oGroup = new RadioButtonGroup<String>();
        oGroup.setItems("Fill Table(s)", "Send to Sql Editor");
        oGroup.setValue("Fill Table(s)");
        formLayout.add(oGroup);
        commitRowSizeField = new TextField("Commit Row Size");
        commitRowSizeField.setValue("1");
        commitRowSizeField.setValueChangeMode(ValueChangeMode.EAGER);
        commitRowSizeField.addValueChangeListener(event -> fillButton.setEnabled(enableFillButton()));
        formLayout.add(commitRowSizeField);
        optionLayout = new Scroller(optionContent);
        optionLayout.setScrollDirection(ScrollDirection.VERTICAL);
        optionLayout.setSizeFull();
    }

    protected void confirm() {
        enableShortcuts(false);
        new ConfirmDialog("Confirm",
                "Are you sure?  Please note that this will affect data in the selected tables.  Make sure you have a backup of your data.",
                "Ok", e -> {
                    fill();
                    close();
                }, "Cancel", e -> enableShortcuts(true)).open();
    }

    protected void enableShortcuts(boolean enabled) {
        if (enabled && cancelShortcutRegistration == null && fillShortcutRegistration == null) {
            cancelShortcutRegistration = cancelButton.addClickShortcut(Key.ESCAPE);
            fillShortcutRegistration = fillButton.addClickShortcut(Key.ENTER);
        } else if (!enabled && cancelShortcutRegistration != null && fillShortcutRegistration != null) {
            cancelShortcutRegistration.remove();
            cancelShortcutRegistration = null;
            fillShortcutRegistration.remove();
            fillShortcutRegistration = null;
        }
    }

    protected void fill() {
        List<String> temp = getSelectedTables();
        String[] tables = new String[temp.size()];
        temp.toArray(tables);
        dbFill.fillTables(tables);
    }

    protected void createDbFill() {
        dbFill = new DbFill(databasePlatform);
        dbFill.setCatalog(tableSelectionLayout.catalogSelect.getValue() != null ? tableSelectionLayout.catalogSelect
                .getValue().toString() : null);
        dbFill.setSchema(tableSelectionLayout.schemaSelect.getValue() != null ? tableSelectionLayout.schemaSelect
                .getValue().toString() : null);
        dbFill.setContinueOnError(continueBox.getValue());
        dbFill.setCascading(cascadeBox.getValue());
        // TODO these can be made available when SymmetricDS dependency is pointed to 3.8
        dbFill.setCascadingSelect(cascadeSelectBox.getValue());
        dbFill.setVerbose(verboseBox.getValue());
        // dbFill.setTruncate(truncateBox.getValue());
        dbFill.setRecordCount(Integer.parseInt(countField.getValue().toString()));
        dbFill.setInterval(Integer.parseInt(intervalField.getValue().toString()));
        dbFill.setDmlWeight(new DmlWeight(Integer.parseInt(insertWeightField.getValue()), Integer.parseInt(updateWeightField.getValue()),
                Integer.parseInt(deleteWeightField.getValue())));
        dbFill.setMaxRowsCommit(Integer.parseInt(commitRowSizeField.getValue()));
        if (oGroup.getValue().toString().equals("Send to Sql Editor")) {
            dbFill.setPrint(true);
        }
    }

    protected void previous() {
        innerContent.remove(optionLayout);
        innerContent.addComponentAtIndex(0, tableSelectionLayout);
        innerContent.expand(tableSelectionLayout);
        fillButton.setVisible(false);
        if (fillShortcutRegistration != null) {
            fillShortcutRegistration.remove();
            fillShortcutRegistration = null;
        }
        previousButton.setVisible(false);
        nextButton.setVisible(true);
        if (nextShortcutRegistration == null) {
            nextShortcutRegistration = nextButton.addClickShortcut(Key.ENTER);
        }
    }

    protected void next() {
        innerContent.remove(tableSelectionLayout);
        innerContent.addComponentAtIndex(0, optionLayout);
        innerContent.expand(optionLayout);
        nextButton.setVisible(false);
        if (nextShortcutRegistration != null) {
            nextShortcutRegistration.remove();
            nextShortcutRegistration = null;
        }
        previousButton.setVisible(true);
        fillButton.setVisible(true);
        if (fillShortcutRegistration == null) {
            fillShortcutRegistration = fillButton.addClickShortcut(Key.ENTER);
        }
    }

    protected boolean enableFillButton() {
        if (!countField.getValue().equals("")) {
            if (!intervalField.getValue().equals("")) {
                if (!insertWeightField.getValue().equals("")) {
                    if (!updateWeightField.getValue().equals("")) {
                        if (!deleteWeightField.getValue().equals("")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public List<String> getSelectedTables() {
        return new ArrayList<String>(tableSelectionLayout.listOfTablesGrid.getSelectedItems());
    }
}
