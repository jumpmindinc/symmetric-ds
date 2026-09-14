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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.properties.TypedProperties;

public class Settings implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String SQL_EXPLORER_EXCLUDE_TABLES_REGEX = "sql.explorer.exclude.tables.regex";
    public static final String SQL_EXPLORER_SHOW_ROW_NUMBERS = "sql.explorer.show.row.numbers";
    public static final String SQL_EXPLORER_AUTO_COMMIT = "sql.explorer.auto.commit";
    public static final String SQL_EXPLORER_AUTO_COMPLETE = "sql.explorer.auto.complete";
    public static final String SQL_EXPLORER_RESULT_AS_TEXT = "sql.explorer.result.as.text";
    public static final String SQL_EXPLORER_IGNORE_ERRORS_WHEN_RUNNING_SCRIPTS = "sql.explorer.ignore.errors.when.running.scripts";
    public static final String SQL_EXPLORER_DELIMITER = "sql.explorer.delimiter";
    public static final String SQL_EXPLORER_MAX_RESULTS = "sql.explorer.max.results";
    public static final String SQL_EXPLORER_MAX_HISTORY = "sql.explorer.max.history";
    public static final String SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS = "sql.explorer.show.results.in.new.tabs";
    public static final String SQL_EXPLORER_SHOW_TRIGGERS = "sql.explorer.show.triggers";
    protected List<SqlHistory> sqlHistory = new ArrayList<SqlHistory>();
    protected TypedProperties properties = new TypedProperties();
    protected boolean allowQueries = true;
    protected boolean allowDml = true;
    protected boolean allowImport = true;
    protected boolean allowExport = true;
    protected boolean allowFill = true;
    protected boolean allowCompare = true;
    protected boolean allowRepair = true;

    public Settings() {
        properties.put(SQL_EXPLORER_DELIMITER, ";");
        properties.put(SQL_EXPLORER_SHOW_ROW_NUMBERS, "true");
        properties.put(SQL_EXPLORER_AUTO_COMMIT, "true");
        properties.put(SQL_EXPLORER_AUTO_COMPLETE, "true");
        properties.put(SQL_EXPLORER_RESULT_AS_TEXT, "false");
        properties.put(SQL_EXPLORER_EXCLUDE_TABLES_REGEX, "(SYM_)\\w+");
        properties.put(SQL_EXPLORER_MAX_RESULTS, "1000");
        properties.put(SQL_EXPLORER_MAX_HISTORY, "100");
        properties.put(SQL_EXPLORER_IGNORE_ERRORS_WHEN_RUNNING_SCRIPTS, "false");
        properties.put(SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS, "false");
        properties.put(SQL_EXPLORER_SHOW_TRIGGERS, "true");
    }

    public TypedProperties getProperties() {
        return properties;
    }

    public void setProperties(TypedProperties properties) {
        this.properties = properties;
    }

    public void addSqlHistory(SqlHistory history) {
        int maxSize = Integer.parseInt(properties.get(SQL_EXPLORER_MAX_HISTORY));
        if (sqlHistory.size() > maxSize) {
            SqlHistory oldest = null;
            for (SqlHistory s : sqlHistory) {
                if (oldest == null || oldest.getLastExecuteTime().before(s.getLastExecuteTime())) {
                    oldest = s;
                }
            }
            sqlHistory.remove(oldest);
        }
        sqlHistory.add(history);
    }

    public List<SqlHistory> getSqlHistory() {
        return sqlHistory;
    }

    public void setSqlHistory(List<SqlHistory> sqlHistory) {
        this.sqlHistory = sqlHistory;
    }

    public SqlHistory getSqlHistory(String sql) {
        sql = sql.trim();
        for (SqlHistory sqlHistory2 : sqlHistory) {
            if (sqlHistory2.getSqlStatement().equals(sql)) {
                return sqlHistory2;
            }
        }
        return null;
    }

    public boolean isAllowQueries() {
        return allowQueries;
    }

    public void setAllowQueries(boolean allowQueries) {
        this.allowQueries = allowQueries;
    }

    public boolean isAllowDml() {
        return allowDml;
    }

    public void setAllowDml(boolean allowAnySql) {
        this.allowDml = allowAnySql;
    }

    public boolean isAllowImport() {
        return allowImport;
    }

    public void setAllowImport(boolean allowImport) {
        this.allowImport = allowImport;
    }

    public boolean isAllowExport() {
        return allowExport;
    }

    public void setAllowExport(boolean allowExport) {
        this.allowExport = allowExport;
    }

    public boolean isAllowFill() {
        return allowFill;
    }

    public void setAllowFill(boolean allowFill) {
        this.allowFill = allowFill;
    }

    public boolean isAllowCompare() {
        return allowCompare;
    }

    public void setAllowCompare(boolean allowCompare) {
        this.allowCompare = allowCompare;
    }

    public boolean isAllowRepair() {
        return allowRepair;
    }

    public void setAllowRepair(boolean allowRepair) {
        this.allowRepair = allowRepair;
    }
}
