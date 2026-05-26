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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DdlBuilderFactory;
import org.jumpmind.db.platform.DmlStatementFactory;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.DmlStatementOptions;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.util.Statistics;

public class StructureDataWriter implements IDataWriter {
    protected IDatabasePlatform platform;
    protected Table currentTable;
    protected Map<Batch, Statistics> statistics = new HashMap<Batch, Statistics>();

    public enum PayloadType {
        CSV, SQL
    };

    /*
     * map is comprised of a batch and a list of payload data that goes with the batch
     */
    protected Map<Long, List<String>> payloadMap = new HashMap<Long, List<String>>();
    protected PayloadType payloadType = PayloadType.SQL;
    protected long currentBatch;
    protected String targetDatabaseName;
    protected boolean useQuotedIdentifiers;
    protected boolean useJdbcTimestampFormat;
    protected boolean useUpsertStatements;
    protected BinaryEncoding binaryEncoding;

    public StructureDataWriter(IDatabasePlatform platform, String targetDatabaseName,
            PayloadType payloatType, boolean useQuotedIdentifiers, BinaryEncoding binaryEncoding,
            boolean useJdbcTimestampFormat, boolean useUpsertStatements) {
        this.platform = platform;
        this.payloadType = payloatType;
        this.targetDatabaseName = targetDatabaseName;
        this.useQuotedIdentifiers = useQuotedIdentifiers;
        this.binaryEncoding = binaryEncoding;
        this.useJdbcTimestampFormat = useJdbcTimestampFormat;
        this.useUpsertStatements = useUpsertStatements;
    }

    public void open(DataContext context) {
    }

    public void close() {
    }

    public Map<Batch, Statistics> getStatistics() {
        return statistics;
    }

    public void start(Batch batch) {
        List<String> payloadData = new ArrayList<String>();
        this.currentBatch = batch.getBatchId();
        this.statistics.put(batch, new Statistics());
        this.payloadMap.put(currentBatch, payloadData);
    }

    public boolean start(Table table) {
        /*
         * in the case when the target schema or catalog is set then we need to use the previous schema or catalog to look up the table locally.
         */
        this.currentTable = (Table) platform.getRelationFromCache(table.getOldCatalog(), table.getOldSchema(),
                table.getName(), false);
        if (this.currentTable != null) {
            this.currentTable = currentTable.copyAndFilterColumns(table.getColumnNames(),
                    table.getPrimaryKeyColumnNames(), true, false);
            /*
             * restore the schema and catalog from the passed in table because they might have not been originally set, but were set when looking up the table
             * locally
             */
            this.currentTable.setSchema(table.getSchema());
            this.currentTable.setCatalog(table.getCatalog());
            this.currentTable.setName(table.getName());
            return true;
        } else {
            return false;
        }
    }

    public void write(CsvData data) {
        String sql = null;
        switch (data.getDataEventType()) {
            case UPDATE:
                sql = buildSql(useUpsertStatements ? DmlType.UPSERT : DmlType.UPDATE, data.getParsedData(CsvData.ROW_DATA),
                        currentTable.getColumns());
                break;
            case INSERT:
                sql = buildSql(useUpsertStatements ? DmlType.UPSERT : DmlType.INSERT, data.getParsedData(CsvData.ROW_DATA),
                        currentTable.getColumns());
                break;
            case DELETE:
                sql = buildSql(DmlType.DELETE, data.getParsedData(CsvData.PK_DATA),
                        currentTable.getPrimaryKeyColumns());
                break;
            case SQL:
                sql = data.getParsedData(CsvData.ROW_DATA)[0];
                break;
            case CREATE:
                IDdlBuilder builder = DdlBuilderFactory.getInstance().create(targetDatabaseName);
                sql = builder.createTable(currentTable);
                break;
            default:
                break;
        }
        if (sql != null) {
            this.payloadMap.get(this.currentBatch).add(sql);
        }
    }

    protected String buildSql(DmlType dmlType, String[] values, Column[] columns) {
        // TODO we should try to reuse statements
        // TODO support primary key updates
        DmlStatementOptions options = new DmlStatementOptions(dmlType, currentTable).quotedIdentifiers(useQuotedIdentifiers);
        DmlStatement statement = DmlStatementFactory.getInstance().create(targetDatabaseName, options);
        Object[] objects = platform.getObjectValues(binaryEncoding, values, columns, false, false);
        Row row = new Row(columns.length);
        for (int i = 0; i < columns.length; i++) {
            row.put(columns[i].getName(), objects[i]);
        }
        return statement.buildDynamicSql(binaryEncoding, row, false, useJdbcTimestampFormat);
    }

    public void end(Table table) {
    }

    public void end(Batch batch, boolean inError) {
    }

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public Map<Long, List<String>> getPayloadMap() {
        return payloadMap;
    }

    public void setPayloadMap(Map<Long, List<String>> payloadMap) {
        this.payloadMap = payloadMap;
    }
}
