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
package org.jumpmind.symmetric.load;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterErrorHandler;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;
import org.jumpmind.symmetric.model.LoadFilter;
import org.jumpmind.symmetric.model.LoadFilter.LoadFilterType;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DynamicDatabaseWriterFilter implements IDatabaseWriterFilter,
        IDatabaseWriterErrorHandler, IBuiltInExtensionPoint {
    private final String BATCH_COMPLETE_SCRIPTS_KEY = String.format("%d.BatchCompleteScripts", hashCode());
    private final String BATCH_COMMIT_SCRIPTS_KEY = String.format("%d.BatchCommitScripts", hashCode());
    private final String BATCH_ROLLBACK_SCRIPTS_KEY = String.format("%d.BatchRollbackScripts", hashCode());
    private final String FAIL_ON_ERROR_KEY = String.format("%d.FailOnError", hashCode());
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ISymmetricEngine engine = null;
    protected Map<String, List<LoadFilter>> loadFilters = null;

    public enum WriteMethod {
        BEFORE_WRITE, AFTER_WRITE, BATCH_COMPLETE, BATCH_COMMIT, BATCH_ROLLBACK, HANDLE_ERROR
    };

    public DynamicDatabaseWriterFilter(ISymmetricEngine engine,
            Map<String, List<LoadFilter>> loadFilters) {
        this.engine = engine;
        this.loadFilters = loadFilters;
    }

    public static List<DynamicDatabaseWriterFilter> getDatabaseWriterFilters(ISymmetricEngine engine,
            Map<LoadFilterType, Map<String, List<LoadFilter>>> loadFilters) {
        List<DynamicDatabaseWriterFilter> databaseWriterFilters = new ArrayList<DynamicDatabaseWriterFilter>();
        if (loadFilters != null) {
            for (Map.Entry<LoadFilterType, Map<String, List<LoadFilter>>> entry : loadFilters.entrySet()) {
                if (entry.getKey().equals(LoadFilterType.BSH)) {
                    databaseWriterFilters.add(new BshDatabaseWriterFilter(engine, entry.getValue()));
                } else if (entry.getKey().equals(LoadFilterType.JAVA)) {
                    databaseWriterFilters.add(new JavaDatabaseWriterFilter(engine, entry.getValue()));
                } else if (entry.getKey().equals(LoadFilterType.SQL)) {
                    databaseWriterFilters.add(new SQLDatabaseWriterFilter(engine, entry.getValue()));
                }
            }
        }
        return databaseWriterFilters;
    }

    public boolean beforeWrite(DataContext context, Table table, CsvData data) {
        return processLoadFilters(context, table, data, null, WriteMethod.BEFORE_WRITE);
    }

    public void afterWrite(DataContext context, Table table, CsvData data) {
        processLoadFilters(context, table, data, null, WriteMethod.AFTER_WRITE);
    }

    public boolean handleError(DataContext context, Table table, CsvData data, Exception error) {
        return processLoadFilters(context, table, data, error, WriteMethod.HANDLE_ERROR);
    }

    public void earlyCommit(DataContext context) {
    }

    public void batchComplete(DataContext context) {
        executeScripts(context, BATCH_COMPLETE_SCRIPTS_KEY);
    }

    public void batchCommitted(DataContext context) {
        executeScripts(context, BATCH_COMMIT_SCRIPTS_KEY);
    }

    public void batchRolledback(DataContext context) {
        executeScripts(context, BATCH_ROLLBACK_SCRIPTS_KEY);
    }

    protected boolean processLoadFilters(DataContext context, Table table, CsvData data,
            Exception error, WriteMethod writeMethod) {
        boolean writeRow = true;
        if (table != null) {
            List<LoadFilter> foundFilters = null;
            if (!table.getName().toLowerCase().startsWith(engine.getTablePrefix() + "_")) {
                foundFilters = lookupFilters(foundFilters,
                        table.getCatalog(), table.getSchema(), FormatUtils.WILDCARD);
                foundFilters = lookupFilters(foundFilters,
                        table.getCatalog(), FormatUtils.WILDCARD, FormatUtils.WILDCARD);
                foundFilters = lookupFilters(foundFilters,
                        FormatUtils.WILDCARD, table.getSchema(), FormatUtils.WILDCARD);
                foundFilters = lookupFilters(foundFilters,
                        FormatUtils.WILDCARD, FormatUtils.WILDCARD, FormatUtils.WILDCARD);
            }
            foundFilters = lookupFilters(foundFilters,
                    FormatUtils.WILDCARD, FormatUtils.WILDCARD, table.getName());
            foundFilters = lookupFilters(foundFilters,
                    FormatUtils.WILDCARD, table.getSchema(), table.getName());
            foundFilters = lookupFilters(foundFilters,
                    table.getCatalog(), FormatUtils.WILDCARD, table.getName());
            foundFilters = lookupFilters(foundFilters,
                    table.getCatalog(), table.getSchema(), table.getName());
            if (foundFilters != null) {
                for (LoadFilter filter : foundFilters) {
                    addBatchScriptsToContext(context, filter);
                }
                writeRow = processLoadFilters(context, table, data, error, writeMethod,
                        foundFilters);
            }
        }
        return writeRow;
    }

    private List<LoadFilter> lookupFilters(List<LoadFilter> foundFilters, String catalogName, String schemaName, String tableName) {
        String fullyQualifiedTableName = Relation.getFullyQualifiedName(catalogName, schemaName, tableName);
        if (isIgnoreCase()) {
            fullyQualifiedTableName = fullyQualifiedTableName.toUpperCase();
        }
        List<LoadFilter> filters = loadFilters.get(
                fullyQualifiedTableName);
        if (filters != null) {
            if (foundFilters == null) {
                foundFilters = new ArrayList<LoadFilter>();
            }
            foundFilters.addAll(filters);
        }
        return foundFilters;
    }

    protected abstract boolean processLoadFilters(DataContext context, Table table, CsvData data,
            Exception error, WriteMethod writeMethod, List<LoadFilter> loadFiltersForTable);

    protected void addBatchScriptsToContext(DataContext context, LoadFilter filter) {
        addBatchScriptToContext(context, BATCH_COMPLETE_SCRIPTS_KEY, filter.getBatchCompleteScript());
        addBatchScriptToContext(context, BATCH_COMMIT_SCRIPTS_KEY, filter.getBatchCommitScript());
        addBatchScriptToContext(context, BATCH_ROLLBACK_SCRIPTS_KEY, filter.getBatchRollbackScript());
        if (filter.isFailOnError()) {
            context.put(FAIL_ON_ERROR_KEY, Boolean.TRUE);
        }
    }

    protected void addBatchScriptToContext(DataContext context, String key, String script) {
        if (StringUtils.isNotBlank(script)) {
            @SuppressWarnings("unchecked")
            Set<String> scripts = (Set<String>) context.get(key);
            if (scripts == null) {
                scripts = new HashSet<String>();
                context.put(key, scripts);
            }
            scripts.add(script);
        }
    }

    protected void executeScripts(DataContext context, String key) {
        @SuppressWarnings("unchecked")
        Set<String> scripts = (Set<String>) context.get(key);
        executeScripts(context, key, scripts, BooleanUtils.isTrue((Boolean) context.get(FAIL_ON_ERROR_KEY)));
    }

    protected abstract void executeScripts(DataContext context, String key, Set<String> scripts, boolean isFailOnError);

    protected boolean isIgnoreCase() {
        return engine.getParameterService().is(ParameterConstants.DB_METADATA_IGNORE_CASE);
    }

    public boolean handlesMissingTable(DataContext context, Table table) {
        if (engine != null && engine.getParameterService() != null
                && engine.getParameterService().is(ParameterConstants.BSH_LOAD_FILTER_HANDLES_MISSING_TABLES)) {
            return true;
        } else {
            String tableName = table.getFullyQualifiedName();
            if (isIgnoreCase()) {
                tableName = tableName.toUpperCase();
            }
            return loadFilters.containsKey(tableName);
        }
    }
}
