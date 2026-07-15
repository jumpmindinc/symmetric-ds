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

import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.io.ChannelUtils;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.SystemPropertyUtils;

import com.google.gson.Gson;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;

public class KafkaWriter extends DynamicDefaultDatabaseWriter {
    private static final Logger log = LoggerFactory.getLogger(KafkaWriter.class);
    private static final String TRUNCATE_PATTERN = "^(truncate)( table)?.*";
    private static final String DELETE_PATTERN = "^(delete from).*";
    protected final String KAFKA_TEXT_CACHE = "KAFKA_TEXT_CACHE" + this.hashCode();
    protected Map<String, List<ProducerRecord<String, Object>>> kafkaDataMap = new HashMap<String, List<ProducerRecord<String, Object>>>();
    protected String kafkaDataKey;
    private String url;
    private String producer;
    private String externalNodeID;
    private String outputFormat;
    private String topicBy;
    private String messageBy;
    private String confluentUrl;
    private String schemaPackage;
    private TypedProperties props;
    private String channelReload;
    private String[] parseDatePatterns = new String[] { "yyyy/MM/dd HH:mm:ss.SSSSSS", "yyyy-MM-dd HH:mm:ss", "ddMMMyyyy:HH:mm:ss.SSS Z",
            "ddMMMyyyy:HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss.SSS", "ddMMMyyyy:HH:mm:ss.SSSSSS", "yyyy-MM-dd", "yyyy-MM-dd'T'HH:mmZZZZ",
            "yyyy-MM-dd'T'HH:mm:ssZZZZ", "yyyy-MM-dd'T'HH:mm:ss.SSSZZZZ" };
    private List<String> schemaPackageClassNames = new ArrayList<String>();
    public final static String KAFKA_FORMAT_XML = "XML";
    public final static String KAFKA_FORMAT_JSON = "JSON";
    public final static String KAFKA_FORMAT_AVRO = "AVRO";
    public final static String KAFKA_FORMAT_CSV = "CSV";
    public final static String KAFKA_MESSAGE_BY_BATCH = "BATCH";
    public final static String KAFKA_MESSAGE_BY_ROW = "ROW";
    public final static String KAFKA_TOPIC_BY_TABLE = "TABLE";
    public final static String KAFKA_TOPIC_BY_CHANNEL = "CHANNEL";
    public final static String AVRO_CDC_SCHEMA = "{" + "\"type\":\"record\"," + "\"name\":\"cdc\"," + "\"fields\":["
            + "  { \"name\":\"table\", \"type\":\"string\" }," + "  { \"name\":\"eventType\", \"type\":\"string\" },"
            + "  { \"name\":\"data\", \"type\":{" + "     \"type\":\"array\", \"items\":{" + "         \"name\":\"column\","
            + "         \"type\":\"record\"," + "         \"fields\":[" + "            {\"name\":\"name\", \"type\":\"string\"},"
            + "            {\"name\":\"value\", \"type\":[\"null\", \"string\"]} ] }}}]}";
    public final static String KAFKA_SECURITY_PROTOCOL_PLAINTEXT = "PLAINTEXT";
    public final static String KAFKA_SECURITY_PROTOCOL_SASL_PLAINTEXT = "SASL_PLAINTEXT";
    public final static String KAFKA_SECURITY_PROTOCOL_SASL_SSL = "SASL_SSL";
    public final static String KAFKA_SECURITY_PROTOCOL_SSL = "SSL";
    Schema.Parser parser = new Schema.Parser();
    Schema schema = null;
    Map<String, Object> configs = new HashMap<String, Object>();
    Map<String, Class<?>> tableClassCache = new HashMap<String, Class<?>>();
    Map<String, String> kafkaTableNamesInBatchMap = new HashMap<String, String>();
    Map<String, Map<String, String>> tableColumnCache = new HashMap<String, Map<String, String>>();
    public KafkaProducer<String, Object> kafkaProducer;
    protected static Map<String, KafkaProducer<String, Object>> producerMap = new HashMap<String, KafkaProducer<String, Object>>();

