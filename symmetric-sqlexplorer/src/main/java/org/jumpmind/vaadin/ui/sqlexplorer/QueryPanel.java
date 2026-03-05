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

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_AUTO_COMMIT;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_DELIMITER;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS;

import java.io.Serializable;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.CustomSplitLayout;
import org.jumpmind.vaadin.ui.common.Label;
import org.jumpmind.vaadin.ui.common.TabSheet;
import org.jumpmind.vaadin.ui.common.TabSheet.EnhancedTab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.shared.Registration;

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMode;
import de.f0rce.ace.events.AceSelectionChanged;

public class QueryPanel extends CustomSplitLayout implements IContentTab {
    private static final Logger log = LoggerFactory.getLogger(QueryPanel.class);
    private static final long serialVersionUID = 1L;
    AceEditor sqlArea;
    IDb db;
    ComponentEventListener<AceSelectionChanged> selectionChangeListener;
    Registration selectionChangeRegistration;
    List<ShortcutRegistration> shortcutRegistrations;
    boolean requestedExecutionAtCursor = false;
    boolean requestedScriptExecution = false;
    boolean commitButtonValue = false;
    boolean rollbackButtonValue = false;
    IButtonBar buttonBar;
    TabSheet resultsTabs;
    EnhancedTab errorTab;
    int maxNumberOfResultTabs = 10;
    ISettingsProvider settingsProvider;
    String user;
    Connection connection;
    Span status;
    transient SqlSuggester suggester;
    boolean canceled = false;
    VerticalLayout emptyResults;
    Map<Component, String> resultStatuses;
    EnhancedTab generalResultsTab;
    private AceEditor editor;
    transient Set<SqlRunner> runnersInProgress = new HashSet<SqlRunner>();

    public QueryPanel(IDb db, ISettingsProvider settingsProvider, IButtonBar buttonBar, String user) {
        this.settingsProvider = settingsProvider;
        this.db = db;
        this.user = user;
        this.buttonBar = buttonBar;
        this.sqlArea = buildSqlEditor();
        this.shortcutRegistrations = new ArrayList<ShortcutRegistration>();
        this.setOrientation(Orientation.VERTICAL);
        setHeightFull();
        VerticalLayout resultsLayout = new VerticalLayout();
        resultsLayout.setMargin(false);
        resultsLayout.setSpacing(false);
        resultsLayout.setSizeFull();
        resultsTabs = CommonUiUtils.createTabSheet();
        resultsTabs.setHeight("90px");
        resultsTabs.getStyle().set("margin-bottom", "20px");
        resultStatuses = new HashMap<Component, String>();
        HorizontalLayout statusBar = new HorizontalLayout();
        statusBar.setMargin(false);
        statusBar.setWidthFull();
        status = new Span("No Results");
        status.getElement().setAttribute("theme", "font-size-s");
        statusBar.add(status);
        setSelectedTabChangeListener();
        resultsLayout.add(resultsTabs, statusBar);
        resultsLayout.expand(resultsTabs);
        addToPrimary(sqlArea);
        addToSecondary(resultsLayout);
        setSplitterPosition(50);
        emptyResults = new VerticalLayout();
        emptyResults.setSizeFull();
        Span span = new Span("New results will appear here");
        span.setWidth(null);
        emptyResults.add(span);
        emptyResults.setHorizontalComponentAlignment(Alignment.CENTER, span);
        resultStatuses.put(emptyResults, "No Results");
        if (!settingsProvider.get().getProperties().is(SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS)) {
            createGeneralResultsTab();
        }
    }

    public IDb getDb() {
        return db;
    }

    protected AceEditor buildSqlEditor() {
        editor = CommonUiUtils.createAceEditor();
        editor.setMode(AceMode.sql);
        editor.addValueChangeListener(event -> {
            setButtonsEnabled();
        });
        editor.addSyncCompletedListener(event -> {
            if (!editor.getValue().equals("")) {
                if (requestedExecutionAtCursor) {
                    if (execute(false) && !settingsProvider.get().getProperties().is(SQL_EXPLORER_AUTO_COMMIT)) {
                        setButtonsEnabled();
                    }
                    requestedExecutionAtCursor = false;
                } else if (requestedScriptExecution) {
                    if (execute(true) && !settingsProvider.get().getProperties().is(SQL_EXPLORER_AUTO_COMMIT)) {
                        setButtonsEnabled();
                    }
                    requestedScriptExecution = false;
                }
            } else {
                requestedExecutionAtCursor = false;
                requestedScriptExecution = false;
            }
        });
        boolean autoSuggestEnabled = settingsProvider.get().getProperties().is(Settings.SQL_EXPLORER_AUTO_COMPLETE);
        setAutoCompleteEnabled(autoSuggestEnabled);
        selectionChangeListener = new DummyChangeListener();
        return editor;
    }

