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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class DbCompareReport {
    private List<TableReport> tableReports;
    private final String TABLE_FORMAT = " %-40s%-40s%-13d%-13d%-13d%-13d%-13d%-13d%-13d%n";

    public List<TableReport> getTableReports() {
        return tableReports;
    }

    public void setTableReports(List<TableReport> tableReports) {
        this.tableReports = tableReports;
    }

    public void addTableReport(TableReport tableReport) {
        if (tableReports == null) {
            tableReports = new ArrayList<DbCompareReport.TableReport>();
        }
        tableReports.add(tableReport);
    }

    public void printReportHeader(PrintStream stream) {
        stream.format(
                "+---------------------------------------+---------------------------------------+------------+------------+------------+------------+------------+------------+------------+%n");
        stream.format(
                "+Source                                  Target                                  Source Rows  Target Rows  Matched      Different    Missing      Extra        Errors       %n");
        stream.format(
                "+---------------------------------------+---------------------------------------+------------+------------+------------+------------+------------+------------+------------+%n");
    }

    public void printTableReport(TableReport report, PrintStream stream) {
        String sourceTable = report.getSourceTable();
        String targetTable = report.getTargetTable();
        if (sourceTable.length() > 30) {
            sourceTable = DbCompareUtil.getUnqualifiedTableName(sourceTable);
        }
        if (targetTable.length() > 30) {
            targetTable = DbCompareUtil.getUnqualifiedTableName(targetTable);
        }
        stream.format(TABLE_FORMAT, sourceTable, targetTable, report.getSourceRows(),
                report.getTargetRows(), report.getMatchedRows(), report.getDifferentRows(), report.getMissingRows(), report.getExtraRows(),
                report.getErrorRows());
    }

    public void printReportFooter(PrintStream stream) {
        stream.format(
                "+---------------------------------------+---------------------------------------+------------+------------+------------+------------+------------+------------+------------+%n");
    }

    public static class TableReport {
        private String sourceTable;
        private String targetTable;
        private int sourceRows;
        private int targetRows;
        private int matchedRows;
        private int differentRows;
        private int missingRows;
        private int extraRows;
        private int errorRows;
        private Throwable throwable;

        public void countSourceRow() {
            sourceRows++;
        }

        public void countTargetRow() {
            targetRows++;
        }

        public void countMatchedRow() {
            matchedRows++;
        }

        public void countDifferentRow() {
            differentRows++;
        }

        public void countMissingRow() {
            missingRows++;
        }

        public void countExtraRow() {
            extraRows++;
        }

        public void countErrorRows() {
            errorRows++;
        }

        public String getSourceTable() {
            return sourceTable;
        }

        public void setSourceTable(String sourceTable) {
            this.sourceTable = sourceTable;
        }

        public String getTargetTable() {
            return targetTable;
        }

        public void setTargetTable(String targetTable) {
            this.targetTable = targetTable;
        }

        public int getSourceRows() {
            return sourceRows;
        }

        public void setSourceRows(int sourceRows) {
            this.sourceRows = sourceRows;
        }

        public int getTargetRows() {
            return targetRows;
        }

        public void setTargetRows(int targetRows) {
            this.targetRows = targetRows;
        }

        public int getMatchedRows() {
            return matchedRows;
        }

        public void setMatchedRows(int matchedRows) {
            this.matchedRows = matchedRows;
        }

        public int getDifferentRows() {
            return differentRows;
        }

        public void setDifferentRows(int differentRows) {
            this.differentRows = differentRows;
        }

        public int getMissingRows() {
            return missingRows;
        }

        public void setMissingRows(int missingRows) {
            this.missingRows = missingRows;
        }

        public int getExtraRows() {
            return extraRows;
        }

        public void setExtraRows(int extraRows) {
            this.extraRows = extraRows;
        }

        public int getErrorRows() {
            return errorRows;
        }

        public void setErrorRows(int errorRows) {
            this.errorRows = errorRows;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }

        @Override
        public String toString() {
            return "TableReport [sourceTable=" + sourceTable + ", targetTable=" + targetTable + ", sourceRows=" + sourceRows + ", targetRows="
                    + targetRows + ", matchedRows=" + matchedRows + ", differentRows=" + differentRows + ", missingRows=" + missingRows
                    + ", extraRows=" + extraRows + ", errorRows=" + errorRows + "]";
        }
    }
}
