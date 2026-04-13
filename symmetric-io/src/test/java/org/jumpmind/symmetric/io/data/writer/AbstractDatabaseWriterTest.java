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
package org.jumpmind.symmetric.io.data.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Proxy;
import java.sql.Types;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.TableColumnSourceReferences;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.Test;

/***
 * Checks column source lookups in AbstractDatabaseWriter using a wrapper class StubAbstractDatabaseWriter - see below.
 */
public class AbstractDatabaseWriterTest {
    protected static IDatabasePlatform platform;
    protected DatabaseWriterSettings writerSettings = new DatabaseWriterSettings();

    /**
     * Helper. Sets all columns in given table to the same data type
     */
    public void setTableColumnTypes(Table sourceTable) {
        for (Column column : sourceTable.getColumnsAsList()) {
            column.setJdbcTypeCode(Types.VARCHAR);
            column.setJdbcTypeName("VARCHAR");
            column.setMappedTypeCode(Types.VARCHAR);
            column.setMappedType("VARCHAR");
            column.setSize("100");
        }
    }

    @Test
    public void testGenerateTableColumnSourceReferences_KeyMatch() {
        String[] columnNames = { "c1", "c2", "c3", "c4", "c5" };
        String[] keyNames = { "c1", "c2" };
        Table sourceTable = new Table("catalog1", "schema1", "table1", columnNames, keyNames);
        Table targetTable = new Table("catalog2", "schema2", "table2", columnNames, keyNames);
        String expectedKey = sourceTable.getFullyQualifiedTableName() + targetTable.getFullyQualifiedTableName();
        String key = TableColumnSourceReferences.generateSearchKey(sourceTable, targetTable);
        assertEquals(expectedKey, key);
    }

    @Test
    public void testTableColumnSourceReferences_AllColumnsMatch() {
        String[] sourceColumnNames = { "c1", "c2", "c3", "c4", "c5" };
        String[] targetColumnNames = { "c2", "c5", "c3", "c1", "c5" };
        String[] keyNames = { "c1", "c2" };
        Table sourceTable = new Table("catalog1", "schema1", "table1", sourceColumnNames, keyNames);
        Table targetTable = new Table("catalog2", "schema2", "table2", targetColumnNames, keyNames);
        TableColumnSourceReferences columnReferences = new TableColumnSourceReferences(sourceTable, targetTable);
        assertEquals(sourceColumnNames.length, columnReferences.size());
        assertEquals(targetColumnNames.length, columnReferences.size());
        assertTrue(columnReferences.isMatchingTables(sourceTable, targetTable));
        for (TableColumnSourceReferences.ColumnSourceReferenceEntry destinationEntry : columnReferences) {
            assertEquals(targetColumnNames[destinationEntry.targetColumnNo()], sourceColumnNames[destinationEntry.sourceColumnNo()]);
        }
    }

    @Test
    public void testTableColumnSourceReferences_FewColumnsMatchTarget() {
        String[] sourceColumnNames = { "c1", "c2", "c3", "c4", "c5" };
        String[] targetColumnNames = { "c2", "c5", "c3", "c1" };
        String[] keyNames = { "c1", "c2" };
        Table sourceTable = new Table("catalog1", "schema1", "table1", sourceColumnNames, keyNames);
        Table targetTable = new Table("catalog2", "schema2", "table2", targetColumnNames, keyNames);
        TableColumnSourceReferences columnReferences = new TableColumnSourceReferences(sourceTable, targetTable);
        assertNotEquals(sourceColumnNames.length, columnReferences.size());
        assertEquals(targetColumnNames.length, columnReferences.size());
        assertTrue(columnReferences.isMatchingTables(sourceTable, targetTable));
        for (TableColumnSourceReferences.ColumnSourceReferenceEntry destinationEntry : columnReferences) {
            assertEquals(targetColumnNames[destinationEntry.targetColumnNo()], sourceColumnNames[destinationEntry.sourceColumnNo()]);
        }
    }