    public AceEditor getSqlEditor() {
        return editor;
    }

    public void syncSqlEditor() {
        editor.sync();
    }

    public IButtonBar getButtonBar() {
        return buttonBar;
    }

    protected void setSelectedTabChangeListener() {
        resultsTabs.addSelectedTabChangeListener(event -> {
            if (resultsTabs.getSelectedTab() != null) {
                Component tab = resultsTabs.getSelectedTab().getComponent();
                String st = resultStatuses.get(tab);
                if (st == null && tab instanceof VerticalLayout) {
                    if (((VerticalLayout) tab).getComponentCount() > 0) {
                        st = resultStatuses.get(((VerticalLayout) tab).getComponentAt(0));
                    }
                }
                if (st == null) {
                    st = "No Results";
                }
                status.setText(st);
            }
        });
    }

    public EnhancedTab getGeneralResultsTab() {
        return generalResultsTab;
    }

    public void createGeneralResultsTab() {
        if (generalResultsTab == null) {
            VerticalLayout generalResultsPanel = new VerticalLayout();
            generalResultsPanel.setSizeFull();
            generalResultsTab = resultsTabs.add(generalResultsPanel, "Results", 0);
            resetGeneralResultsTab();
        }
    }

    public void removeGeneralResultsTab() {
        if (generalResultsTab != null) {
            Component content = ((VerticalLayout) generalResultsTab.getComponent()).getComponentAt(0);
            if (content instanceof TabularResultLayout) {
                addResultsTab(((TabularResultLayout) content).refreshWithoutSaveButton(),
                        StringUtils.abbreviate(((TabularResultLayout) content).getSql(), 20), generalResultsTab.getIcon(), 0);
            }
            resultsTabs.remove(generalResultsTab);
            generalResultsTab = null;
        }
    }

    public void resetGeneralResultsTab() {
        if (generalResultsTab != null) {
            replaceGeneralResultsWith(emptyResults, null);
            generalResultsTab.setCloseable(false);
        }
    }

    public void replaceGeneralResultsWith(Component newComponent, VaadinIcon icon) {
        ((VerticalLayout) generalResultsTab.getComponent()).removeAll();
        ((VerticalLayout) generalResultsTab.getComponent()).add(newComponent);
        if (icon != null) {
            generalResultsTab.setIcon(new Icon(icon));
        }
    }

    @Override
    public void selected() {
        unselected();
        selectionChangeRegistration = sqlArea.addSelectionChangeListener(selectionChangeListener);
        shortcutRegistrations.add(createExecuteSqlShortcutListener());
        shortcutRegistrations.add(createExecuteSqlScriptShortcutListener());
        setButtonsEnabled();
        editor.focus();
    }

    @Override
    public void unselected() {
        if (selectionChangeRegistration != null) {
            selectionChangeRegistration.remove();
        }
        for (ShortcutRegistration registration : shortcutRegistrations) {
            registration.remove();
        }
        shortcutRegistrations.clear();
    }

    protected void setButtonsEnabled() {
        buttonBar.setCommitButtonEnabled(commitButtonValue);
        buttonBar.setRollbackButtonEnabled(rollbackButtonValue);
    }

    protected ShortcutRegistration createExecuteSqlShortcutListener() {
        return Shortcuts.addShortcutListener(editor, () -> requestExecutionAtCursor(), Key.ENTER, KeyModifier.CONTROL)
                .listenOn(editor);
    }

    protected ShortcutRegistration createExecuteSqlScriptShortcutListener() {
        return Shortcuts.addShortcutListener(editor, () -> requestScriptExecution(), Key.ENTER, KeyModifier.CONTROL,
                KeyModifier.SHIFT).listenOn(editor);
    }

    public void requestExecutionAtCursor() {
        requestedExecutionAtCursor = true;
        editor.sync();
    }

    public void requestScriptExecution() {
        requestedScriptExecution = true;
        editor.sync();
    }

    protected void addToSqlHistory(String sqlStatement, Date executeTime, long executeDuration, String userId) {
        sqlStatement = sqlStatement.trim();
        Settings settings = settingsProvider.load();
        SqlHistory history = settings.getSqlHistory(sqlStatement);
        if (history == null) {
            history = new SqlHistory();
            history.setSqlStatement(sqlStatement);
            settings.addSqlHistory(history);
        }
        history.setLastExecuteDuration(executeDuration);
        history.setExecuteCount(history.getExecuteCount() + 1);
        history.setLastExecuteUserId(userId);
        history.setLastExecuteTime(executeTime);
        settingsProvider.save(settings);
    }

