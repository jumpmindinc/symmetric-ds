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

import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_AUTO_COMMIT;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_DELIMITER;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_IGNORE_ERRORS_WHEN_RUNNING_SCRIPTS;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_MAX_RESULTS;
import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_RESULT_AS_TEXT;

import java.io.Serializable;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.mariadb.MariaDBDatabasePlatform;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlScriptReader;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class SqlRunner extends Thread {
    private static final Logger log = LoggerFactory.getLogger(SqlRunner.class);
    private SqlExplorer explorer;
    private static List<SqlRunner> sqlRunners = new ArrayList<SqlRunner>();
    private ISqlRunnerListener listener;
    private QueryPanel queryPanel;
    private boolean isInQueryGeneralResults;
    private boolean runAsScript;
    private String sqlText;
    private Connection connection;
    private Date startTime = new Date();
    private Date endTime = null;
    private boolean rowsUpdated = false;
    private boolean createdConnection = true;
    private boolean showSqlOnResults = true;
    private IDb db;
    private String user;
    private boolean autoCommit;
    private boolean logAtDebug;
    private static final String COMMIT_COMMAND = "commit";
    private Settings settings;
    private boolean isCanceled = false;
    private PreparedStatement stmt;

    public static List<SqlRunner> getSqlRunners() {
        return sqlRunners;
    }

    public SqlRunner(String sqlText, boolean runAsScript, String user, IDb db, Settings settings) {
        this(sqlText, runAsScript, user, db, settings, null, null);
    }

    public SqlRunner(String sqlText, boolean runAsScript, String user, IDb db, Settings settings, ISqlRunnerListener listener) {
        this(sqlText, runAsScript, user, db, settings, null, listener);
    }

    public SqlRunner(String sqlText, boolean runAsScript, String user, IDb db, Settings settings, SqlExplorer explorer) {
        this(sqlText, runAsScript, user, db, settings, explorer, null);
    }

    public SqlRunner(String sqlText, boolean runAsScript, String user, IDb db, Settings settings, SqlExplorer explorer, ISqlRunnerListener listener) {
        this(sqlText, runAsScript, user, db, settings, explorer, listener, null, false);
    }

    public SqlRunner(String sqlText, boolean runAsScript, String user, IDb db, Settings settings, QueryPanel queryPanel, boolean isInQueryGeneralResults) {
        this(sqlText, runAsScript, user, db, settings, null, null, queryPanel, isInQueryGeneralResults);
    }

    public SqlRunner(String sqlText, boolean runAsScript, String user, IDb db, Settings settings, SqlExplorer explorer, ISqlRunnerListener listener,
            QueryPanel queryPanel, boolean isInQueryGeneralResults) {
        this.setName("sql-runner-" + threadId());
        this.sqlText = sqlText;
        this.runAsScript = runAsScript;
        this.db = db;
        this.listener = listener;
        this.settings = settings;
        this.autoCommit = settings.getProperties().is(SQL_EXPLORER_AUTO_COMMIT);
        this.user = user;
        this.explorer = explorer;
        this.queryPanel = queryPanel;
        this.isInQueryGeneralResults = isInQueryGeneralResults;
        sqlRunners.add(0, this);
    }

    public void setLogAtDebug(boolean logAtDebug) {
        this.logAtDebug = logAtDebug;
    }

    public void setShowSqlOnResults(boolean showSqlOnResults) {
        this.showSqlOnResults = showSqlOnResults;
    }

    public static void commit(Connection connection) throws SQLException {
        if (connection != null) {
            try {
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException e1) {
                }
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                }
                JdbcSqlTemplate.close(connection);
            }
        }
    }

    public static void rollback(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException e) {
                // do nothing
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                }
                JdbcSqlTemplate.close(connection);
            }
        }
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    @Override
    public void run() {
        TypedProperties properties = settings.getProperties();
        boolean resultsAsText = properties.is(SQL_EXPLORER_RESULT_AS_TEXT);
        int maxResultsSize = properties.getInt(SQL_EXPLORER_MAX_RESULTS);
        String delimiter = properties.get(SQL_EXPLORER_DELIMITER);
        boolean ignoreWhenRunAsScript = properties.is(SQL_EXPLORER_IGNORE_ERRORS_WHEN_RUNNING_SCRIPTS);
        List<Component> resultComponents = new ArrayList<Component>();
        VaadinIcon icon = VaadinIcon.CHECK_CIRCLE;
        rowsUpdated = false;
        boolean committed = false;
        boolean autoCommitBefore = true;
        try {
            DataSource dataSource = db.getPlatform().getDataSource();
            if (!(db.getPlatform().getSqlTemplate() instanceof JdbcSqlTemplate)) {
                resultComponents.add(wrapTextInComponent("<span style='color: red'> SQL queries are not available for this platform. </span>", "marked"));
                return;
            }
            JdbcSqlTemplate sqlTemplate = (JdbcSqlTemplate) db.getPlatform().getSqlTemplate();
            stmt = null;
            StringBuilder results = new StringBuilder();
            try {
                if (connection == null) {
                    connection = dataSource.getConnection();
                    connection.setAutoCommit(autoCommit);
                }
                autoCommitBefore = connection.getAutoCommit();
                if (connection.getTransactionIsolation() != sqlTemplate.getIsolationLevel()) {
                    connection.setTransactionIsolation(sqlTemplate.getIsolationLevel());
                }
                if (sqlTemplate.isRequiresAutoCommitFalseToSetFetchSize()) {
                    connection.setAutoCommit(false);
                }
                try (SqlScriptReader sqlReader = new SqlScriptReader(new StringReader(sqlText))) {
                    sqlReader.setDelimiter(delimiter);
                    sqlReader.setStripOutComments(true);
                    String sql = sqlReader.readSqlStatement();
                    while (sql != null) {
                        JdbcSqlTemplate.close(stmt);
                        if (db.getPlatform().getName().equals("voltdb")) {
                            stmt = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                        } else {
                            stmt = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                        }
                        String lowercaseSql = sql.trim().toLowerCase();
                        if (!lowercaseSql.startsWith("delete") && !lowercaseSql.startsWith("update") && !lowercaseSql.startsWith("insert")) {
                            if (db.getPlatform().getName().equals(DatabaseNamesConstants.MYSQL) &&
                                    !(db.getPlatform() instanceof MariaDBDatabasePlatform)) {
                                stmt.setFetchSize(Integer.MIN_VALUE);
                            } else {
                                stmt.setFetchSize(maxResultsSize < 100 ? maxResultsSize : 100);
                            }
                        }
                        if (settings != null && !settings.isAllowQueries()) {
                            log.info("[{}] Blocked execution by {} in hidden mode: {}", db.getName(), user, sql.trim());
                            throw new AbortedQueryException("No query access in hidden mode");
                        }
                        if (settings != null && !settings.isAllowDml() && !lowercaseSql.startsWith("select")) {
                            log.info("[{}] Blocked execution by {} in read-only mode: {}", db.getName(), user, sql.trim());
                            throw new AbortedQueryException("Only select statements are allowed in read-only mode");
                        }
                        if (logAtDebug) {
                            log.debug("[" + db.getName() + "] Executing by {}: {}", user, sql.trim());
                        } else {
                            log.info("[" + db.getName() + "] Executing by {}: {}", user, sql.trim());
                        }
                        if (sql.replaceAll("\\s", "").equalsIgnoreCase(COMMIT_COMMAND)) {
                            committed = true;
                        } else {
                            committed = false;
                        }
                        boolean hasResults = false;
                        try {
                            hasResults = stmt.execute();
                        } catch (SQLException e) {
                            if (runAsScript && ignoreWhenRunAsScript) {
                                results.append(sql);
                                results.append("\n");
                                results.append(buildErrorMessage(e));
                                results.append("\n");
                                results.append("\n");
                            } else {
                                throw e;
                            }
                        }
                        int updateCount = stmt.getUpdateCount();
                        boolean firstTimeThrough = true;
                        while (hasResults || updateCount != -1 || firstTimeThrough) {
                            ResultSet rs = null;
                            try {
                                if (hasResults) {
                                    rs = stmt.getResultSet();
                                    if (!runAsScript) {
                                        if (!resultsAsText) {
                                            resultComponents.add(new TabularResultLayout(explorer, db, sql, rs, listener, user, settings, queryPanel,
                                                    showSqlOnResults, isInQueryGeneralResults));
                                        } else {
                                            resultComponents.add(putResultsInArea(rs, maxResultsSize));
                                        }
                                    } else {
                                        int rowsRetrieved = 0;
                                        while (rs.next()) {
                                            rowsRetrieved++;
                                        }
                                        results.append(sql);
                                        results.append("\n");
                                        results.append("Rows Retrieved: ");
                                        results.append(rowsRetrieved);
                                        results.append("\n");
                                        results.append("\n");
                                    }
                                } else {
                                    rowsUpdated = updateCount > 0 ? true : false;
                                    if (!runAsScript) {
                                        String message = null;
                                        if (updateCount > 0) {
                                            message = String.format("%d rows affected", updateCount);
                                        } else {
                                            message = "Statement(s) executed";
                                        }
                                        resultComponents.add(wrapTextInComponent(message));
                                    } else {
                                        results.append(sql);
                                        results.append("\n");
                                        if (updateCount > 0) {
                                            results.append("Rows Affected: ");
                                            results.append(updateCount);
                                        } else {
                                            results.append("Statement executed");
                                        }
                                        results.append("\n");
                                        results.append("\n");
                                    }
                                }
                                hasResults = stmt.getMoreResults();
                                updateCount = stmt.getUpdateCount();
                                if (db.getPlatform().getName().equals(DatabaseNamesConstants.FIREBIRD) && !hasResults && updateCount == 0) {
                                    updateCount = -1;
                                }
                            } finally {
                                JdbcSqlTemplate.close(rs);
                                firstTimeThrough = false;
                            }
                        }
                        sql = sqlReader.readSqlStatement();
                    }
                }
            } catch (AbortedQueryException ex) {
                resultComponents.add(wrapTextInComponent(buildErrorMessage(ex.getMessage()), "marked"));
            } catch (Throwable ex) {
                if (isCanceled) {
                    String canceledMessage = "Canceled successfully.\n\n" + sqlText;
                    resultComponents.add(wrapTextInComponent(canceledMessage));
                } else {
                    icon = VaadinIcon.BAN;
                    resultComponents.add(wrapTextInComponent(buildErrorMessage(ex), "marked"));
                }
            } finally {
                if (autoCommitBefore) {
                    try {
                        connection.commit();
                        connection.setAutoCommit(autoCommitBefore);
                    } catch (SQLException e) {
                    }
                }
                JdbcSqlTemplate.close(stmt);
                if (autoCommit || (!autoCommit && !rowsUpdated && createdConnection)) {
                    JdbcSqlTemplate.close(connection);
                    connection = null;
                }
            }
            if (resultComponents.size() == 0 && StringUtils.isNotBlank(results.toString())) {
                resultComponents.add(wrapTextInComponent(results.toString(), icon == VaadinIcon.BAN ? "marked" : null));
            }
        } finally {
            endTime = new Date();
            if (listener != null) {
                listener.finished(icon, resultComponents, endTime.getTime() - startTime.getTime(), !autoCommit && rowsUpdated, committed);
            } else if (!autoCommit) {
                rollback(connection);
            }
        }
    }

    protected String buildErrorMessage(Throwable ex) {
        StringBuilder errorMessage = new StringBuilder("<span style='color: red'>");
        if (ex instanceof SQLException) {
            SQLException sqlException = (SQLException) ex;
            errorMessage.append("SQL Message: ").append(ex.getMessage());
            errorMessage.append("\nSQL State: ");
            errorMessage.append(sqlException.getSQLState());
            errorMessage.append("\nError Code: ");
            errorMessage.append(sqlException.getErrorCode());
        } else {
            errorMessage.append(ex.getMessage());
            errorMessage.append(ExceptionUtils.getStackTrace(ex));
        }
        errorMessage.append("</span>");
        return errorMessage.toString();
    }

    protected String buildErrorMessage(String message) {
        StringBuilder errorMessage = new StringBuilder("<span style='color: red'>");
        errorMessage.append(message);
        errorMessage.append("</span>");
        return errorMessage.toString();
    }

    protected Class<?> getClass(ResultSetMetaData meta, int i) throws SQLException {
        try {
            return Class.forName(meta.getColumnClassName(i));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected Scroller wrapTextInComponent(String text) {
        return wrapTextInComponent(text, null);
    }

    protected Scroller wrapTextInComponent(String text, String style) {
        Scroller panel = new Scroller();
        VerticalLayout content = new VerticalLayout();
        content.setMargin(false);
        panel.setContent(content);
        Label label = new Label("<pre>" + text + "</pre>");
        label.getStyle().set("margin", null);
        if (StringUtils.isNotBlank(style)) {
            label.setClassName(style);
        }
        content.add(label);
        return panel;
    }

    protected Scroller putResultsInArea(ResultSet rs, int maxResultSize) throws SQLException {
        Scroller panel = new Scroller();
        Pre pre = new Pre(resultsAsText(rs, maxResultSize));
        panel.setContent(pre);
        pre.getStyle().set("margin", "0");
        pre.getStyle().set("white-space", "pre");
        pre.setWidth("max-content");
        return panel;
    }

    protected String resultsAsText(ResultSet rs, int maxResultSize) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        int[] maxColumnSizes = new int[columns];
        for (int i = 1; i <= columns; i++) {
            String columnName = meta.getColumnName(i);
            maxColumnSizes[i - 1] = columnName.length();
        }
        int rowNumber = 1;
        List<Object[]> rows = new ArrayList<Object[]>();
        while (rs.next() && rowNumber <= maxResultSize) {
            Object[] row = new Object[columns];
            for (int i = 1; i <= columns; i++) {
                Object obj = CommonUiUtils.getObject(rs, i);
                row[i - 1] = obj;
                if (obj != null) {
                    int size = obj.toString().length();
                    if (maxColumnSizes[i - 1] < size) {
                        maxColumnSizes[i - 1] = size;
                    }
                }
            }
            rows.add(row);
            rowNumber++;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= columns; i++) {
            String columnName = meta.getColumnName(i);
            text.append(StringUtils.rightPad(columnName, maxColumnSizes[i - 1]));
            text.append(" ");
        }
        text.append("\n");
        for (int i = 1; i <= columns; i++) {
            text.append(StringUtils.rightPad("", maxColumnSizes[i - 1], "-"));
            text.append(" ");
        }
        text.append("\n");
        for (Object[] objects : rows) {
            for (int i = 0; i < objects.length; i++) {
                text.append(StringUtils.rightPad(objects[i] != null ? objects[i].toString() : "<null>", maxColumnSizes[i]));
                text.append(" ");
            }
            text.append("\n");
        }
        return text.toString();
    }

    public void setListener(ISqlRunnerListener listener) {
        this.listener = listener;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        if (connection != null) {
            this.createdConnection = false;
        }
        this.connection = connection;
    }

    public String getUser() {
        return user;
    }

    public String getSqlText() {
        return sqlText;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public boolean isRowsUpdated() {
        return rowsUpdated;
    }

    public boolean isRunAsScript() {
        return runAsScript;
    }

    public void cancel() {
        try {
            stmt.cancel();
            isCanceled = true;
        } catch (SQLException e) {
            log.error("Failed to cancel", e);
        }
    }

    interface ISqlRunnerListener extends Serializable {
        public void writeSql(String sql);

        public void reExecute(String sql);

        public void finished(VaadinIcon icon, List<Component> results, long executionTimeInMs, boolean transactionStarted,
                boolean transactionEnded);
    }
}
