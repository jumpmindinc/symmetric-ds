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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class KafkaWriterTest {

    @Mock
    private IDatabasePlatform mockPlatform;

    @Mock
    private DatabaseInfo mockDatabaseInfo;

    @Mock
    private ISqlTemplate mockSqlTemplate;

    @Mock
    private ISqlTransaction mockSqlTransaction;

    @Mock
    private KafkaProducer<String, Object> mockKafkaProducer;

    @Mock
    private Future<RecordMetadata> mockFuture;

    private TypedProperties props;
    private Table testTable;
    private Batch testBatch;
    private DataContext testContext;

    @BeforeEach
    public void setUp() {
        props = new TypedProperties();

        // Set up mock platform to return DatabaseInfo and SqlTemplate
        lenient().when(mockPlatform.getDatabaseInfo()).thenReturn(mockDatabaseInfo);
        lenient().when(mockDatabaseInfo.isRequiresSavePointsInTransaction()).thenReturn(false);
        lenient().when(mockPlatform.getSqlTemplate()).thenReturn(mockSqlTemplate);
        lenient().when(mockSqlTemplate.startSqlTransaction()).thenReturn(mockSqlTransaction);

        // Create test table with columns
        testTable = new Table("test_table");
        Column idColumn = new Column("id", true);
        idColumn.setPrimaryKey(true);
        Column nameColumn = new Column("name");
        Column valueColumn = new Column("value");
        testTable.addColumn(idColumn);
        testTable.addColumn(nameColumn);
        testTable.addColumn(valueColumn);

        // Create test batch
        testBatch = new Batch(BatchType.LOAD, 1L, "default", BinaryEncoding.BASE64, "node1", "node2", false);
        testContext = new DataContext(testBatch);

        // Clear the static producer map before each test
        KafkaWriter.producerMap.clear();
    }

    @Test
    public void testConstructorWithNullUrl() {
        assertThrows(RuntimeException.class, () -> {
            new KafkaWriter(
                mockPlatform, mockPlatform, "sym_",
                null, new DatabaseWriterSettings(),
                "producer1", KafkaWriter.KAFKA_FORMAT_JSON,
                KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW,
                null, null, "node1", null, "kafka.", props, "reload"
            );
        });
    }

    @Test
    public void testGetTableName() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);

        assertEquals("TestTable", writer.getTableName("TEST_TABLE"));
        assertEquals("MyTableName", writer.getTableName("MY_TABLE_NAME"));
        assertEquals("Simple", writer.getTableName("SIMPLE"));
    }

    @Test
    public void testWriteKafkaJsonFormat() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        // Set up the context and table without calling open()
        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        // Create test data with old data (required for writeKafka)
        String[] rowData = {"1", "test_name", "test_value"};
        String[] oldData = {"1", "old_name", "old_value"};
        CsvData csvData = new CsvData(DataEventType.INSERT);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        int result = writer.writeKafka(csvData, testTable);

        assertEquals(1, result);
        assertFalse(writer.kafkaDataMap.isEmpty());

        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        assertNotNull(records);
        assertEquals(1, records.size());

        String recordValue = (String) records.get(0).value();
        assertTrue(recordValue.contains("\"test_table\""));
        assertTrue(recordValue.contains("\"eventType\": \"INSERT\""));
        assertTrue(recordValue.contains("\"id\""));
        assertTrue(recordValue.contains("\"name\""));
        assertTrue(recordValue.contains("\"test_name\""));
    }

    @Test
    public void testWriteKafkaCsvFormat() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_CSV,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        String[] rowData = {"1", "test_name", "test_value"};
        String[] oldData = {"1", "old_name", "old_value"};
        CsvData csvData = new CsvData(DataEventType.UPDATE);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        int result = writer.writeKafka(csvData, testTable);

        assertEquals(1, result);

        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        assertNotNull(records);

        String recordValue = (String) records.get(0).value();
        assertTrue(recordValue.contains("\"TABLE\""));
        assertTrue(recordValue.contains("\"test_table\""));
        assertTrue(recordValue.contains("\"EVENT\""));
        assertTrue(recordValue.contains("UPDATE"));
    }

    @Test
    public void testWriteKafkaXmlFormat() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_XML,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        String[] rowData = {"1", "test_name", "test_value"};
        String[] oldData = {"1", "old_name", "old_value"};
        CsvData csvData = new CsvData(DataEventType.INSERT);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        int result = writer.writeKafka(csvData, testTable);

        assertEquals(1, result);

        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        assertNotNull(records);

        String recordValue = (String) records.get(0).value();
        assertTrue(recordValue.contains("<row entity=\"test_table\""));
        assertTrue(recordValue.contains("dml=\"INSERT\""));
        assertTrue(recordValue.contains("<data key=\"id\">1</data>"));
        assertTrue(recordValue.contains("<data key=\"name\">test_name</data>"));
    }

    @Test
    public void testWriteKafkaDeleteEvent() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        String[] oldData = {"1", "test_name", "test_value"};
        CsvData csvData = new CsvData(DataEventType.DELETE);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        int result = writer.writeKafka(csvData, testTable);

        assertEquals(1, result);

        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        assertNotNull(records);

        String recordValue = (String) records.get(0).value();
        assertTrue(recordValue.contains("\"eventType\": \"DELETE\""));
    }

    @Test
    public void testTopicByChannel() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_CHANNEL, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        String[] rowData = {"1", "test_name", "test_value"};
        String[] oldData = {"1", "old_name", "old_value"};
        CsvData csvData = new CsvData(DataEventType.INSERT);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        writer.writeKafka(csvData, testTable);

        // Topic should be the channel id "default" instead of table name
        assertTrue(writer.kafkaDataMap.containsKey("default"));
        assertFalse(writer.kafkaDataMap.containsKey("test_table"));
    }

    @Test
    public void testMessageByBatch() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_BATCH);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        // Insert multiple rows
        String[] rowData1 = {"1", "name1", "value1"};
        String[] oldData1 = {"1", "old1", "old1"};
        CsvData csvData1 = new CsvData(DataEventType.INSERT);
        csvData1.putParsedData(CsvData.ROW_DATA, rowData1);
        csvData1.putParsedData(CsvData.OLD_DATA, oldData1);

        String[] rowData2 = {"2", "name2", "value2"};
        String[] oldData2 = {"2", "old2", "old2"};
        CsvData csvData2 = new CsvData(DataEventType.INSERT);
        csvData2.putParsedData(CsvData.ROW_DATA, rowData2);
        csvData2.putParsedData(CsvData.OLD_DATA, oldData2);

        writer.writeKafka(csvData1, testTable);
        writer.writeKafka(csvData2, testTable);

        // Both records should be in the same list (batched)
        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        assertNotNull(records);
        assertEquals(2, records.size());
    }

    @Test
    public void testBatchComplete() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;
        when(mockKafkaProducer.send(any())).thenReturn(mockFuture);

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        String[] rowData = {"1", "test_name", "test_value"};
        String[] oldData = {"1", "old_name", "old_value"};
        CsvData csvData = new CsvData(DataEventType.INSERT);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        writer.writeKafka(csvData, testTable);

        // Verify data is queued
        assertFalse(writer.kafkaDataMap.isEmpty());

        // Complete the batch - this should send messages
        writer.batchComplete(testContext);

        // Verify send was called
        verify(mockKafkaProducer, atLeastOnce()).send(any(ProducerRecord.class));

        // Verify the map is cleared after batch complete
        assertTrue(writer.kafkaDataMap.isEmpty());
    }

    @Test
    public void testBatchCompleteWithBatchMessaging() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_BATCH);
        writer.kafkaProducer = mockKafkaProducer;
        when(mockKafkaProducer.send(any())).thenReturn(mockFuture);

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        // Add multiple records
        for (int i = 0; i < 3; i++) {
            String[] rowData = {String.valueOf(i), "name" + i, "value" + i};
            String[] oldData = {String.valueOf(i), "old" + i, "old" + i};
            CsvData csvData = new CsvData(DataEventType.INSERT);
            csvData.putParsedData(CsvData.ROW_DATA, rowData);
            csvData.putParsedData(CsvData.OLD_DATA, oldData);
            writer.writeKafka(csvData, testTable);
        }

        writer.batchComplete(testContext);

        // With BATCH messaging, all records should be combined into one message
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(mockKafkaProducer).send(captor.capture());

        ProducerRecord<String, Object> sentRecord = captor.getValue();
        String value = (String) sentRecord.value();

        // Should contain all three records' data
        assertTrue(value.contains("name0"));
        assertTrue(value.contains("name1"));
        assertTrue(value.contains("name2"));
    }

    @Test
    public void testXmlEscaping() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_XML,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        // Test with special XML characters
        String[] rowData = {"1", "<script>alert('xss')</script>", "value&with\"quotes"};
        String[] oldData = {"1", "old", "old"};
        CsvData csvData = new CsvData(DataEventType.INSERT);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        writer.writeKafka(csvData, testTable);

        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        String recordValue = (String) records.get(0).value();

        // Verify XML special characters are escaped
        assertTrue(recordValue.contains("&lt;script&gt;"));
        assertTrue(recordValue.contains("&amp;"));
    }

    @Test
    public void testJsonEscaping() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        // Test with special JSON characters
        String[] rowData = {"1", "value with \"quotes\" and \\backslash", "tab\there"};
        String[] oldData = {"1", "old", "old"};
        CsvData csvData = new CsvData(DataEventType.INSERT);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        writer.writeKafka(csvData, testTable);

        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        String recordValue = (String) records.get(0).value();

        // Gson should properly escape the JSON
        assertTrue(recordValue.contains("\\\"quotes\\\""));
        assertTrue(recordValue.contains("\\\\backslash"));
    }

    @Test
    public void testCsvEscaping() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_CSV,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        // Test with quotes that need escaping in CSV
        String[] rowData = {"1", "value with \"quotes\"", "normal"};
        String[] oldData = {"1", "old", "old"};
        CsvData csvData = new CsvData(DataEventType.INSERT);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        writer.writeKafka(csvData, testTable);

        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        String recordValue = (String) records.get(0).value();

        // CSV escapes quotes by doubling them
        assertTrue(recordValue.contains("\"\"quotes\"\""));
    }

    @Test
    public void testNullValues() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        // Test with null value
        String[] rowData = {"1", null, "test_value"};
        String[] oldData = {"1", "old", "old"};
        CsvData csvData = new CsvData(DataEventType.INSERT);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        writer.writeKafka(csvData, testTable);

        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        assertNotNull(records);
        assertEquals(1, records.size());

        String recordValue = (String) records.get(0).value();
        assertTrue(recordValue.contains("\"name\": null"));
    }

    @Test
    public void testGetColumnName() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);

        // Create a simple test bean
        TestBean bean = new TestBean();

        String columnName = writer.getColumnName("test_table", "myField", bean);
        assertEquals("myField", columnName);

        // Test with underscore in column name - should match myfield property
        columnName = writer.getColumnName("test_table", "MY_FIELD", bean);
        assertEquals("myField", columnName);
    }

    @Test
    public void testDatumToByteArray() throws Exception {
        org.apache.avro.Schema.Parser parser = new org.apache.avro.Schema.Parser();
        org.apache.avro.Schema schema = parser.parse(KafkaWriter.AVRO_CDC_SCHEMA);

        org.apache.avro.generic.GenericData.Record record =
            new org.apache.avro.generic.GenericData.Record(schema);
        record.put("table", "test_table");
        record.put("eventType", "INSERT");
        record.put("data", new ArrayList<>());

        byte[] result = KafkaWriter.datumToByteArray(schema, record);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    public void testInternalChannelNotPublished() {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_JSON,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        // Create a batch with internal channel "config"
        Batch internalBatch = new Batch(BatchType.LOAD, 1L, "config", BinaryEncoding.BASE64, "node1", "node2", false);
        DataContext internalContext = new DataContext(internalBatch);

        writer.context = internalContext;
        writer.batch = internalBatch;

        // This should not attempt to send to Kafka (internal channel)
        writer.batchComplete(internalContext);

        // Verify send was never called for internal channel
        verify(mockKafkaProducer, never()).send(any());
    }

    @Test
    public void testWriteKafkaAvroFormatWithoutConfluent() throws Exception {
        KafkaWriter writer = createKafkaWriter(KafkaWriter.KAFKA_FORMAT_AVRO,
            KafkaWriter.KAFKA_TOPIC_BY_TABLE, KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        writer.kafkaProducer = mockKafkaProducer;

        writer.context = testContext;
        writer.batch = testBatch;
        writer.sourceTable = testTable;
        writer.targetTable = testTable;

        String[] rowData = {"1", "test_name", "test_value"};
        String[] oldData = {"1", "old_name", "old_value"};
        CsvData csvData = new CsvData(DataEventType.INSERT);
        csvData.putParsedData(CsvData.ROW_DATA, rowData);
        csvData.putParsedData(CsvData.OLD_DATA, oldData);

        int result = writer.writeKafka(csvData, testTable);

        assertEquals(1, result);

        List<ProducerRecord<String, Object>> records = writer.kafkaDataMap.get("test_table");
        assertNotNull(records);
        assertEquals(1, records.size());

        // The value should be a byte array for AVRO without Confluent
        Object recordValue = records.get(0).value();
        assertTrue(recordValue instanceof byte[]);
        assertTrue(((byte[]) recordValue).length > 0);
    }

    @Test
    public void testKafkaConstants() {
        assertEquals("JSON", KafkaWriter.KAFKA_FORMAT_JSON);
        assertEquals("XML", KafkaWriter.KAFKA_FORMAT_XML);
        assertEquals("CSV", KafkaWriter.KAFKA_FORMAT_CSV);
        assertEquals("AVRO", KafkaWriter.KAFKA_FORMAT_AVRO);
        assertEquals("BATCH", KafkaWriter.KAFKA_MESSAGE_BY_BATCH);
        assertEquals("ROW", KafkaWriter.KAFKA_MESSAGE_BY_ROW);
        assertEquals("TABLE", KafkaWriter.KAFKA_TOPIC_BY_TABLE);
        assertEquals("CHANNEL", KafkaWriter.KAFKA_TOPIC_BY_CHANNEL);
    }

    private KafkaWriter createKafkaWriter(String outputFormat, String topicBy, String messageBy) {
        KafkaWriter writer = new KafkaWriter(
            mockPlatform, mockPlatform, "sym_",
            null, new DatabaseWriterSettings(),
            "test-producer", outputFormat,
            topicBy, messageBy,
            null, null, "test-node", "localhost:9092", "kafka.", props, "reload"
        );
        return writer;
    }

    // Simple test bean for getColumnName test
    public static class TestBean {
        private String myField;

        public String getMyField() {
            return myField;
        }

        public void setMyField(String myField) {
            this.myField = myField;
        }
    }
}