    public boolean reExecute(String sql) {
        EnhancedTab tab = resultsTabs.getSelectedTab();
        int tabPosition = resultsTabs.getTabIndex(tab.getComponent());
        if (generalResultsTab != null && generalResultsTab == tab) {
            return execute(false, sql, tabPosition);
        } else {
            resultsTabs.remove(tab.getName());
            return execute(false, sql, tabPosition, true);
        }
    }

    public boolean execute(final boolean runAsScript) {
        return execute(runAsScript, null, resultsTabs.getComponentCount());
    }

    public void appendSql(String sql) {
        if (isNotBlank(sql)) {
            sqlArea.setValue((isNotBlank(sqlArea.getValue()) ? sqlArea.getValue() + "\n" : "") + sql);
        }
    }

    public String getSql() {
        return sqlArea.getValue();
    }

    protected void executeSql(String sql, boolean writeToQueryWindow) {
        if (writeToQueryWindow) {
            appendSql(sql);
        }
        execute(false, sql, resultsTabs.getComponentCount());
    }

    protected boolean execute(final boolean runAsScript, String sqlText, final int tabPosition) {
        return execute(runAsScript, sqlText, tabPosition, false);
    }

    protected boolean execute(final boolean runAsScript, String sqlText, final int tabPosition, final boolean forceNewTab) {
        boolean scheduled = false;
        if (runnersInProgress == null) {
            runnersInProgress = new HashSet<SqlRunner>();
        }
        if (sqlText == null) {
            if (!runAsScript) {
                sqlText = selectSqlToRun();
            } else {
                sqlText = sqlArea.getValue();
            }
            sqlText = sqlText != null ? sqlText.trim() : null;
        }
        if (StringUtils.isNotBlank(sqlText)) {
            final HorizontalLayout executingLayout = new HorizontalLayout();
            executingLayout.setMargin(true);
            executingLayout.setSizeFull();
            final Label label = new Label("Executing:\n\n" + StringUtils.abbreviate(sqlText, 250));
            label.setEnabled(false);
            executingLayout.add(label);
            executingLayout.setVerticalComponentAlignment(Alignment.START, label);
            final String sql = sqlText;
            final EnhancedTab executingTab;
            if (!forceNewTab && generalResultsTab != null) {
                replaceGeneralResultsWith(executingLayout, VaadinIcon.SPINNER);
                executingTab = null;
            } else {
                executingTab = resultsTabs.add(executingLayout, StringUtils.abbreviate(sql, 20),
                        new Icon(VaadinIcon.SPINNER), tabPosition);
            }
            if (executingTab != null) {
                executingTab.setCloseable(true);
                resultsTabs.setSelectedTab(executingTab);
            }
            final SqlRunner runner = new SqlRunner(sql, runAsScript, user, db, settingsProvider.get(), this, generalResultsTab != null);
            runnersInProgress.add(runner);
            runner.setConnection(connection);
            runner.setListener(new SqlRunner.ISqlRunnerListener() {
                private static final long serialVersionUID = 1L;

                @Override
                public void writeSql(String sql) {
                    QueryPanel.this.appendSql(sql);
                }

                @Override
                public void reExecute(String sql) {
                    QueryPanel.this.reExecute(sql);
                }

                public void finished(final VaadinIcon icon, final List<Component> results, final long executionTimeInMs,
                        final boolean transactionStarted, final boolean transactionEnded) {
                    UI ui = getUI().orElse(null);
                    if (ui != null) {
                        ui.access(() -> {
                            try {
                                if (transactionEnded) {
                                    transactionEnded();
                                } else if (transactionStarted) {
                                    rollbackButtonValue = true;
                                    commitButtonValue = true;
                                    setButtonsEnabled();
                                    sqlArea.setClassName("transaction-in-progress");
                                    connection = runner.getConnection();
                                }
                                addToSqlHistory(StringUtils.abbreviate(sql, 1024 * 8), runner.getStartTime(), executionTimeInMs, user);
                                for (Component resultComponent : results) {
                                    ((HasSize) resultComponent).setSizeFull();
                                    if (forceNewTab || generalResultsTab == null || results.size() > 1) {
                                        String tabLabel = sql;
                                        if (resultComponent instanceof TabularResultLayout tabularResultLayout) {
                                            tabLabel = tabularResultLayout.getSql();
                                            resultComponent = tabularResultLayout.refreshWithoutSaveButton();
                                        }
                                        addResultsTab(resultComponent, StringUtils.abbreviate(tabLabel, 20), new Icon(icon), tabPosition);
                                    } else {
                                        replaceGeneralResultsWith(resultComponent, icon);
                                        resultsTabs.setSelectedTab(generalResultsTab.getComponent());
                                    }
                                    String statusVal;
                                    if (canceled) {
                                        statusVal = "Sql canceled after " + executionTimeInMs + " ms for "
                                                + db.getName() + ".  Finished at "
                                                + SimpleDateFormat.getTimeInstance().format(new Date());
                                    } else {
                                        statusVal = "Sql executed in " + executionTimeInMs + " ms for " + db.getName()
                                                + ".  Finished at " + SimpleDateFormat.getTimeInstance().format(new Date());
                                    }
                                    status.setText(statusVal);
                                    resultStatuses.put(resultComponent, statusVal);
                                    canceled = false;
                                }
                            } finally {
                                setButtonsEnabled();
                                if (executingTab != null) {
                                    resultsTabs.remove(executingTab);
                                } else if (results.size() > 1) {
                                    resetGeneralResultsTab();
                                }
                                runnersInProgress.remove(runner);
                                runner.setListener(null);
                            }
                        });
                    }
                }
            });
            final Button cancel = new Button("Cancel");
            cancel.addClickListener(event -> {
                log.info("Canceling sql: " + sql);
                String labelText = label.getText();
                if (labelText != null) {
                    label.setText("Canceling" + labelText.substring(9));
                } else {
                    label.setText("Canceling:\n\n" + StringUtils.abbreviate(sql, 250));
                }
                executingLayout.remove(cancel);
                canceled = true;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        runner.cancel();
                    }
                }).start();
            });
            executingLayout.add(cancel);
            scheduled = true;
            runner.start();
        }
        setButtonsEnabled();
        return scheduled;
    }

    public void addResultsTab(Component resultComponent, String title, Icon icon) {
        addResultsTab(resultComponent, title, icon, resultsTabs.getComponentCount());
    }

    public void addResultsTab(Component resultComponent, String title, Icon icon, int position) {
        EnhancedTab tab = resultsTabs.add(resultComponent, title, icon, position);
        resultsTabs.setCloseable(true);
        resultsTabs.setSelectedTab(title);
        if (errorTab != null) {
            resultsTabs.remove(errorTab);
            errorTab = null;
        }
        if (maxNumberOfResultTabs > 0 && resultsTabs.getTabCount() > maxNumberOfResultTabs) {
            resultsTabs.remove(resultsTabs.getTab(resultsTabs.getTabCount() - 1));
        }
        if (icon.equals(new Icon(VaadinIcon.STOP))) {
            errorTab = tab;
        }
    }

    public void commit() {
        try {
            SqlRunner.commit(connection);
        } catch (Exception ex) {
            Notification.show(ex.getMessage());
        } finally {
            commitButtonValue = false;
            rollbackButtonValue = false;
            setButtonsEnabled();
            connection = null;
        }
    }

    public void transactionEnded() {
        commitButtonValue = false;
        rollbackButtonValue = false;
        setButtonsEnabled();
        connection = null;
    }

    public void rollback() {
        try {
            SqlRunner.rollback(connection);
        } catch (Exception ex) {
            Notification.show(ex.getMessage());
        } finally {
            commitButtonValue = false;
            rollbackButtonValue = false;
            setButtonsEnabled();
            connection = null;
        }
    }

    protected String selectSqlToRun() {
        String delimiter = settingsProvider.get().getProperties().get(SQL_EXPLORER_DELIMITER);
        String sql = sqlArea.getValue();
        String selectedText = sqlArea.getSelection().getSelectedText();
        boolean selected = selectedText != null && !selectedText.trim().isEmpty();
        if (selected) {
            sql = sqlArea.getSelection().getSelectedText();
        } else {
            StringBuilder sqlBuffer = new StringBuilder();
            String[] lines = sql.split("\n");
            int cursorPosition = sqlArea.getCursorPosition().getRow();
            boolean pastCursor = false;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (i == cursorPosition) {
                    pastCursor = true;
                }
                if (!pastCursor) {
                    if (line.trim().endsWith(delimiter) || line.trim().equals("")) {
                        sqlBuffer.setLength(0);
                    } else {
                        sqlBuffer.append(line).append("\n");
                    }
                } else if (line.trim().endsWith(delimiter)) {
                    sqlBuffer.append(line);
                    break;
                } else if (line.trim().equals("")) {
                    break;
                } else {
                    sqlBuffer.append(line).append("\n");
                }
            }
            sql = sqlBuffer.toString();
        }
        sql = sql.trim();
        if (sql.endsWith(delimiter)) {
            sql = sql.substring(0, sql.length() - 1);
        }
        return sql;
    }

    public void setAutoCompleteEnabled(boolean enabled) {
        if (suggester == null) {
            suggester = new SqlSuggester(db, editor);
        }
        suggester.setEnabled(enabled);
    }

    static class DummyChangeListener implements ComponentEventListener<AceSelectionChanged>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void onComponentEvent(AceSelectionChanged event) {
        }
    }
}