    @Test
    public void testGetRowData_FullMatch4Columns() {
        String[] sourceColumnNames = { "c1", "c2", "c3", "c4", "c5" };
        String[] targetColumnNames = { "c2", "c5", "c3", "c1" };
        String[] keyNames = { "c1", "c2" };
        Table sourceTable = new Table("catalog1", "schema1", "table1", sourceColumnNames, keyNames);
        setTableColumnTypes(sourceTable);
        Table targetTable = new Table("catalog2", "schema2", "table2", targetColumnNames, keyNames);
        setTableColumnTypes(targetTable);
        String[] sourceRowData = { "v1", "v2", "v3", "v4", "v5" };
        String[] expectedRowData = { "v2", "v5", "v3", "v1" };
        CsvData csvData = new CsvData(DataEventType.INSERT, sourceRowData);
        StubAbstractDatabaseWriter abstractDatabaseWriter = new StubAbstractDatabaseWriter();
        abstractDatabaseWriter.start(sourceTable, targetTable);
        String[] rowData = abstractDatabaseWriter.getRowDataNew(csvData, CsvData.ROW_DATA);
        // String[] rowData = abstractDatabaseWriter.getRowDataOld(csvData, CsvData.ROW_DATA);
        assertNotNull(rowData);
        assertEquals(targetColumnNames.length, rowData.length);
        assertArrayEquals(expectedRowData, rowData);
    }

    /**
     * Source and target have lots of columns with same names and only 5 differences. Time the getRowData() method! 10 => 0 ms ; 100 cols * 1 row => 4 ms ; 1000
     * cols * 1 row=> 19 ms; 100 cols*1000 rows => 9 ms; OLD: 20 ros*500000ros=>706 ms NEW=73ms!
     */
    @Test
    public void testGetRowData_LotsOfRandomAndFewSkippedColumns() {
        int sourceColumnCount = 20;
        int skipColumnCount = 5;
        int targetColumnCount = sourceColumnCount - skipColumnCount;
        int maxRows = 500000;
        String[] sourceColumnNames = new String[sourceColumnCount];
        String[] targetColumnNames = new String[targetColumnCount];
        String[] sourceRowData = new String[sourceColumnCount];
        String[] expectedRowData = new String[targetColumnCount];
        Random randomSourceColumn = new Random();
        HashSet<Integer> randomSourceColumnSet = new HashSet<Integer>(targetColumnCount);
        for (int columnNo = 0; columnNo < sourceColumnCount; columnNo++) {
            sourceColumnNames[columnNo] = String.format("column%d", columnNo);
            sourceRowData[columnNo] = String.format("value%d", columnNo);
            if (columnNo < targetColumnCount) {
                // Randomly assign source column
                int randomSourceColumnNo = randomSourceColumn.nextInt(sourceColumnCount);
                while (randomSourceColumnSet.contains(randomSourceColumnNo)) {
                    randomSourceColumnNo = randomSourceColumn.nextInt(sourceColumnCount);
                }
                randomSourceColumnSet.add(randomSourceColumnNo);
                targetColumnNames[columnNo] = String.format("column%d", randomSourceColumnNo);
                expectedRowData[columnNo] = String.format("value%d", randomSourceColumnNo);
            }
        }
        String[] keyNames = { "column1", "column2" };
        Table sourceTable = new Table("catalog1", "schema1", "table1", sourceColumnNames, keyNames);
        setTableColumnTypes(sourceTable);
        Table targetTable = new Table("catalog2", "schema2", "table2", targetColumnNames, keyNames);
        setTableColumnTypes(targetTable);
        CsvData csvData = new CsvData(DataEventType.INSERT, sourceRowData);
        StubAbstractDatabaseWriter abstractDatabaseWriter = new StubAbstractDatabaseWriter();
        abstractDatabaseWriter.start(sourceTable, targetTable);
        String[] rowData = null;
        for (int rowNo = 0; rowNo < maxRows; rowNo++) {
            rowData = abstractDatabaseWriter.getRowDataNew(csvData, CsvData.ROW_DATA);
            // rowData = abstractDatabaseWriter.getRowDataOld(csvData, CsvData.ROW_DATA);
        }
        assertNotNull(rowData);
        assertEquals(targetColumnNames.length, rowData.length);
        assertArrayEquals(expectedRowData, rowData);
        // System.out.println("testGetRowData_LotsOfRandomAndFewSkippedColumns done; Runtime ms=" + (System.currentTimeMillis()
        // - startTime));
        abstractDatabaseWriter.close();
    }

