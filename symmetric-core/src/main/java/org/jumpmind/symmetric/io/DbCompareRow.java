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
package org.jumpmind.symmetric.io;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbCompareRow {
    final static Logger log = LoggerFactory.getLogger(DbCompareRow.class);
    private DbValueComparator dbValueComparator;
    private ISymmetricEngine engine;
    private Table table;
    private Row row;
    private Map<String, String> rowValues;
    private Map<String, String> rowPkValues;

    public DbCompareRow(ISymmetricEngine engine, DbValueComparator dbValueComparator, Table table, Row row) {
        this.engine = engine;
        this.table = table;
        this.row = row;
        this.dbValueComparator = dbValueComparator;
        loadRowValues();
    }

    public int comparePks(DbCompareTables tables, DbCompareRow targetRow) {
        for (Column sourcePkColumn : table.getPrimaryKeyColumns()) {
            Column targetPkColumn = tables.getColumnMapping().get(sourcePkColumn);
            if (targetPkColumn == null) {
                return 0;
            }
            int result = dbValueComparator.compareValues(sourcePkColumn, targetPkColumn,
                    rowValues.get(sourcePkColumn.getName()), targetRow.getRowValues().get(targetPkColumn.getName()));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    public Map<Column, String> compareTo(DbCompareTables tables, DbCompareRow targetRow) {
        Map<Column, String> deltas = new LinkedHashMap<Column, String>();
        for (Column sourceColumn : table.getColumns()) {
            Column targetColumn = tables.getColumnMapping().get(sourceColumn);
            if (targetColumn == null) {
                continue;
            }
            int result = dbValueComparator.compareValues(sourceColumn, targetColumn,
                    rowValues.get(sourceColumn.getName()), targetRow.getRowValues().get(targetColumn.getName()));
            if (result != 0) {
                if (sourceColumn.getJdbcTypeName().equalsIgnoreCase("unitext") || sourceColumn.getJdbcTypeName().equalsIgnoreCase("unichar") || sourceColumn
                        .getJdbcTypeName().equalsIgnoreCase("univarchar")) {
                    deltas.put(sourceColumn, rowValues.get(sourceColumn.getName()));
                } else {
                    deltas.put(targetColumn, rowValues.get(sourceColumn.getName()));
                }
            }
        }
        return deltas;
    }

    protected void loadRowValues() {
        String[] stringValues = engine.getDatabasePlatform().getStringValues(BinaryEncoding.HEX, table.getColumns(), row, false, false);
        Map<String, String> localRowValues = new LinkedHashMap<String, String>();
        Map<String, String> localPkRowValues = new LinkedHashMap<String, String>();
        for (int i = 0; i < stringValues.length; i++) {
            if (i < table.getColumnCount()) {
                String columnName = table.getColumn(i).getName();
                String stringValue = stringValues[i];
                localRowValues.put(columnName, stringValue);
            }
            if (i < table.getPrimaryKeyColumnCount()) {
                String columnName = table.getPrimaryKeyColumns()[i].getName();
                String stringValue = stringValues[i];
                localPkRowValues.put(columnName, stringValue);
            }
        }
        this.rowValues = localRowValues;
        this.rowPkValues = localPkRowValues;
    }

    public DbValueComparator getDbValueComparator() {
        return dbValueComparator;
    }

    public void setDbValueComparator(DbValueComparator dbValueComparator) {
        this.dbValueComparator = dbValueComparator;
    }

    public ISymmetricEngine getEngine() {
        return engine;
    }

    public void setEngine(ISymmetricEngine engine) {
        this.engine = engine;
    }

    public Table getTable() {
        return table;
    }

    public void setTable(Table table) {
        this.table = table;
    }

    public Row getRow() {
        return row;
    }

    public void setRow(Row row) {
        this.row = row;
    }

    public Map<String, String> getRowValues() {
        return rowValues;
    }

    public void setRowValues(Map<String, String> rowValues) {
        this.rowValues = rowValues;
    }

    public Map<String, String> getRowPkValues() {
        return rowPkValues;
    }

    public void setRowPkValues(Map<String, String> rowPkValues) {
        this.rowPkValues = rowPkValues;
    }
}