    public KafkaWriter(IDatabasePlatform symmetricPlatform, IDatabasePlatform targetPlatform, String runtimeConfigTablePrefix,
            IDatabaseWriterConflictResolver conflictResolver, DatabaseWriterSettings writerSettings, String kafkaProducerId, String outputFormat,
            String topicBy, String messageBy, String confluentUrl, String schemaPackage, String externalNodeID, String kafkaUrl,
            String loadOnlyPrefix, TypedProperties props, String channelReload) {
        super(symmetricPlatform, targetPlatform, runtimeConfigTablePrefix, conflictResolver, writerSettings);
        schema = parser.parse(AVRO_CDC_SCHEMA);
        this.url = kafkaUrl;
        this.producer = kafkaProducerId;
        this.outputFormat = outputFormat;
        this.topicBy = topicBy;
        this.messageBy = messageBy;
        this.confluentUrl = confluentUrl;
        this.schemaPackage = schemaPackage;
        this.externalNodeID = externalNodeID;
        this.props = props;
        this.channelReload = channelReload;
        if (this.url == null) {
            throw new RuntimeException(
                    "Kakfa not configured properly! Ensure the following startup parameter points to Kafka server (and restart): " + loadOnlyPrefix
                            + "db.url");
        }
        String clientID = this.producer + "-" + this.externalNodeID;
        if (producerMap.get(clientID) != null) {
            kafkaProducer = producerMap.get(clientID);
        } else {
            configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.url);
            configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            if (confluentUrl != null) {
                configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
                configs.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, confluentUrl);
                configs.put(ProducerConfig.CLIENT_ID_CONFIG, clientID);
            } else {
                if (outputFormat.equals(KAFKA_FORMAT_AVRO)) {
                    configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
                } else {
                    configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                }
                configs.put(ProducerConfig.CLIENT_ID_CONFIG, this.producer);
            }
            for (Object key : this.props.keySet()) {
                if (key.toString().startsWith("kafkaclient.")) {
                    configs.put(key.toString().substring(12), this.props.get(key));
                }
            }
            kafkaProducer = new KafkaProducer<String, Object>(configs);
            producerMap.put(clientID, kafkaProducer);
            if (log.isDebugEnabled()) {
                log.debug("Initialized Kafka producer. Config: {}", configs);
            } else {
                log.info("Initialized Kafka producer. Format={}, topicBy={}, messageBy={}, URL={}", outputFormat, topicBy, messageBy, kafkaUrl);
            }
        }
    }

    @Override
    protected void prepare() {
        if (isSymmetricTable(this.targetTable != null ? this.targetTable.getName() : "")) {
            super.prepare();
        }
    }

    @Override
    protected void prepare(String sql, CsvData data) {
        // This is used to prevent the sql from triggering and causing an error
        // in DefaultDatabaseWriter.
    }

    @Override
    public int prepareAndExecute(String sql, CsvData data) {
        return 1;
    }

    @Override
    protected int execute(CsvData data, String[] values) {
        if (isSymmetricTable(targetTable)) {
            return super.execute(data, values);
        }
        Table table = this.targetTable;
        String[] rowData = data.getParsedData(CsvData.ROW_DATA);
        if (data.getDataEventType() == DataEventType.DELETE) {
            rowData = data.getParsedData(CsvData.OLD_DATA);
        }
        StringBuilder kafkaText = new StringBuilder();
        String kafkaKey = null;
        if (messageBy.equals(KAFKA_MESSAGE_BY_ROW)) {
            StringBuilder sb = new StringBuilder();
            sb.append(table.getName()).append(":");
            for (int i = 0; i < table.getPrimaryKeyColumnNames().length; i++) {
                sb.append(":").append(rowData[i]);
            }
            kafkaKey = String.valueOf(sb.toString().hashCode());
        } else if (messageBy.equals(KAFKA_MESSAGE_BY_BATCH)) {
            String s = context.getBatch().getSourceNodeId() + "-" + context.getBatch().getBatchId();
            kafkaKey = String.valueOf(s.hashCode());
        }
        if (topicBy.equals(KAFKA_TOPIC_BY_CHANNEL)) {
            kafkaDataKey = context.getBatch().getChannelId();
        } else {
            kafkaDataKey = table.getNameLowerCase();
        }
        log.debug("Processing table {} for Kafka on topic {}", table.getName(), kafkaDataKey);
        if (kafkaDataMap.get(kafkaDataKey) == null) {
            kafkaDataMap.put(kafkaDataKey, new ArrayList<ProducerRecord<String, Object>>());
        }
        List<ProducerRecord<String, Object>> kafkaDataList = kafkaDataMap.get(kafkaDataKey);
        if (outputFormat.equals(KAFKA_FORMAT_JSON)) {
            if (!kafkaDataList.isEmpty() && !messageBy.equals(KAFKA_MESSAGE_BY_ROW)) {
                kafkaText.append(", ");
            }
            kafkaText.append("{\"").append(table.getName()).append("\": {").append("\"eventType\": \"" + data.getDataEventType() + "\",")
                    .append("\"data\": { ");
            // Let Gson escape the json values
            Gson gson = new Gson();
            for (int i = 0; i < table.getColumnNames().length; i++) {
                kafkaText.append("\"").append(table.getColumnNames()[i]).append("\": ");
                kafkaText.append(gson.toJson(rowData[i]));
                if (i + 1 < table.getColumnNames().length) {
                    kafkaText.append(",");
                }
            }
            kafkaText.append(" } } }");
        } else if (outputFormat.equals(KAFKA_FORMAT_CSV)) {
            // Quote every non-null field, escape quote character by
            // doubling the quote character
            kafkaText.append("\n\"TABLE\"").append(",\"").append(table.getName()).append("\",\"").append("EVENT").append("\",\"")
                    .append(data.getDataEventType()).append("\",");
            for (int i = 0; i < table.getColumnNames().length; i++) {
                kafkaText.append("\"").append(Strings.CS.replace(table.getColumnNames()[i], "\"", "\"\"")).append("\",");
                if (rowData[i] != null) {
                    kafkaText.append("\"").append(Strings.CS.replace(rowData[i], "\"", "\"\"")).append("\"");
                }
                if (i + 1 < table.getColumnNames().length) {
                    kafkaText.append(",");
                }
            }
        } else if (outputFormat.equals(KAFKA_FORMAT_XML)) {
            kafkaText.append("<row entity=\"").append(StringEscapeUtils.escapeXml11(table.getName())).append("\"").append(" dml=\"")
                    .append(data.getDataEventType()).append("\">");
            for (int i = 0; i < table.getColumnNames().length; i++) {
                kafkaText.append("<data key=\"").append(StringEscapeUtils.escapeXml11(table.getColumnNames()[i])).append("\">")
                        .append(StringEscapeUtils.escapeXml11(rowData[i])).append("</data>");
            }
            kafkaText.append("</row>");
        } else if (outputFormat.equals(KAFKA_FORMAT_AVRO)) {
            String tableName = getTableName(table.getName());
            if (confluentUrl != null) {
                try {
                    Class<?> curClass = getClassByTableName(tableName);
                    if (curClass != null) {
                        Constructor<?> defaultConstructor = curClass.getConstructor();
                        Object pojo = defaultConstructor.newInstance();
                        Set<String> allowedProperties = new HashSet<>();
                        for (PropertyDescriptor pd : PropertyUtils.getPropertyDescriptors(pojo)) {
                            if (pd.getWriteMethod() != null) {
                                allowedProperties.add(pd.getName());
                            }
                        }
                        for (int i = 0; i < table.getColumnNames().length; i++) {
                            String colName = getColumnName(table.getName(), table.getColumnNames()[i], pojo);
                            if (colName != null) {
                                Class<?> propertyTypeClass = PropertyUtils.getPropertyType(pojo, colName);
                                if (CharSequence.class.equals(propertyTypeClass)) {
                                    PropertyUtils.setSimpleProperty(pojo, colName, rowData[i]);
                                } else if (Long.class.equals(propertyTypeClass)) {
                                    Date date = null;
                                    try {
                                        date = DateUtils.parseDate(rowData[i], parseDatePatterns);
                                    } catch (Exception e) {
                                        log.debug(rowData[i] + " was not a recognized date format so treating it as a long.");
                                    }
                                    if (allowedProperties.contains(colName)) {
                                        BeanUtils.setProperty(pojo, colName, date != null ? date.getTime() : rowData[i]);
                                    }
                                } else {
                                    if (allowedProperties.contains(colName)) {
                                        BeanUtils.setProperty(pojo, colName, rowData[i]);
                                    }
                                }
                            }
                        }
                        sendKafkaMessage(new ProducerRecord<String, Object>(kafkaDataKey, kafkaKey, pojo));
                    } else {
                        throw new RuntimeException(
                                "Unable to find a POJO to load for AVRO based message onto Kafka for table : " + tableName);
                    }
                } catch (NoSuchMethodException e) {
                    log.info("Unable to find setter on POJO based on table " + tableName, e);
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    log.info("Unable to invoke a default constructor on POJO based on table " + tableName, e);
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    log.info("Unable to access a default constructor on POJO based on table " + tableName, e);
                    throw new RuntimeException(e);
                } catch (InstantiationException e) {
                    log.info("Unable to instantiate a constructor on POJO based on table " + tableName, e);
                    throw new RuntimeException(e);
                }
            } else {
                GenericData.Record avroRecord = new GenericData.Record(schema);
                log.debug("Preparing Avro record for table {} eventType {}", tableName, data.getDataEventType().toString());
                avroRecord.put("table", table.getName());
                avroRecord.put("eventType", data.getDataEventType().toString());
                Collection<GenericRecord> dataCollection = new ArrayList<GenericRecord>();
                for (int i = 0; i < table.getColumnNames().length; i++) {
                    GenericRecord columnRecord = new GenericData.Record(schema.getField("data").schema().getElementType());
                    columnRecord.put("name", table.getColumnNames()[i]);
                    columnRecord.put("value", rowData[i]);
                    dataCollection.add(columnRecord);
                    if (log.isDebugEnabled()) {
                        log.debug("Appending to Avro record for table {} column {}={} eventType {}",
                                tableName, table.getColumnNames()[i], rowData[i], data.getDataEventType().toString());
                    }
                }
                avroRecord.put("data", dataCollection);
                try {
                    kafkaDataList.add(new ProducerRecord<String, Object>(kafkaDataKey, kafkaKey, datumToByteArray(schema, avroRecord)));
                    return 1;
                } catch (IOException ioe) {
                    throw new RuntimeException("Unable to convert row data to an Avro record for table " + tableName, ioe);
                }
            }
        }
        kafkaDataList.add(new ProducerRecord<String, Object>(kafkaDataKey, kafkaKey, kafkaText.toString()));
        return 1;
    }

    @Override
    protected LoadStatus delete(CsvData data, boolean useConflictDetection) {
        statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
        Table table = this.targetTable;
        int successValue = 0;
        if (isSymmetricTable(table)) {
            return super.delete(data, useConflictDetection);
        } else {
            successValue = writeKafka(data, table);
        }
        statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
        if (successValue == 1) {
            return LoadStatus.SUCCESS;
        } else {
            return LoadStatus.CONFLICT;
        }
    }

    @Override
    protected LoadStatus update(CsvData data, boolean applyChangesOnly, boolean useConflictDetection) {
        statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
        Table table = this.targetTable;
        int successValue = 0;
        if (isSymmetricTable(table)) {
            return super.update(data, applyChangesOnly, useConflictDetection);
        } else {
            successValue = writeKafka(data, table);
        }
        statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
        if (successValue == 1) {
            return LoadStatus.SUCCESS;
        } else {
            return LoadStatus.CONFLICT;
        }
    }

    @Override
    protected boolean create(CsvData data) {
        String xml = null;
        try {
            // Placeholder to ensure target platform and transaction is
            // returned.
            // SYM_* tables are not created through this process.
            String tempNonSymTable = "NON_SYM_TABLE";
            getTransaction(tempNonSymTable).commit();
            statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
            xml = data.getParsedData(CsvData.ROW_DATA)[0];
            log.info("Creating Kafka Topic for the following xml:", xml);
            writeKafka(data, this.targetTable);
            statistics.get(batch).increment(DataWriterStatisticConstants.CREATECOUNT);
            return true;
        } catch (RuntimeException ex) {
            // This is not logged upstream
            log.error("Failed to alter Kafka Topic using the following xml: " + xml, ex);
            throw ex;
        } finally {
            statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
        }
    }

    // This is where the delete/truncate actions from the load wizard hit
    @Override
    protected boolean sql(CsvData data) {
        try {
            statistics.get(batch).startTimer(DataWriterStatisticConstants.LOADMILLIS);
            String[] parsedData = data.getParsedData(CsvData.ROW_DATA);
            String script = parsedData[0];
            List<String> sqlStatements = getSqlStatements(script);
            long count = 0;
            for (String sql : sqlStatements) {
                ISqlTransaction newTransaction = null;
                try {
                    Table table = targetTable != null ? targetTable : sourceTable;
                    sql = FormatUtils.replace("nodeId", batch.getTargetNodeId(), sql);
                    if (table != null) {
                        sql = FormatUtils.replace("catalogName", quoteString(table.getCatalog()), sql);
                        sql = FormatUtils.replace("schemaName", quoteString(table.getSchema()), sql);
                        sql = FormatUtils.replace("tableName", quoteString(table.getName()), sql);
                        DatabaseInfo info = getPlatform().getDatabaseInfo();
                        String quote = getPlatform().getDdlBuilder().isDelimitedIdentifierModeOn() ? info.getDelimiterToken() : "";
                        sql = FormatUtils.replace("fullTableName",
                                table.getQualifiedTableName(quote, info.getCatalogSeparator(), info.getSchemaSeparator()), sql);
                        final String old38CompatibilityTable = "sym_node";
                        if ((channelReload.equals(batch.getChannelId()) && sql.matches(TRUNCATE_PATTERN)
                                && !table.getNameLowerCase().equals(old38CompatibilityTable))
                                || (channelReload.equals(batch.getChannelId()) && sql.matches(DELETE_PATTERN)
                                        && !sql.toUpperCase().contains("WHERE")
                                        && !table.getNameLowerCase().equals(old38CompatibilityTable))) {
                            writeKafka(data, targetTable);
                        }
                    }
                } finally {
                    if (newTransaction != null) {
                        newTransaction.close();
                    }
                }
            }
            statistics.get(batch).increment(DataWriterStatisticConstants.SQLCOUNT);
            statistics.get(batch).increment(DataWriterStatisticConstants.SQLROWSAFFECTEDCOUNT, count);
            return true;
        } finally {
            statistics.get(batch).stopTimer(DataWriterStatisticConstants.LOADMILLIS);
        }
    }

    @Override
    protected void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails) {
        // Overridden to work with write-only kafka
    }

    @Override
    protected void allowInsertIntoAutoIncrementColumns(boolean value, Table table) {
        // Overridden to work with write-only kafka
    }

    @Override
    protected Table lookupTableAtTarget(Table sourceTable) {
        if (isSymmetricTable(sourceTable)) {
            return super.lookupTableAtTarget(sourceTable);
        }
        return sourceTable;
    }

    @Override
    public void end(Batch batch, boolean inError) {
        this.lastData = null;
        if (batch.isIgnored()) {
            getStatistics().get(batch).increment(DataWriterStatisticConstants.IGNORECOUNT);
        }
        if (!inError) {
            notifyFiltersBatchComplete();
            batchComplete(context);
            commit(false);
        } else {
            rollback();
        }
    }

    public String getTableName(String dbTableName) {
        String name = kafkaTableNamesInBatchMap.get(dbTableName);
        if (name == null) {
            String[] split = dbTableName.split("_");
            StringBuilder kafkaTableName = new StringBuilder();
            for (String part : split) {
                kafkaTableName.append(StringUtils.capitalize(part.toLowerCase()));
            }
            kafkaTableNamesInBatchMap.put(dbTableName, kafkaTableName.toString());
            name = kafkaTableName.toString();
        }
        return name;
    }

    public Class<?> getClassByTableName(String tableName) {
        Class<?> classMatch = tableClassCache.get(tableName);
        if (classMatch == null) {
            try {
                log.debug("Looking for an exact match for a POJO based on tableName " + tableName);
                classMatch = Class.forName(schemaPackage + "." + tableName);
            } catch (Exception e) {
                if (schemaPackageClassNames.size() == 0) {
                    scanSchemaPackage();
                }
                String fullTableName = schemaPackage + "." + tableName;
                for (String scannedName : schemaPackageClassNames) {
                    if (scannedName.indexOf("$") > 0) {
                        continue;
                    }
                    if (scannedName.startsWith(fullTableName)) {
                        try {
                            log.debug("Looking for a starts with match for a POJO based on tableName " + scannedName);
                            classMatch = Class.forName(scannedName);
                            break;
                        } catch (Exception nestedException) {
                        }
                    } else if (scannedName.toLowerCase().startsWith(fullTableName)) {
                        try {
                            log.debug("Looking for a starts with match all lower case for a POJO based on tableName " + scannedName);
                            classMatch = Class.forName(scannedName);
                            break;
                        } catch (Exception nestedException) {
                        }
                    }
                }
            }
            if (classMatch != null) {
                tableClassCache.put(tableName, classMatch);
            }
        }
        return classMatch;
    }

    private void scanSchemaPackage() {
        try {
            ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
            MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + resolveBasePackage(schemaPackage) + "/"
                    + "**/*.class";
            Resource[] resources = resourcePatternResolver.getResources(packageSearchPath);
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                    schemaPackageClassNames.add(metadataReader.getClassMetadata().getClassName());
                }
            }
            Collections.sort(schemaPackageClassNames, Collections.reverseOrder());
        } catch (Exception e) {
            log.warn("Unable to scan schema package : " + schemaPackage);
        }
    }

    private String resolveBasePackage(String basePackage) {
        return ClassUtils.convertClassNameToResourcePath(SystemPropertyUtils.resolvePlaceholders(basePackage));
    }

    public String getColumnName(String dbTableName, String dbColumnName, Object bean) {
        if (tableColumnCache.containsKey(dbTableName) && tableColumnCache.get(dbTableName).containsKey(dbColumnName)) {
            return tableColumnCache.get(dbTableName).get(dbColumnName);
        }
        Map<String, String> columnMap = tableColumnCache.computeIfAbsent(dbTableName, k -> new HashMap<>());
        String dbColumnNameSimple = dbColumnName.toLowerCase().replaceAll("[^a-z0-9]", "");
        for (PropertyDescriptor pd : PropertyUtils.getPropertyDescriptors(bean)) {
            if (pd.getName().toLowerCase().equals(dbColumnNameSimple)) {
                columnMap.put(dbColumnName, pd.getName());
                return pd.getName();
            }
        }
        return null;
    }

    public void sendKafkaMessage(ProducerRecord<String, Object> record) {
        log.debug("Sending message (topic={}) (key={}) {}", record.topic(), record.key(), record.value());
        kafkaProducer.send(record);
    }

    public static byte[] datumToByteArray(Schema schema, GenericRecord datum) throws IOException {
        GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<GenericRecord>(schema);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            Encoder e = EncoderFactory.get().binaryEncoder(os, null);
            writer.write(datum, e);
            e.flush();
            byte[] byteData = os.toByteArray();
            return byteData;
        } finally {
            os.close();
        }
    }

    public void batchComplete(DataContext context) {
        String batchFileName = "batch-" + context.getBatch().getSourceNodeId() + "-" + context.getBatch().getBatchId();
        String channelId = context.getBatch().getChannelId();
        if (ChannelUtils.isInternalSymmetricChannel(channelId)) {
            log.debug("Detected batch {} on the internal channel {}. Not publishing to Kafka", batchFileName, channelId);
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Completing batch {} on the {} channel. Kafka client config: {}", batchFileName, channelId, configs);
        }
        try {
            if (confluentUrl == null && kafkaDataMap.size() > 0) {
                StringBuffer kafkaText = new StringBuffer();
                String kafkaKey = null;
                for (Map.Entry<String, List<ProducerRecord<String, Object>>> entry : kafkaDataMap.entrySet()) {
                    for (ProducerRecord<String, Object> record : entry.getValue()) {
                        if (messageBy.equals(KAFKA_MESSAGE_BY_ROW)) {
                            sendKafkaMessage(record);
                        } else {
                            kafkaKey = record.key();
                            kafkaText.append(record.value());
                        }
                    }
                    if (messageBy.equals(KAFKA_MESSAGE_BY_BATCH)) {
                        sendKafkaMessage(new ProducerRecord<String, Object>(entry.getKey(), kafkaKey, kafkaText.toString()));
                    }
                }
                kafkaDataMap = new HashMap<String, List<ProducerRecord<String, Object>>>();
            }
        } catch (Exception e) {
            log.warn("Unable to write batch to Kafka " + batchFileName, e);
            throw new RuntimeException(e);
            // e.printStackTrace();
        } finally {
            context.put(KAFKA_TEXT_CACHE, new HashMap<String, List<String>>());
            kafkaTableNamesInBatchMap.clear();
            tableColumnCache = new HashMap<String, Map<String, String>>();
        }
    }

    public int writeKafka(CsvData data, Table table) {
        String[] rowData = data.getParsedData(CsvData.ROW_DATA);
        String[] oldData = data.getParsedData(CsvData.OLD_DATA);
        if (data.getDataEventType() == DataEventType.DELETE) {
            rowData = data.getParsedData(CsvData.OLD_DATA);
            if (rowData == null) {
                rowData = data.getParsedData(CsvData.PK_DATA);
            }
        }
        StringBuilder kafkaText = new StringBuilder();
        String kafkaKey = null;
        if (messageBy.equals(KAFKA_MESSAGE_BY_ROW)) {
            StringBuilder sb = new StringBuilder();
            sb.append(table.getName()).append(":");
            for (int i = 0; i < table.getPrimaryKeyColumnNames().length; i++) {
                sb.append(":").append(rowData[i]);
            }
            kafkaKey = String.valueOf(sb.toString().hashCode());
        } else if (messageBy.equals(KAFKA_MESSAGE_BY_BATCH)) {
            String s = context.getBatch().getSourceNodeId() + "-" + context.getBatch().getBatchId();
            kafkaKey = String.valueOf(s.hashCode());
        }
        if (topicBy.equals(KAFKA_TOPIC_BY_CHANNEL)) {
            kafkaDataKey = context.getBatch().getChannelId();
        } else {
            kafkaDataKey = table.getNameLowerCase();
        }
        log.debug("Processing table {} for Kafka on topic {}", table, kafkaDataKey);
        if (kafkaDataMap.get(kafkaDataKey) == null) {
            kafkaDataMap.put(kafkaDataKey, new ArrayList<ProducerRecord<String, Object>>());
        }
        List<ProducerRecord<String, Object>> kafkaDataList = kafkaDataMap.get(kafkaDataKey);
        if (rowData[0].matches(TRUNCATE_PATTERN) || rowData[0].matches(DELETE_PATTERN) || rowData[0].contains("xml version")) {
            return 1;
        }
        if (outputFormat.equals(KAFKA_FORMAT_JSON)) {
            kafkaText.append("{\"").append(table.getName()).append("\": {").append("\"eventType\": \"" + data.getDataEventType() + "\",")
                    .append("\"data\": { ");
            // Let Gson escape the json values
            Gson gson = new Gson();
            if (oldData != null) {
                for (int i = 0; i < table.getColumnCount(); i++) {
                    kafkaText.append("\"").append(table.getColumnNames()[i]).append("\": ");
                    kafkaText.append(gson.toJson(rowData[i]));
                    if (i + 1 < table.getColumnCount()) {
                        kafkaText.append(",");
                    }
                }
            } else {
                for (int i = 0; i < table.getPrimaryKeyColumnCount(); i++) {
                    kafkaText.append("\"").append(table.getColumnNames()[i]).append("\": ");
                    kafkaText.append(gson.toJson(rowData[i]));
                    if (i + 1 < table.getPrimaryKeyColumnCount()) {
                        kafkaText.append(",");
                    }
                }
            }
            kafkaText.append(" } } }");
        } else if (outputFormat.equals(KAFKA_FORMAT_CSV)) {
            // Quote every non-null field, escape quote character by
            // doubling the quote character
            kafkaText.append("\n\"TABLE\"").append(",\"").append(table.getName()).append("\",\"").append("EVENT").append("\",\"")
                    .append(data.getDataEventType()).append("\",");
            if (oldData != null) {
                for (int i = 0; i < table.getColumnNames().length; i++) {
                    kafkaText.append("\"").append(Strings.CS.replace(table.getColumnNames()[i], "\"", "\"\"")).append("\",");
                    if (rowData[i] != null) {
                        kafkaText.append("\"").append(Strings.CS.replace(rowData[i], "\"", "\"\"")).append("\"");
                    }
                    if (i + 1 < table.getColumnNames().length) {
                        kafkaText.append(",");
                    }
                }
            } else {
                for (int i = 0; i < table.getPrimaryKeyColumnCount(); i++) {
                    kafkaText.append("\"").append(Strings.CS.replace(table.getPrimaryKeyColumnNames()[i], "\"", "\"\"")).append("\",");
                    if (rowData[i] != null) {
                        kafkaText.append("\"").append(Strings.CS.replace(rowData[i], "\"", "\"\"")).append("\"");
                    }
                    if (i + 1 < table.getColumnNames().length) {
                        kafkaText.append(",");
                    }
                }
            }
        } else if (outputFormat.equals(KAFKA_FORMAT_XML)) {
            kafkaText.append("<row entity=\"").append(StringEscapeUtils.escapeXml11(table.getName())).append("\"").append(" dml=\"")
                    .append(data.getDataEventType()).append("\">");
            if (oldData != null) {
                for (int i = 0; i < table.getColumnNames().length; i++) {
                    kafkaText.append("<data key=\"").append(StringEscapeUtils.escapeXml11(table.getColumnNames()[i])).append("\">")
                            .append(StringEscapeUtils.escapeXml11(rowData[i])).append("</data>");
                }
            } else {
                for (int i = 0; i < table.getPrimaryKeyColumnCount(); i++) {
                    kafkaText.append("<data key=\"").append(StringEscapeUtils.escapeXml11(table.getPrimaryKeyColumnNames()[i])).append("\">")
                            .append(StringEscapeUtils.escapeXml11(rowData[i])).append("</data>");
                }
            }
            kafkaText.append("</row>");
        } else if (outputFormat.equals(KAFKA_FORMAT_AVRO)) {
            String tableName = getTableName(table.getName());
            if (confluentUrl != null) {
                try {
                    Class<?> curClass = getClassByTableName(tableName);
                    if (curClass != null) {
                        Constructor<?> defaultConstructor = curClass.getConstructor();
                        Object pojo = defaultConstructor.newInstance();
                        Set<String> allowedProperties = new HashSet<>();
                        for (PropertyDescriptor pd : PropertyUtils.getPropertyDescriptors(pojo)) {
                            if (pd.getWriteMethod() != null) {
                                allowedProperties.add(pd.getName());
                            }
                        }
                        if (oldData != null) {
                            for (int i = 0; i < table.getColumnNames().length; i++) {
                                String colName = getColumnName(table.getName(), table.getColumnNames()[i], pojo);
                                if (colName != null) {
                                    Class<?> propertyTypeClass = PropertyUtils.getPropertyType(pojo, colName);
                                    if (CharSequence.class.equals(propertyTypeClass)) {
                                        PropertyUtils.setSimpleProperty(pojo, colName, rowData[i]);
                                    } else if (Long.class.equals(propertyTypeClass)) {
                                        Date date = null;
                                        try {
                                            date = DateUtils.parseDate(rowData[i], parseDatePatterns);
                                        } catch (Exception e) {
                                            log.debug(rowData[i] + " was not a recognized date format so treating it as a long.");
                                        }
                                        if (allowedProperties.contains(colName)) {
                                            BeanUtils.setProperty(pojo, colName, date != null ? date.getTime() : rowData[i]);
                                        }
                                    } else {
                                        if (allowedProperties.contains(colName)) {
                                            BeanUtils.setProperty(pojo, colName, rowData[i]);
                                        }
                                    }
                                }
                            }
                        } else {
                            for (int i = 0; i < table.getPrimaryKeyColumnCount(); i++) {
                                String colName = getColumnName(table.getName(), table.getPrimaryKeyColumnNames()[i], pojo);
                                if (colName != null) {
                                    Class<?> propertyTypeClass = PropertyUtils.getPropertyType(pojo, colName);
                                    if (CharSequence.class.equals(propertyTypeClass)) {
                                        PropertyUtils.setSimpleProperty(pojo, colName, rowData[i]);
                                    } else if (Long.class.equals(propertyTypeClass)) {
                                        Date date = null;
                                        try {
                                            date = DateUtils.parseDate(rowData[i], parseDatePatterns);
                                        } catch (Exception e) {
                                            log.debug(rowData[i] + " was not a recognized date format so treating it as a long.");
                                        }
                                        if (allowedProperties.contains(colName)) {
                                            BeanUtils.setProperty(pojo, colName, date != null ? date.getTime() : rowData[i]);
                                        }
                                    } else {
                                        if (allowedProperties.contains(colName)) {
                                            BeanUtils.setProperty(pojo, colName, rowData[i]);
                                        }
                                    }
                                }
                            }
                        }
                        sendKafkaMessage(new ProducerRecord<String, Object>(kafkaDataKey, kafkaKey, pojo));
                    } else {
                        throw new RuntimeException(
                                "Unable to find a POJO to load for AVRO based message onto Kafka for table : " + tableName);
                    }
                } catch (NoSuchMethodException e) {
                    log.info("Unable to find setter on POJO based on table " + table.getName(), e);
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    log.info("Unable to invoke a default constructor on POJO based on table " + tableName, e);
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    log.info("Unable to access a default constructor on POJO based on table " + tableName, e);
                    throw new RuntimeException(e);
                } catch (InstantiationException e) {
                    log.info("Unable to instantiate a constructor on POJO based on table " + tableName, e);
                    throw new RuntimeException(e);
                }
            } else {
                GenericData.Record avroRecord = new GenericData.Record(schema);
                avroRecord.put("table", table.getName());
                avroRecord.put("eventType", data.getDataEventType().toString());
                Collection<GenericRecord> dataCollection = new ArrayList<GenericRecord>();
                String[] columnNames = (data.getDataEventType() == DataEventType.DELETE) ? table.getPrimaryKeyColumnNames() : table.getColumnNames();
                for (int i = 0; i < columnNames.length; i++) {
                    GenericRecord columnRecord = new GenericData.Record(schema.getField("data").schema().getElementType());
                    columnRecord.put("name", columnNames[i]);
                    columnRecord.put("value", rowData[i]);
                    dataCollection.add(columnRecord);
                }
                avroRecord.put("data", dataCollection);
                try {
                    kafkaDataList.add(new ProducerRecord<String, Object>(kafkaDataKey, kafkaKey, datumToByteArray(schema, avroRecord)));
                    return 1;
                } catch (IOException ioe) {
                    throw new RuntimeException("Unable to convert row data to an Avro record for table=" + tableName, ioe);
                }
            }
        }
        kafkaDataList.add(new ProducerRecord<String, Object>(kafkaDataKey, kafkaKey, kafkaText.toString()));
        return 1;
    }
}
