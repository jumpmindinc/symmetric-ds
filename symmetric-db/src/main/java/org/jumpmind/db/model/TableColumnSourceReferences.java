package org.jumpmind.db.model;

import java.util.ArrayList;

import org.apache.commons.lang3.Strings;

/***
 * Holds array of column references (from source to target) based on matching names. Target table columns are a priority. Unreferenced source columns are
 * excluded. The searchKey contains table names and helps find this object in a map.
 */
public class TableColumnSourceReferences extends ArrayList<TableColumnSourceReferences.ColumnSourceReferenceEntry> {
    private static final long serialVersionUID = 1L;
    private String searchKey;

    /***
     * Builds array of column references based on matching names
     */
    public TableColumnSourceReferences(Table sourceTable, Table targetTable) {
        super(sourceTable.getColumns().length);
        this.searchKey = generateSearchKey(sourceTable, targetTable);
        Column[] sourceColumns = sourceTable.getColumns();
        Column[] targetColumns = targetTable.getColumns();
        for (int targetColumnNo = 0; targetColumnNo < targetColumns.length; targetColumnNo++) {
            Column targetColumn = targetColumns[targetColumnNo];
            for (int sourceColumnNo = 0; sourceColumnNo < sourceColumns.length; sourceColumnNo++) {
                Column sourceColumn = sourceColumns[sourceColumnNo];
                if (Strings.CI.equals(sourceColumn.getName(), targetColumn.getName())) {
                    this.add(new ColumnSourceReferenceEntry(sourceColumnNo, targetColumnNo, sourceColumn, targetColumn));
                    break;
                }
            }
        }
    }

    /***
     * Builds key for storing/searching this object in a map
     */
    public static String generateSearchKey(Table sourceTable, Table targetTable) {
        return sourceTable.getFullyQualifiedName() + targetTable.getFullyQualifiedName();
    }

    public String getSearchKey() {
        return this.searchKey;
    }

    /***
     * Compare existing column mappings to target table.
     */
    public boolean isMatchingTables(Table sourceTable, Table targetTable) {
        Column[] targetColumns = targetTable.getColumns();
        if (targetColumns.length != this.size()) {
            return false;
        }
        Column[] sourceColumns = sourceTable.getColumns();
        for (int targetColumnNo = 0; targetColumnNo < targetColumns.length; targetColumnNo++) {
            Column targetColumn = targetColumns[targetColumnNo];
            ColumnSourceReferenceEntry columnReference = this.get(targetColumnNo);
            if (!targetColumn.getName().equals(columnReference.targetColumn.getName()) || targetColumnNo != columnReference.targetColumnNo) {
                return false;
            }
            if (columnReference.sourceColumnNo >= sourceColumns.length
                    || !sourceColumns[columnReference.sourceColumnNo].getName().equals(columnReference.sourceColumn.getName())) {
                return false;
            }
        }
        return true;
    }

    /***
     * Internal class for column mappings to move data efficiently. Column numbers are used to copy data from source to target.
     */
    public record ColumnSourceReferenceEntry(int sourceColumnNo, int targetColumnNo, Column sourceColumn, Column targetColumn) {
    }
}
