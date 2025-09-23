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
package org.jumpmind.symmetric.io;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Strings;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.io.data.transform.ColumnPolicy;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;

public class DbCompareTables {
    private Table sourceTable;
    private Table targetTable;
    private TransformTableNodeGroupLink transform;
    private Map<Column, Column> columnMapping = new LinkedHashMap<Column, Column>();

    public DbCompareTables(Table sourceTable, Table targetTable) {
        this.sourceTable = sourceTable;
        this.targetTable = targetTable;
    }

    public Map<Column, Column> getColumnMapping() {
        return columnMapping;
    }

    public void setColumnMapping(Map<Column, Column> columnMapping) {
        this.columnMapping = columnMapping;
    }

    public void addColumnMapping(Column sourceColumn, Column targetColumn) {
        columnMapping.put(sourceColumn, targetColumn);
    }

    public void applyColumnMappings() {
        columnMapping.clear();
        if (transform != null && !CollectionUtils.isEmpty(transform.getTransformColumns())) {
            applyColumnMappingsFromTransform();
        } else {
            applyColumnMappingsDefault();
        }
    }

    protected void applyColumnMappingsFromTransform() {
        for (Column sourceColumn : sourceTable.getColumns()) {
            List<TransformColumn> sourceTransformColumns = transform.getTransformColumnFor(sourceColumn.getName());
            if (!sourceTransformColumns.isEmpty()) {
                TransformColumn transformColumn = sourceTransformColumns.get(0);
                Column targetColumn = targetTable.getColumnWithName(transformColumn.getTargetColumnName());
                if (transformColumn.isPk()) {
                    sourceColumn.setPrimaryKey(true);
                }
                columnMapping.put(sourceColumn, targetColumn);
            } else {
                if (transform.getColumnPolicy() == ColumnPolicy.SPECIFIED) {
                    sourceTable.removeColumn(sourceColumn);
                } else {
                    mapColumnDefault(sourceColumn);
                }
            }
        }
    }

    protected void applyColumnMappingsDefault() {
        for (Column sourceColumn : sourceTable.getColumns()) {
            mapColumnDefault(sourceColumn);
        }
    }

    protected void mapColumnDefault(Column sourceColumn) {
        for (Column targetColumn : targetTable.getColumns()) {
            if (Strings.CI.equals(sourceColumn.getName(), targetColumn.getName())) {
                columnMapping.put(sourceColumn, targetColumn);
            }
        }
    }

    public TransformTableNodeGroupLink getTransform() {
        return transform;
    }

    public void setTransform(TransformTableNodeGroupLink transform) {
        this.transform = transform;
    }

    public Table getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(Table sourceTable) {
        this.sourceTable = sourceTable;
    }

    public Table getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(Table targetTable) {
        this.targetTable = targetTable;
    }

    public void filterExcludedColumns(DbCompareConfig config) {
        filterExcludedColumns(config, sourceTable);
        filterExcludedColumns(config, targetTable);
    }

    private void filterExcludedColumns(DbCompareConfig config, Table table) {
        for (Column column : table.getColumnsAsList()) {
            if (!config.shouldIncludeColumn(table.getName(), column.getName())) {
                if (table.getPrimaryKeyColumnsAsList().contains(column)) {
                    throw new SymmetricException("Invalid config - cannot exclude a primary key column: " + column + " (Table " + table + ")");
                }
                table.removeColumn(column);
            }
        }
    }
}
