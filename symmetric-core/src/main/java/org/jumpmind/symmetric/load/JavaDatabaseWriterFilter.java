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
package org.jumpmind.symmetric.load;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.LoadFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaDatabaseWriterFilter extends DynamicDatabaseWriterFilter {
    public final static String CODE_START = "import org.jumpmind.symmetric.load.*;\n"
            + "import org.jumpmind.symmetric.io.data.*;\n"
            + "import org.jumpmind.db.model.*;\n"
            + "import java.util.*;\n"
            + "public class JavaDatabaseWriterFilterExt implements JavaDatabaseWriterFilter.JavaLoadFilter { \n"
            + "    public boolean execute(DataContext context, Table table, CsvData data, Exception error) {\n\n";
    public final static String CODE_END = "\n\n   }\n}\n";

    public interface JavaLoadFilter {
        public boolean execute(DataContext context, Table table, CsvData data, Exception error);
    }

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public JavaDatabaseWriterFilter(ISymmetricEngine engine,
            Map<String, List<LoadFilter>> loadFilters) {
        super(engine, loadFilters);
    }

    @Override
    protected boolean processLoadFilters(DataContext context, Table table, CsvData data, Exception error,
            WriteMethod writeMethod, List<LoadFilter> loadFiltersForTable) {
        boolean writeRow = true;
        LoadFilter currentFilter = null;
        try {
            for (LoadFilter filter : loadFiltersForTable) {
                currentFilter = filter;
                if (filter.isFilterOnDelete()
                        && data.getDataEventType().equals(DataEventType.DELETE)
                        || filter.isFilterOnInsert()
                                && data.getDataEventType().equals(DataEventType.INSERT)
                        || filter.isFilterOnUpdate()
                                && data.getDataEventType().equals(DataEventType.UPDATE)) {
                    if (writeMethod.equals(WriteMethod.BEFORE_WRITE)
                            && StringUtils.isNotBlank(filter.getBeforeWriteScript())) {
                        writeRow = getCompiledClass(filter.getBeforeWriteScript()).execute(context, table, data, error);
                    } else if (writeMethod.equals(WriteMethod.AFTER_WRITE)
                            && StringUtils.isNotBlank(filter.getAfterWriteScript())) {
                        writeRow = getCompiledClass(filter.getAfterWriteScript()).execute(context, table, data, error);
                    } else if (writeMethod.equals(WriteMethod.HANDLE_ERROR)
                            && StringUtils.isNotBlank(filter.getHandleErrorScript())) {
                        writeRow = getCompiledClass(filter.getHandleErrorScript()).execute(context, table, data, error);
                    }
                }
            }
        } catch (Exception ex) {
            String formattedMessage = String.format(
                    "Error executing Java load filter %s on table %s. The error was: %s",
                    new Object[] { currentFilter != null ? currentFilter.getLoadFilterId() : "N/A",
                            table.getName(), ex.getMessage() });
            log.error(formattedMessage);
            if (currentFilter.isFailOnError()) {
                throw new SymmetricException(formattedMessage, ex);
            }
        }
        return writeRow;
    }

    @Override
    protected void executeScripts(DataContext context, String key, Set<String> scripts, boolean isFailOnError) {
        try {
            if (scripts != null) {
                for (String script : scripts) {
                    getCompiledClass(script).execute(context, null, null, null);
                }
            }
        } catch (Exception e) {
            String errorMsg = String.format("Java load filter %s with error %s", new Object[] {
                    key, e.getMessage() });
            log.error(errorMsg);
            if (isFailOnError) {
                throw new SymmetricException(errorMsg);
            }
        }
    }

    public static int countHeaderLines() {
        return CODE_START.split("\n").length;
    }

    public JavaLoadFilter getCompiledClass(String javaExpression) throws Exception {
        String javaCode = CODE_START + javaExpression + CODE_END;
        return (JavaLoadFilter) engine.getExtensionService().getCompiledClass(javaCode);
    }
}
