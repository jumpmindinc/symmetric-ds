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
package org.jumpmind.symmetric.map;

import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ext.INodeGroupExtensionPoint;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterFilterAdapter;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;
import org.jumpmind.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scripting.ScriptCompilationException;

public class ColumnDataFilters extends DatabaseWriterFilterAdapter implements IDatabaseWriterFilter, INodeGroupExtensionPoint {
    final Logger log = LoggerFactory.getLogger(getClass());
    private String[] nodeGroupIdsToApplyTo;
    List<TableColumnValueFilter> filters;
    private boolean ignoreCase = true;
    private boolean enabled = true;

    @Override
    public boolean beforeWrite(
            DataContext context, Table table, CsvData data) {
        filterColumnValues(context, table, data);
        return true;
    }

    protected void filterColumnValues(Context context, Table table, CsvData data) {
        if (enabled && filters != null) {
            for (TableColumnValueFilter filteredColumn : filters) {
                try {
                    if (filteredColumn.isEnabled()
                            && ((ignoreCase && filteredColumn.getTableName().equalsIgnoreCase(
                                    table.getName())) || (!ignoreCase && filteredColumn
                                            .getTableName().equals(table.getName())))) {
                        String columnName = filteredColumn.getColumnName();
                        int index = table.getColumnIndex(columnName);
                        if (index < 0 && ignoreCase) {
                            columnName = columnName.toUpperCase();
                            index = table.getColumnIndex(columnName);
                            if (index < 0) {
                                columnName = columnName.toLowerCase();
                                index = table.getColumnIndex(columnName);
                            }
                        }
                        if (index >= 0) {
                            try {
                                String[] columnValues = data.getParsedData(CsvData.ROW_DATA);
                                if (columnValues != null && columnValues.length > index) {
                                    columnValues[index] = filteredColumn.getFilter().filter(
                                            filteredColumn.getTableName(),
                                            filteredColumn.getColumnName(), columnValues[index],
                                            context);
                                }
                            } catch (RuntimeException ex) {
                                // Try to log script errors so they are more
                                // readable
                                Throwable causedBy = ex;
                                do {
                                    causedBy = ex.getCause();
                                    if (causedBy instanceof ScriptCompilationException) {
                                        log.error("{}", causedBy.getMessage());
                                        throw new RuntimeException(causedBy.getMessage(), ex);
                                    }
                                } while (causedBy != null);
                                throw ex;
                            }
                        }
                    }
                } catch (RuntimeException ex) {
                    log.error("Failed to transform value for column {} on table {}", filteredColumn.getColumnName(),
                            filteredColumn.getTableName());
                    throw ex;
                }
            }
        }
    }

    public void setFilters(List<TableColumnValueFilter> filters) {
        this.filters = filters;
    }

    public String[] getNodeGroupIdsToApplyTo() {
        return this.nodeGroupIdsToApplyTo;
    }

    public void setNodeGroupIdsToApplyTo(String[] nodeGroupIdsToApplyTo) {
        this.nodeGroupIdsToApplyTo = nodeGroupIdsToApplyTo;
    }

    public void setNodeGroupIdToApplyTo(String nodeGroupId) {
        this.nodeGroupIdsToApplyTo = new String[] { nodeGroupId };
    }

    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
