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

import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.*;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_DELIMITER;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_EXCLUDE_TABLES_REGEX;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_MAX_RESULTS;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_RESULT_AS_TEXT;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_SHOW_ROW_NUMBERS;

import java.text.DecimalFormat;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.ResizableDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.converter.StringToIntegerConverter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

public class SettingsDialog extends ResizableDialog {
    private static final long serialVersionUID = 1L;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private TextField rowsToFetchField;
    private Binder<Integer> binder;
    private Checkbox autoCommitBox;
    private Checkbox autoCompleteBox;
    private TextField delimiterField;
    private TextField excludeTablesWithPrefixField;
    private Checkbox resultAsTextBox;
    private Checkbox ignoreErrorsWhenRunningScript;
    private Checkbox showRowNumbersBox;
    private Checkbox showResultsInNewTabsBox;
    private Checkbox showTriggersBox;
    ISettingsProvider settingsProvider;
    SqlExplorer explorer;

    public SettingsDialog(SqlExplorer explorer) {
        super("Settings");
        this.explorer = explorer;
        this.settingsProvider = explorer.getSettingsProvider();
        setCloseOnOutsideClick(false);
        setWidth("800px");
        add(createSettingsLayout(), 1);
        createButtonLayout();
    }

    protected HorizontalLayout createSettingsLayout() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidth("700px");
        layout.getStyle().set("margin", "0 16px");
        FormLayout settingsLayout = new FormLayout();
        Settings settings = settingsProvider.get();
        TypedProperties properties = settings.getProperties();
        rowsToFetchField = new TextField("Max Results");
        rowsToFetchField.setWidth("6em");
        binder = new Binder<Integer>();
        binder.forField(rowsToFetchField).withConverter(new StringToIntegerConverter("Could not convert value to Integer"))
                .withValidator(value -> value != null, "Invalid value")
                .bind(integer -> integer, (integer, value) -> integer = value);
        rowsToFetchField.setValue(properties.getProperty(SQL_EXPLORER_MAX_RESULTS, "100"));
        settingsLayout.add(rowsToFetchField);
        delimiterField = new TextField("Delimiter");
        delimiterField.setValue(properties.getProperty(SQL_EXPLORER_DELIMITER, ";"));
        settingsLayout.add(delimiterField);
        excludeTablesWithPrefixField = new TextField("Hide Tables (regex)");
        excludeTablesWithPrefixField.setValue(properties.getProperty(SQL_EXPLORER_EXCLUDE_TABLES_REGEX));
        settingsLayout.add(excludeTablesWithPrefixField);
        resultAsTextBox = new Checkbox("Result As Text");
        String resultAsTextValue = (properties.getProperty(SQL_EXPLORER_RESULT_AS_TEXT, "false"));
        if (resultAsTextValue.equals("true")) {
            resultAsTextBox.setValue(true);
        } else {
            resultAsTextBox.setValue(false);
        }
        settingsLayout.add(resultAsTextBox);
        ignoreErrorsWhenRunningScript = new Checkbox("Ignore Errors When Running Scripts");
        String ignoreErrorsWhenRunningScriptTextValue = (properties.getProperty(SQL_EXPLORER_IGNORE_ERRORS_WHEN_RUNNING_SCRIPTS, "false"));
        if (ignoreErrorsWhenRunningScriptTextValue.equals("true")) {
            ignoreErrorsWhenRunningScript.setValue(true);
        } else {
            ignoreErrorsWhenRunningScript.setValue(false);
        }
        settingsLayout.add(ignoreErrorsWhenRunningScript);
        autoCommitBox = new Checkbox("Auto Commit");
        String autoCommitValue = (properties.getProperty(SQL_EXPLORER_AUTO_COMMIT, "true"));
        if (autoCommitValue.equals("true")) {
            autoCommitBox.setValue(true);
        } else {
            autoCommitBox.setValue(false);
        }
        settingsLayout.add(autoCommitBox);
        autoCompleteBox = new Checkbox("Auto Complete");
        String autoCompleteValue = (properties.getProperty(SQL_EXPLORER_AUTO_COMPLETE, "true"));
        if (autoCompleteValue.equals("true")) {
            autoCompleteBox.setValue(true);
        } else {
            autoCompleteBox.setValue(false);
        }
        settingsLayout.add(autoCompleteBox);
        showRowNumbersBox = new Checkbox("Show Row Numbers");
        String showRowNumbersValue = (properties.getProperty(SQL_EXPLORER_SHOW_ROW_NUMBERS, "true"));
        if (showRowNumbersValue.equals("true")) {
            showRowNumbersBox.setValue(true);
        } else {
            showRowNumbersBox.setValue(false);
        }
        settingsLayout.add(showRowNumbersBox);
        showResultsInNewTabsBox = new Checkbox("Always Put Results In New Tabs");
        String showResultsInNewTabsValue = (properties.getProperty(SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS, "false"));
        if (showResultsInNewTabsValue.equals("true")) {
            showResultsInNewTabsBox.setValue(true);
        } else {
            showResultsInNewTabsBox.setValue(false);
        }
        settingsLayout.add(showResultsInNewTabsBox);
        showTriggersBox = new Checkbox("Show Triggers");
        String showTriggersValue = properties.getProperty(SQL_EXPLORER_SHOW_TRIGGERS, "false");
        showTriggersBox.setValue(showTriggersValue.equals("true"));
        settingsLayout.add(showTriggersBox);
        layout.add(settingsLayout);
        return layout;
    }

    protected void createButtonLayout() {
        Button saveButton = CommonUiUtils.createPrimaryButton("Save", event -> {
            if (save()) {
                close();
            }
        });
        buildButtonFooter(new Button("Cancel", new CloseButtonListener()), saveButton);
    }

    protected boolean save() {
        if (binder.validate().isOk()) {
            Settings settings = settingsProvider.get();
            TypedProperties properties = settings.getProperties();
            try {
                properties.setProperty(SQL_EXPLORER_MAX_RESULTS, new DecimalFormat().parse(rowsToFetchField.getValue()).intValue());
                properties.setProperty(SQL_EXPLORER_AUTO_COMMIT, String.valueOf(autoCommitBox.getValue()));
                properties.setProperty(SQL_EXPLORER_AUTO_COMPLETE, String.valueOf(autoCompleteBox.getValue()));
                properties.setProperty(SQL_EXPLORER_DELIMITER, delimiterField.getValue());
                properties.setProperty(SQL_EXPLORER_RESULT_AS_TEXT, String.valueOf(resultAsTextBox.getValue()));
                properties.setProperty(SQL_EXPLORER_SHOW_ROW_NUMBERS, String.valueOf(showRowNumbersBox.getValue()));
                properties.setProperty(SQL_EXPLORER_EXCLUDE_TABLES_REGEX, excludeTablesWithPrefixField.getValue());
                properties.setProperty(SQL_EXPLORER_IGNORE_ERRORS_WHEN_RUNNING_SCRIPTS, String.valueOf(ignoreErrorsWhenRunningScript.getValue()));
                properties.setProperty(SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS, String.valueOf(showResultsInNewTabsBox.getValue()));
                properties.setProperty(SQL_EXPLORER_SHOW_TRIGGERS, String.valueOf(showTriggersBox.getValue()));
                settingsProvider.save(settings);
                explorer.refreshQueryPanels();
                return true;
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
                CommonUiUtils.notifyError(opened -> enableEscapeShortcut(!opened));
                return false;
            }
        }
        CommonUiUtils.notify("Save Failed", "Ensure that all fields are valid", opened -> enableEscapeShortcut(!opened));
        return false;
    }
}