    @Test
    public void testClearTargetColumnReferencesMap() {
        String[] sourceColumnNames = { "c1", "c2", "c3", "c4", "c5" };
        String[] targetColumnNames = { "c2", "c5", "c3", "c1" };
        String[] keyNames = { "c1", "c2" };
        Table sourceTable = new Table("catalog1", "schema1", "table1", sourceColumnNames, keyNames);
        setTableColumnTypes(sourceTable);
        Table targetTable = new Table("catalog2", "schema2", "table2", targetColumnNames, keyNames);
        setTableColumnTypes(targetTable);
        StubAbstractDatabaseWriter abstractDatabaseWriter = new StubAbstractDatabaseWriter();
        assertEquals(0, abstractDatabaseWriter.getTargetColumnReferencesMapSize());
        abstractDatabaseWriter.start(sourceTable, targetTable);
        assertEquals(1, abstractDatabaseWriter.getTargetColumnReferencesMapSize());
        abstractDatabaseWriter.clearTargetColumnReferencesMap();
        assertEquals(0, abstractDatabaseWriter.getTargetColumnReferencesMapSize());
        abstractDatabaseWriter.getTargetColumnReferencesMap();
        assertEquals(1, abstractDatabaseWriter.getTargetColumnReferencesMapSize());
        abstractDatabaseWriter.clearTargetColumnReferencesMap();
        assertEquals(0, abstractDatabaseWriter.getTargetColumnReferencesMapSize());
        abstractDatabaseWriter.refreshTargetColumnReferencesMap();
        assertEquals(1, abstractDatabaseWriter.getTargetColumnReferencesMapSize());
    }

    @Test
    public void testHasFilterThatHandlesMissingTable_BshProxyNullReturn() {
        // Simulate a BeanShell script-based filter (JDK dynamic proxy) that does not implement
        // handlesMissingTable(). The proxy returns null, which Java cannot auto-unbox to boolean,
        // throwing a NullPointerException — the scenario the fix in hasFilterThatHandlesMissingTable() guards against.
        IDatabaseWriterFilter proxyFilter = (IDatabaseWriterFilter) Proxy.newProxyInstance(
                IDatabaseWriterFilter.class.getClassLoader(),
                new Class<?>[] { IDatabaseWriterFilter.class },
                (proxy, method, args) -> null);
        Table sourceTable = new Table("catalog1", "schema1", "table1", new String[] { "col1", "col2" }, new String[] { "key1" });
        StubAbstractDatabaseWriter writer = new StubAbstractDatabaseWriter();
        writer.getWriterSettings().setDatabaseWriterFilters(Arrays.asList(proxyFilter));
        writer.start(sourceTable, null);
        assertTrue(writer.getTargetTable() == null);
    }

    /***
     * Test wrapper class for the AbstractDatabaseWriter class Includes a copy of the older code (getRowDataOld) - before optimization.
     */
    protected class StubAbstractDatabaseWriter extends AbstractDatabaseWriter {
        public StubAbstractDatabaseWriter() {
            super();
        }

        public String[] getRowDataNew(CsvData data, String dataType) {
            return getRowData(data, dataType);
        }

        protected String[] getRowDataOld(CsvData data, String dataType) {
            String[] targetValues = new String[targetTable.getColumnCount()];
            String[] targetColumnNames = targetTable.getColumnNames();
            String[] originalValues = data.getParsedData(dataType);
            String[] sourceColumnNames = sourceTable.getColumnNames();
            if (originalValues != null) {
                for (int i = 0; i < sourceColumnNames.length && i < originalValues.length; i++) {
                    for (int t = 0; t < targetColumnNames.length; t++) {
                        if (sourceColumnNames[i].equalsIgnoreCase(targetColumnNames[t])) {
                            targetValues[t] = originalValues[i];
                            break;
                        }
                    }
                }
                return targetValues;
            } else {
                return null;
            }
        }

        protected int getTargetColumnReferencesMapSize() {
            return this.targetColumnSourceReferencesMap.size();
        }

        protected void start(Table sourceTable, Table targetTable) {
            super.start(sourceTable);
            clearTargetColumnReferencesMap();
            this.targetTable = targetTable;
            refreshTargetColumnReferencesMap();
        }

        @Override
        protected Table lookupTableAtTarget(Table table) {
            return this.targetTable;
        }

        @Override
        protected LoadStatus insert(CsvData data) {
            // Auto-generated method stub for AbstractDatabaseWriter
            return null;
        }

        @Override
        protected LoadStatus delete(CsvData data, boolean useConflictDetection) {
            // Auto-generated method stub for AbstractDatabaseWriter
            return null;
        }

        @Override
        protected LoadStatus update(CsvData data, boolean applyChangesOnly, boolean useConflictDetection) {
            // Auto-generated method stub for AbstractDatabaseWriter
            return null;
        }

        @Override
        protected boolean create(CsvData data) {
            // Auto-generated method stub for AbstractDatabaseWriter
            return false;
        }

        @Override
        protected boolean sql(CsvData data) {
            // Auto-generated method stub for AbstractDatabaseWriter
            return false;
        }

        @Override
        protected void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails) {
            // Auto-generated method stub for AbstractDatabaseWriter
        }

        @Override
        protected void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails, Object[] values) {
            // TODO Auto-generated method stub
        }
    }
}
