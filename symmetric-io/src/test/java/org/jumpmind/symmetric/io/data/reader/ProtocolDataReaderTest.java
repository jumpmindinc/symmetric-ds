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
package org.jumpmind.symmetric.io.data.reader;

import static org.junit.Assert.*;

import org.jumpmind.db.model.Relation;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvConstants;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.junit.jupiter.api.Test;

public class ProtocolDataReaderTest {
    @Test
    public void testSimpleRead() {
        String nodeId = "055";
        long batchId = 123;
        String channelId = "nbc";
        StringBuilder builder = beginCsv(nodeId);
        beginBatch(builder, batchId, channelId);
        putTableN(builder, 1, true);
        putInsert(builder, 4);
        endCsv(builder);
        ProtocolDataReader reader = new ProtocolDataReader(BatchType.LOAD, "test", builder);
        DataContext ctx = new DataContext(reader);
        reader.open(ctx);
        Batch batch = reader.nextBatch();
        assertNotNull(batch);
        assertEquals(batchId, batch.getBatchId());
        Relation relation = reader.nextRelation();
        assertNotNull(relation);
        assertEquals("test1", relation.getName());
        assertEquals(2, relation.getColumnCount());
        assertEquals(1, relation.getPrimaryKeyColumns().length);
        assertEquals("id", relation.getColumn(0).getName());
        assertEquals("text", relation.getColumn(1).getName());
        CsvData data = reader.nextData();
        assertNotNull(data);
        assertEquals(DataEventType.INSERT, data.getDataEventType());
        assertEquals("0", data.getParsedData(CsvData.ROW_DATA)[0]);
        assertEquals("test", data.getParsedData(CsvData.ROW_DATA)[1]);
        data = reader.nextData();
        assertNotNull(data);
        assertEquals(DataEventType.INSERT, data.getDataEventType());
        assertEquals("1", data.getParsedData(CsvData.ROW_DATA)[0]);
        assertEquals("test", data.getParsedData(CsvData.ROW_DATA)[1]);
        data = reader.nextData();
        assertNotNull(data);
        assertEquals(DataEventType.INSERT, data.getDataEventType());
        assertEquals("2", data.getParsedData(CsvData.ROW_DATA)[0]);
        assertEquals("test", data.getParsedData(CsvData.ROW_DATA)[1]);
        data = reader.nextData();
        assertNotNull(data);
        assertEquals(DataEventType.INSERT, data.getDataEventType());
        assertEquals("3", data.getParsedData(CsvData.ROW_DATA)[0]);
        assertEquals("test", data.getParsedData(CsvData.ROW_DATA)[1]);
        data = reader.nextData();
        assertNull(data);
        relation = reader.nextRelation();
        assertNull(relation);
        batch = reader.nextBatch();
        assertNull(batch);
        reader.close();
    }

    @Test
    public void testTableContextSwitch() {
        String nodeId = "1";
        long batchId = 1;
        String channelId = "test";
        StringBuilder builder = beginCsv(nodeId);
        beginBatch(builder, batchId, channelId);
        putTableN(builder, 1, true);
        putInsert(builder, 4);
        putTableN(builder, 2, true);
        putInsert(builder, 4);
        putTableN(builder, 1, false);
        putInsert(builder, 2);
        putTableN(builder, 2, false);
        putInsert(builder, 2);
        endCsv(builder);
        ProtocolDataReader reader = new ProtocolDataReader(BatchType.LOAD, "test", builder);
        DataContext ctx = new DataContext(reader);
        reader.open(ctx);
        Batch batch = reader.nextBatch();
        assertNotNull(batch);
        Relation relation = reader.nextRelation();
        assertNotNull(relation);
        assertEquals(2, relation.getColumnCount());
        assertEquals(1, relation.getPrimaryKeyColumnCount());
        assertEquals("test1", relation.getName());
        int dataCount = 0;
        while (reader.nextData() != null) {
            dataCount++;
        }
        assertEquals(4, dataCount);
        relation = reader.nextRelation();
        assertNotNull(relation);
        assertEquals(2, relation.getColumnCount());
        assertEquals(1, relation.getPrimaryKeyColumnCount());
        assertEquals("test2", relation.getName());
        dataCount = 0;
        while (reader.nextData() != null) {
            dataCount++;
        }
        assertEquals(4, dataCount);
        relation = reader.nextRelation();
        assertNotNull(relation);
        assertEquals(2, relation.getColumnCount());
        assertEquals(1, relation.getPrimaryKeyColumnCount());
        assertEquals("test1", relation.getName());
        dataCount = 0;
        while (reader.nextData() != null) {
            dataCount++;
        }
        assertEquals(2, dataCount);
        relation = reader.nextRelation();
        assertNotNull(relation);
        assertEquals(2, relation.getColumnCount());
        assertEquals(1, relation.getPrimaryKeyColumnCount());
        assertEquals("test2", relation.getName());
        dataCount = 0;
        while (reader.nextData() != null) {
            dataCount++;
        }
        assertEquals(2, dataCount);
    }

    protected StringBuilder beginCsv(String nodeId) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("%s,%s\n", CsvConstants.NODEID, nodeId));
        builder.append(String.format("%s,%s\n", CsvConstants.BINARY, BinaryEncoding.BASE64.toString()));
        return builder;
    }

    protected StringBuilder beginBatch(StringBuilder builder, long batchId, String channelId) {
        builder.append(String.format("%s,%s\n", CsvConstants.CHANNEL, channelId));
        builder.append(String.format("%s,%d\n", CsvConstants.BATCH, batchId));
        return builder;
    }

    protected StringBuilder putTableN(StringBuilder builder, int n, boolean includeMetadata) {
        builder.append(String.format("%s,%s\n", CsvConstants.SCHEMA, ""));
        builder.append(String.format("%s,%s\n", CsvConstants.CATALOG, ""));
        builder.append(String.format("%s,%s%d\n", CsvConstants.TABLE, "test", n));
        if (includeMetadata) {
            builder.append(String.format("%s,%s\n", CsvConstants.KEYS, "id"));
            builder.append(String.format("%s,%s,%s\n", CsvConstants.COLUMNS, "id", "text"));
        }
        return builder;
    }

    protected StringBuilder putInsert(StringBuilder builder, int count) {
        for (int i = 0; i < count; i++) {
            builder.append(String.format("%s,%d,%s\n", CsvConstants.INSERT, i, "\"test\""));
        }
        return builder;
    }

    protected StringBuilder endCsv(StringBuilder builder) {
        builder.append(String.format("%s\n", CsvConstants.COMMIT));
        return builder;
    }
}
