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
package org.jumpmind.symmetric.model;

import java.io.Serializable;
import java.util.Date;

import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;

public class IncomingError implements Serializable {
    private static final String CUR_DATA = "curData";
    private static final long serialVersionUID = 1L;
    private long batchId;
    private String nodeId;
    private long failedRowNumber;
    private long failedLineNumber;
    private String targetCatalogName;
    private String targetSchemaName;
    private String targetTableName;
    private BinaryEncoding binaryEncoding = BinaryEncoding.HEX;
    private DataEventType eventType;
    private String columnNames;
    private String primaryKeyColumnNames;
    private CsvData csvData = new CsvData();
    private boolean resolveIgnore = false;
    private String conflictId;
    private Date createTime = new Date();
    private Date lastUpdateTime = new Date();
    private String lastUpdateBy = "symmetricds";

    public String[] getParsedRowData() {
        return csvData.getParsedData(CsvData.ROW_DATA);
    }

    public String[] getParsedOldData() {
        return csvData.getParsedData(CsvData.OLD_DATA);
    }

    public String[] getParsedResolveData() {
        return csvData.getParsedData(CsvData.RESOLVE_DATA);
    }

    public void setParsedResolveData(String[] resolveData) {
        csvData.putParsedData(CsvData.RESOLVE_DATA, resolveData);
    }

    public String getRowData() {
        return csvData.getCsvData(CsvData.ROW_DATA);
    }

    public void setRowData(String rowData) {
        csvData.putCsvData(CsvData.ROW_DATA, rowData);
    }

    public String getOldData() {
        return csvData.getCsvData(CsvData.OLD_DATA);
    }

    public void setOldData(String oldData) {
        csvData.putCsvData(CsvData.OLD_DATA, oldData);
    }

    public String getCurData() {
        return csvData.getCsvData(CUR_DATA);
    }

    public void setCurData(String curData) {
        csvData.putCsvData(CUR_DATA, curData);
    }

    public String getResolveData() {
        return csvData.getCsvData(CsvData.RESOLVE_DATA);
    }

    public void setResolveData(String resolveData) {
        csvData.putCsvData(CsvData.RESOLVE_DATA, resolveData);
    }
    /* getters and setters */

    public long getBatchId() {
        return batchId;
    }

    public void setBatchId(long batchId) {
        this.batchId = batchId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public long getFailedRowNumber() {
        return failedRowNumber;
    }

    public void setFailedRowNumber(long failedRowNumber) {
        this.failedRowNumber = failedRowNumber;
    }

    public String getTargetCatalogName() {
        return targetCatalogName;
    }

    public void setTargetCatalogName(String targetCatalogName) {
        this.targetCatalogName = targetCatalogName;
    }

    public String getTargetSchemaName() {
        return targetSchemaName;
    }

    public void setTargetSchemaName(String targetSchemaName) {
        this.targetSchemaName = targetSchemaName;
    }

    public String getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
    }

    public DataEventType getEventType() {
        return eventType;
    }

    public void setEventType(DataEventType eventType) {
        this.eventType = eventType;
    }

    public CsvData getCsvData() {
        return csvData;
    }

    public void setCsvData(CsvData csvData) {
        this.csvData = csvData;
    }

    public boolean isResolveIgnore() {
        return resolveIgnore;
    }

    public void setResolveIgnore(boolean resolveIgnore) {
        this.resolveIgnore = resolveIgnore;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getLastUpdateBy() {
        return lastUpdateBy;
    }

    public void setLastUpdateBy(String lastUpdateBy) {
        this.lastUpdateBy = lastUpdateBy;
    }

    public long getFailedLineNumber() {
        return failedLineNumber;
    }

    public void setFailedLineNumber(long failedLineNumber) {
        this.failedLineNumber = failedLineNumber;
    }

    public String getColumnNames() {
        return columnNames;
    }

    public void setColumnNames(String columnNames) {
        this.columnNames = columnNames;
    }

    public String getPrimaryKeyColumnNames() {
        return primaryKeyColumnNames;
    }

    public void setPrimaryKeyColumnNames(String primaryKeyColumnNames) {
        this.primaryKeyColumnNames = primaryKeyColumnNames;
    }

    public String[] getParsedColumnNames() {
        if (columnNames != null) {
            return columnNames.split(",");
        } else {
            return null;
        }
    }

    public String[] getParsedPrimaryKeyColumnNames() {
        if (primaryKeyColumnNames != null) {
            return primaryKeyColumnNames.split(",");
        } else {
            return null;
        }
    }

    public void setBinaryEncoding(BinaryEncoding binaryEncoding) {
        if (binaryEncoding != null) {
            this.binaryEncoding = binaryEncoding;
        }
    }

    public BinaryEncoding getBinaryEncoding() {
        return binaryEncoding;
    }

    public void setConflictId(String conflictId) {
        this.conflictId = conflictId;
    }

    public String getConflictId() {
        return conflictId;
    }
}
