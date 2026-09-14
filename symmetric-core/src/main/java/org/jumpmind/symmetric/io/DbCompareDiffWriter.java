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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbCompareDiffWriter {
    private final static Logger log = LoggerFactory.getLogger(DbCompareDiffWriter.class);
    // Continue after parsing errors
    private boolean continueAfterError = false;
    private boolean error = false;
    private Throwable throwable = null;
    private String longVarbinary = "LONGVARBINARY";
    private String varchar = "VARCHAR";

    public DbCompareDiffWriter(ISymmetricEngine targetEngine, DbCompareTables tables, OutputStream stream) {
        super();
        this.targetEngine = targetEngine;
        this.tables = tables;
        this.stream = stream;
    }

    private ISymmetricEngine targetEngine;
    private DbCompareTables tables;
    private OutputStream stream;

    public void writeDelete(DbCompareRow targetCompareRow) {
        if (stream == null) {
            return;
        }
        try {
            Table table = targetCompareRow.getTable();
            DmlStatement statement = targetEngine.getDatabasePlatform().createDmlStatement(DmlType.DELETE,
                    table.getCatalog(), table.getSchema(), table.getName(),
                    table.getPrimaryKeyColumns(), null,
                    null, null);
            Row row = new Row(targetCompareRow.getTable().getPrimaryKeyColumnCount());
            for (int i = 0; i < targetCompareRow.getTable().getPrimaryKeyColumnCount(); i++) {
                if (isUniType(table.getColumn(i).getJdbcTypeName())) {
                    table.getColumn(i).setMappedType(varchar);
                    String convertedValue = DbValueComparator.convertString(targetCompareRow.getRowValues().get(targetCompareRow.getTable().getColumn(i)
                            .getName()), targetCompareRow.getTable().getColumn(i), false);
                    row.put(table.getColumn(i).getName(), convertedValue);
                } else {
                    row.put(table.getColumn(i).getName(),
                            targetCompareRow.getRowValues().get(targetCompareRow.getTable().getColumn(i).getName()));
                }
            }
            String sql = statement.buildDynamicDeleteSql(BinaryEncoding.HEX, row, false, true);
            writeLine(sql);
            for (int i = 0; i < targetCompareRow.getTable().getPrimaryKeyColumnCount(); i++) {
                if (isUniType(table.getColumn(i).getJdbcTypeName())) {
                    table.getColumn(i).setMappedType(longVarbinary);
                }
            }
        } catch (RuntimeException e) {
            error = true;
            throwable = e;
            log.error(e.getMessage(), e);
            if (!isContinueAfterError()) {
                throw e;
            }
        }
    }

    public void writeInsert(DbCompareRow sourceCompareRow) {
        if (stream == null) {
            return;
        }
        try {
            Table targetTable = tables.getTargetTable();
            List<Column> targetColumns = new ArrayList<Column>();
            List<Column> targetPkColumns = new ArrayList<Column>();
            for (Column targetColumn : targetTable.getColumns()) {
                if (tables.getColumnMapping().containsValue(targetColumn) || !targetColumn.isRequired()
                        || targetColumn.getDefaultValue() == null) {
                    targetColumns.add(targetColumn);
                    if (targetColumn.isPrimaryKey()) {
                        targetPkColumns.add(targetColumn);
                    }
                }
            }
            DmlStatement statement = targetEngine.getDatabasePlatform().createDmlStatement(DmlType.INSERT,
                    targetTable.getCatalog(), targetTable.getSchema(), targetTable.getName(),
                    targetPkColumns.toArray(new Column[targetPkColumns.size()]),
                    targetColumns.toArray(new Column[targetColumns.size()]), null, null);
            Row row = new Row(targetColumns.size());
            for (Column sourceColumn : tables.getSourceTable().getColumns()) {
                Column targetColumn = tables.getColumnMapping().get(sourceColumn);
                if (targetColumn == null) {
                    continue;
                }
                boolean targetUnitype = isUniType(targetColumn.getJdbcTypeName());
                boolean sourceUnitype = isUniType(sourceColumn.getJdbcTypeName());
                if (targetUnitype || sourceUnitype) {
                    if (sourceUnitype) {
                        sourceColumn.setMappedType("VARCHAR");
                        if (sourceColumn.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                            row.put(targetColumn.getName(), sourceCompareRow.getRowValues().get(sourceColumn.getName()));
                        } else {
                            String convertedString = DbValueComparator.convertString(sourceCompareRow.getRowValues().get(sourceColumn.getName()), sourceColumn,
                                    false);
                            row.put(targetColumn.getName(), convertedString);
                        }
                    } else if (targetUnitype) {
                        targetColumn.setMappedType("VARCHAR");
                        row.put(targetColumn.getName(), sourceCompareRow.getRowValues().get(sourceColumn.getName()));
                    }
                } else {
                    row.put(targetColumn.getName(), sourceCompareRow.getRowValues().get(sourceColumn.getName()));
                }
            }
            String sql = statement.buildDynamicSql(BinaryEncoding.HEX, row, false, false);
            writeLine(sql);
            for (Column sourceColumn : tables.getSourceTable().getColumns()) {
                Column targetColumn = tables.getColumnMapping().get(sourceColumn);
                if (targetColumn == null) {
                    continue;
                }
                if (isUniType(targetColumn.getJdbcTypeName())) {
                    targetColumn.setMappedType(longVarbinary);
                }
                if (isUniType(sourceColumn.getJdbcTypeName())) {
                    sourceColumn.setMappedType(longVarbinary);
                }
            }
        } catch (RuntimeException e) {
            error = true;
            throwable = e;
            log.error(e.getMessage(), e);
            if (!isContinueAfterError()) {
                throw e;
            }
        }
    }

    public void writeUpdate(DbCompareRow targetCompareRow, Map<Column, String> deltas) {
        if (stream == null) {
            return;
        }
        try {
            Table table = targetCompareRow.getTable();
            Map<Column, String> deltasCopy = new LinkedHashMap<Column, String>();
            for (Column column : deltas.keySet()) {
                deltasCopy.put((Column) column.clone(), deltas.get(column));
            }
            Column[] changedColumns = deltasCopy.keySet().toArray(new Column[deltasCopy.keySet().size()]);
            DmlStatement statement = targetEngine.getDatabasePlatform().createDmlStatement(DmlType.UPDATE,
                    table.getCatalog(), table.getSchema(), table.getName(),
                    table.getPrimaryKeyColumns(), changedColumns,
                    null, null);
            Row row = new Row(changedColumns.length + table.getPrimaryKeyColumnCount());
            for (Column changedColumn : deltasCopy.keySet()) {
                String value = deltasCopy.get(changedColumn);
                if (isUniType(targetCompareRow.getTable().getColumnWithName(changedColumn.getName()).getJdbcTypeName())) {
                    changedColumn.setMappedType(varchar);
                    row.put(changedColumn.getName(), value);
                } else if (isUniType(changedColumn.getJdbcTypeName())) {
                    changedColumn.setMappedType(varchar);
                    if (changedColumn.getJdbcTypeName().equalsIgnoreCase("unitext")) {
                        row.put(changedColumn.getName(), value);
                    } else {
                        String convertedValue = DbValueComparator.convertString(value, changedColumn, false);
                        row.put(changedColumn.getName(), convertedValue);
                    }
                } else {
                    row.put(changedColumn.getName(), value);
                }
            }
            for (String pkColumnName : table.getPrimaryKeyColumnNames()) {
                String value = targetCompareRow.getRow().getString(pkColumnName);
                Column pkColumn = targetCompareRow.getTable().getColumnWithName(pkColumnName);
                if (isUniType(pkColumn.getJdbcTypeName())) {
                    String convertedValue = DbValueComparator.convertString(value, pkColumn, true);
                    row.put(pkColumnName, convertedValue);
                } else {
                    row.put(pkColumnName, value);
                }
            }
            String sql = statement.buildDynamicSql(BinaryEncoding.HEX, row, false, true);
            writeLine(sql);
            for (Column changedColumn : deltasCopy.keySet()) {
                if (isUniType(changedColumn.getJdbcTypeName())) {
                    changedColumn.setMappedType(longVarbinary);
                }
            }
            for (String pkColumnName : table.getPrimaryKeyColumnNames()) {
                Column pkColumn = targetCompareRow.getTable().getColumnWithName(pkColumnName);
                if (isUniType(pkColumn.getJdbcTypeName())) {
                    pkColumn.setMappedType(longVarbinary);
                }
            }
        } catch (RuntimeException e) {
            error = true;
            throwable = e;
            log.error(e.getMessage(), e);
            if (!isContinueAfterError()) {
                throw e;
            }
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                log.debug("CAUGHT EXCEPTION while closing stream", e);
            }
            stream = null;
        }
    }

    protected void writeLine(String line) {
        try {
            stream.write(line.getBytes());
            stream.write("\r\n".getBytes());
        } catch (Exception ex) {
            throw new RuntimeException("failed to write to stream", ex);
        }
    }

    public boolean isUniType(String type) {
        return type.equalsIgnoreCase("UNITEXT") || type.equalsIgnoreCase("UNICHAR") || type.equalsIgnoreCase("UNIVARCHAR");
    }

    public void setContinueAfterError(boolean continueAfterError) {
        this.continueAfterError = continueAfterError;
    }

    public boolean isContinueAfterError() {
        return continueAfterError;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public boolean isError() {
        return error;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
