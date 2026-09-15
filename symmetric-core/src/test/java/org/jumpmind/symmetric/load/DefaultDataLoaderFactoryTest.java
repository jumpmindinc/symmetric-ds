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
package org.jumpmind.symmetric.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IAlterDatabaseInterceptor;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.cassandra.CassandraPlatform;
import org.jumpmind.db.platform.kafka.KafkaPlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.AbstractDatabaseWriter;
import org.jumpmind.symmetric.io.data.writer.CassandraDatabaseWriter;
import org.jumpmind.symmetric.io.data.writer.Conflict;
import org.jumpmind.symmetric.io.data.writer.Conflict.PingBack;
import org.jumpmind.symmetric.io.data.writer.Conflict.ResolveConflict;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterSettings;
import org.jumpmind.symmetric.io.data.writer.DynamicDefaultDatabaseWriter;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterConflictResolver;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterErrorHandler;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;
import org.jumpmind.symmetric.io.data.writer.KafkaWriter;
import org.jumpmind.symmetric.io.data.writer.ResolvedData;
import org.jumpmind.symmetric.io.data.writer.TransformWriter;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultDataLoaderFactoryTest {
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private ISymmetricDialect symmetricDialect;
    private IExtensionService extensionService;
    private ICacheManager cacheManager;
    private DefaultDataLoaderFactory factory;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        extensionService = mock(IExtensionService.class);
        cacheManager = mock(ICacheManager.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(extensionService.getExtensionPointList(IAlterDatabaseInterceptor.class)).thenReturn(new ArrayList<IAlterDatabaseInterceptor>());
        IDatabasePlatform defaultPlatform = mock(IDatabasePlatform.class);
        when(defaultPlatform.getName()).thenReturn("h2");
        when(symmetricDialect.getTargetPlatform()).thenReturn(defaultPlatform);
        IDatabasePlatform sourcePlatform = mock(IDatabasePlatform.class);
        when(sourcePlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(symmetricDialect.getPlatform()).thenReturn(sourcePlatform);
        when(symmetricDialect.getTablePrefix()).thenReturn("sym");
        when(parameterService.getAllParameters()).thenReturn(new TypedProperties());
        factory = new DefaultDataLoaderFactory(engine);
    }

    @Test
    void testGetTypeName() {
        assertEquals("default", factory.getTypeName());
    }

    @Test
    void testIsPlatformSupported() {
        assertTrue(factory.isPlatformSupported(mock(IDatabasePlatform.class)));
    }

    @Test
    void testSetSymmetricEngine_wiresEngineAndParameterService() {
        DefaultDataLoaderFactory f = new DefaultDataLoaderFactory();
        f.setSymmetricEngine(engine);
        DatabaseWriterSettings settings = f.buildDatabaseWriterSettings(null, null, null, null);
        assertNotNull(settings);
    }

    @Test
    void testGetDataWriter_withCassandraPlatformReturnsCassandraDatabaseWriter() {
        CassandraPlatform cassandraPlatform = mock(CassandraPlatform.class);
        when(cassandraPlatform.getName()).thenReturn(DatabaseNamesConstants.CASSANDRA);
        when(symmetricDialect.getTargetPlatform()).thenReturn(cassandraPlatform);
        IDataWriter writer = factory.getDataWriter("node1", null, symmetricDialect, null, null, null, null, null);
        assertTrue(writer instanceof CassandraDatabaseWriter);
    }

    @Test
    void testGetDataWriter_withKafkaPlatformReturnsKafkaWriter() {
        KafkaPlatform kafkaPlatform = mock(KafkaPlatform.class);
        when(kafkaPlatform.getName()).thenReturn(DatabaseNamesConstants.KAFKA);
        when(symmetricDialect.getTargetPlatform()).thenReturn(kafkaPlatform);
        when(parameterService.getString(ParameterConstants.KAFKA_PRODUCER, "SymmetricDS")).thenReturn("SymmetricDS");
        when(parameterService.getString(ParameterConstants.KAFKA_FORMAT, KafkaWriter.KAFKA_FORMAT_JSON)).thenReturn(KafkaWriter.KAFKA_FORMAT_JSON);
        when(parameterService.getString(ParameterConstants.KAFKA_TOPIC_BY, KafkaWriter.KAFKA_TOPIC_BY_CHANNEL)).thenReturn(KafkaWriter.KAFKA_TOPIC_BY_CHANNEL);
        when(parameterService.getString(ParameterConstants.KAFKA_MESSAGE_BY, KafkaWriter.KAFKA_MESSAGE_BY_BATCH)).thenReturn(
                KafkaWriter.KAFKA_MESSAGE_BY_BATCH);
        when(parameterService.getString(ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + "db.url")).thenReturn("localhost:9092");
        when(parameterService.getExternalId()).thenReturn("node1");
        IDataWriter writer = factory.getDataWriter("node1", null, symmetricDialect, null, null, null, null, null);
        assertTrue(writer instanceof KafkaWriter);
    }

    @Test
    void testGetDataWriter_withOtherPlatformReturnsDynamicDefaultDatabaseWriter() {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(false);
        IDataWriter writer = factory.getDataWriter("node1", null, symmetricDialect, null, null, null, null, null);
        assertTrue(writer instanceof DynamicDefaultDatabaseWriter);
    }

    @Test
    void testGetDataWriter_whenUsingTargetExternalIdReturnsDynamicDefaultDatabaseWriter() {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(true);
        IDataWriter writer = factory.getDataWriter("node1", null, symmetricDialect, null, null, null, null, null);
        assertTrue(writer instanceof DynamicDefaultDatabaseWriter);
    }

    @Test
    void testGetDataWriter_tableKeyOverride_delegatesToSuperWhenNameLacksSourceNodeId() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(true);
        DynamicDefaultDatabaseWriter writer = (DynamicDefaultDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, null, null, null,
                null, null);
        writer.start(new Batch(BatchType.LOAD, 1L, null, null, "005", "node1", false));
        Table table = new Table(null, null, "TEST_TABLE");
        String key = (String) invokeProtected(writer, "getTableKey", new Class<?>[] { Table.class }, table);
        assertEquals(table.getKey(), key);
    }

    @Test
    void testGetDataWriter_tableKeyOverride_stripsSourceNodeIdWhenNameContainsIt() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(true);
        DynamicDefaultDatabaseWriter writer = (DynamicDefaultDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, null, null, null,
                null, null);
        writer.start(new Batch(BatchType.LOAD, 1L, null, null, "005", "node1", false));
        Table table = new Table(null, null, "TEST_TABLE_005");
        String key = (String) invokeProtected(writer, "getTableKey", new Class<?>[] { Table.class }, table);
        assertEquals(new Table(null, null, "TEST_TABLE_").getKey(), key);
    }

    @Test
    void testGetDataWriter_putTableInCacheOverride_storesInFactoryTargetTableMap() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(true);
        DynamicDefaultDatabaseWriter writer = (DynamicDefaultDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, null, null, null,
                null, null);
        Table table = new Table(null, null, "TEST_TABLE");
        invokeProtected(writer, "putTableInCache", new Class<?>[] { String.class, Table.class }, "key1", table);
        assertEquals(table, factory.targetTableMap.get("key1"));
    }

    @Test
    void testGetDataWriter_lookupTableFromCacheOverride_returnsRenamedCopyWhenSourceNameContainsSourceNodeId() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(true);
        DynamicDefaultDatabaseWriter writer = (DynamicDefaultDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, null, null, null,
                null, null);
        writer.start(new Batch(BatchType.LOAD, 1L, null, null, "005", "node1", false));
        Table cachedTable = new Table(null, null, "TEST_TABLE_");
        factory.targetTableMap.put("key1", cachedTable);
        Table sourceTable = new Table(null, null, "TEST_TABLE_005");
        Table result = (Table) invokeProtected(writer, "lookupTableFromCache", new Class<?>[] { Table.class, String.class }, sourceTable, "key1");
        assertNotSame(cachedTable, result);
        assertEquals("TEST_TABLE_005", result.getName());
    }

    @Test
    void testGetDataWriter_lookupTableFromCacheOverride_returnsNullWhenNotCached() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(true);
        DynamicDefaultDatabaseWriter writer = (DynamicDefaultDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, null, null, null,
                null, null);
        writer.start(new Batch(BatchType.LOAD, 1L, null, null, "005", "node1", false));
        Table sourceTable = new Table(null, null, "TEST_TABLE_005");
        Table result = (Table) invokeProtected(writer, "lookupTableFromCache", new Class<?>[] { Table.class, String.class }, sourceTable, "key1");
        assertNull(result);
    }

    @Test
    void testGetDataWriter_conflictResolverBeforeResolutionAttempt_enablesSyncTriggersWhenPingBackEnabled() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(false);
        TransformWriter transformWriter = mock(TransformWriter.class);
        DynamicDefaultDatabaseWriter nestedWriter = mock(DynamicDefaultDatabaseWriter.class);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        when(transformWriter.getNestedWriterOfType(DynamicDefaultDatabaseWriter.class)).thenReturn(nestedWriter);
        when(nestedWriter.getTransaction()).thenReturn(transaction);
        AbstractDatabaseWriter writer = (AbstractDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, transformWriter, null, null,
                null, null);
        IDatabaseWriterConflictResolver resolver = writer.getConflictResolver();
        Conflict conflict = new Conflict();
        conflict.setPingBack(PingBack.SINGLE_ROW);
        invokeProtected(resolver, "beforeResolutionAttempt", new Class<?>[] { CsvData.class, Conflict.class }, new CsvData(), conflict);
        verify(symmetricDialect).enableSyncTriggers(transaction);
    }

    @Test
    void testGetDataWriter_conflictResolverBeforeResolutionAttempt_skipsSyncTriggersWhenPingBackOff() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(false);
        TransformWriter transformWriter = mock(TransformWriter.class);
        AbstractDatabaseWriter writer = (AbstractDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, transformWriter, null, null,
                null, null);
        IDatabaseWriterConflictResolver resolver = writer.getConflictResolver();
        Conflict conflict = new Conflict();
        conflict.setPingBack(PingBack.OFF);
        invokeProtected(resolver, "beforeResolutionAttempt", new Class<?>[] { CsvData.class, Conflict.class }, new CsvData(), conflict);
        verify(transformWriter, never()).getNestedWriterOfType(any());
        verify(symmetricDialect, never()).enableSyncTriggers(any(ISqlTransaction.class));
    }

    @Test
    void testGetDataWriter_conflictResolverAfterResolutionAttempt_disablesSyncTriggersOnSingleRowPingBack() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(false);
        TransformWriter transformWriter = mock(TransformWriter.class);
        DynamicDefaultDatabaseWriter nestedWriter = mock(DynamicDefaultDatabaseWriter.class);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        when(transformWriter.getNestedWriterOfType(DynamicDefaultDatabaseWriter.class)).thenReturn(nestedWriter);
        when(nestedWriter.getContext()).thenReturn(new DataContext());
        when(nestedWriter.getTransaction()).thenReturn(transaction);
        AbstractDatabaseWriter writer = (AbstractDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, transformWriter, null, null,
                null, null);
        IDatabaseWriterConflictResolver resolver = writer.getConflictResolver();
        Conflict conflict = new Conflict();
        conflict.setPingBack(PingBack.SINGLE_ROW);
        conflict.setResolveType(ResolveConflict.MANUAL);
        invokeProtected(resolver, "afterResolutionAttempt", new Class<?>[] { CsvData.class, Conflict.class }, new CsvData(), conflict);
        verify(symmetricDialect).disableSyncTriggers(transaction, "node1");
    }

    @Test
    void testGetDataWriter_conflictResolverAfterResolutionAttempt_doesNothingWhenTransactionAborted() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(false);
        TransformWriter transformWriter = mock(TransformWriter.class);
        DynamicDefaultDatabaseWriter nestedWriter = mock(DynamicDefaultDatabaseWriter.class);
        DataContext nestedContext = new DataContext();
        nestedContext.put(AbstractDatabaseWriter.TRANSACTION_ABORTED, true);
        when(transformWriter.getNestedWriterOfType(DynamicDefaultDatabaseWriter.class)).thenReturn(nestedWriter);
        when(nestedWriter.getContext()).thenReturn(nestedContext);
        AbstractDatabaseWriter writer = (AbstractDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, transformWriter, null, null,
                null, null);
        IDatabaseWriterConflictResolver resolver = writer.getConflictResolver();
        Conflict conflict = new Conflict();
        conflict.setPingBack(PingBack.SINGLE_ROW);
        invokeProtected(resolver, "afterResolutionAttempt", new Class<?>[] { CsvData.class, Conflict.class }, new CsvData(), conflict);
        verify(nestedWriter, never()).getTransaction();
        verify(symmetricDialect, never()).disableSyncTriggers(any(ISqlTransaction.class), any());
    }

    @Test
    void testGetDataWriter_conflictResolverIsCaptureTimeNewer_returnsTrueWhenTargetTableIsNull() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(false);
        AbstractDatabaseWriter writer = (AbstractDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, null, null, null, null, null);
        IDatabaseWriterConflictResolver resolver = writer.getConflictResolver();
        AbstractDatabaseWriter rowWriter = mock(AbstractDatabaseWriter.class);
        when(rowWriter.getTargetTable()).thenReturn(null);
        boolean result = (boolean) invokeProtected(resolver, "isCaptureTimeNewer",
                new Class<?>[] { Conflict.class, AbstractDatabaseWriter.class, CsvData.class, String.class },
                new Conflict(), rowWriter, new CsvData(), "TEST_TABLE");
        assertTrue(result);
    }

    @Test
    void testGetDataWriter_conflictResolverIsCaptureTimeNewer_returnsTrueWhenNoMatchingTriggerHistory() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(false);
        AbstractDatabaseWriter writer = (AbstractDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, null, null, null, null, null);
        IDatabaseWriterConflictResolver resolver = writer.getConflictResolver();
        AbstractDatabaseWriter rowWriter = mock(AbstractDatabaseWriter.class);
        when(rowWriter.getTargetTable()).thenReturn(new Table(null, null, "TEST_TABLE"));
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(triggerRouterService.getActiveTriggerHistoriesFromCache()).thenReturn(Collections.emptyList());
        boolean result = (boolean) invokeProtected(resolver, "isCaptureTimeNewer",
                new Class<?>[] { Conflict.class, AbstractDatabaseWriter.class, CsvData.class, String.class },
                new Conflict(), rowWriter, new CsvData(), "TEST_TABLE");
        assertTrue(result);
    }

    @Test
    void testGetDataWriter_conflictResolverCaptureMissingDelete_insertsDataWhenTriggerSyncsOnIncomingBatch() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(false);
        AbstractDatabaseWriter writer = (AbstractDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, null, null, null, null, null);
        IDatabaseWriterConflictResolver resolver = writer.getConflictResolver();
        AbstractDatabaseWriter rowWriter = mock(AbstractDatabaseWriter.class);
        when(rowWriter.getTargetTable()).thenReturn(new Table(null, null, "TEST_TABLE"));
        DataContext rowContext = mock(DataContext.class);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        when(rowContext.findTransaction()).thenReturn(transaction);
        when(rowWriter.getContext()).thenReturn(rowContext);
        TriggerHistory hist = new TriggerHistory();
        hist.setTriggerId("trig1");
        hist.setSourceTableName("TEST_TABLE");
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(triggerRouterService.getActiveTriggerHistories("TEST_TABLE")).thenReturn(Arrays.asList(hist));
        Trigger trigger = new Trigger();
        trigger.setSyncOnIncomingBatch(true);
        when(triggerRouterService.getTriggerById("trig1", false)).thenReturn(trigger);
        IDataService dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
        CsvData data = new CsvData(DataEventType.DELETE);
        data.putParsedData(CsvData.PK_DATA, new String[] { "1" });
        invokeProtected(resolver, "captureMissingDelete", new Class<?>[] { Conflict.class, AbstractDatabaseWriter.class, CsvData.class },
                new Conflict(), rowWriter, data);
        verify(dataService).insertData(eq(transaction), any(Data.class));
    }

    @Test
    void testGetDataWriter_conflictResolverCaptureMissingDelete_doesNothingWhenNoTriggerHistories() throws Exception {
        when(cacheManager.isUsingTargetExternalId(false)).thenReturn(false);
        AbstractDatabaseWriter writer = (AbstractDatabaseWriter) factory.getDataWriter("node1", null, symmetricDialect, null, null, null, null, null);
        IDatabaseWriterConflictResolver resolver = writer.getConflictResolver();
        AbstractDatabaseWriter rowWriter = mock(AbstractDatabaseWriter.class);
        when(rowWriter.getTargetTable()).thenReturn(new Table(null, null, "TEST_TABLE"));
        ITriggerRouterService triggerRouterService = mock(ITriggerRouterService.class);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        when(triggerRouterService.getActiveTriggerHistories("TEST_TABLE")).thenReturn(Collections.emptyList());
        invokeProtected(resolver, "captureMissingDelete", new Class<?>[] { Conflict.class, AbstractDatabaseWriter.class, CsvData.class },
                new Conflict(), rowWriter, new CsvData(DataEventType.DELETE));
        verify(engine, never()).getDataService();
    }

    @Test
    void testGetDefaultTreatBitAsInteger_trueForMysqlFamily() {
        assertTrue(getDefaultTreatBitAsIntegerFor(DatabaseNamesConstants.MYSQL));
        assertTrue(getDefaultTreatBitAsIntegerFor(DatabaseNamesConstants.MARIADB));
        assertTrue(getDefaultTreatBitAsIntegerFor(DatabaseNamesConstants.AURORA_MYSQL));
        assertTrue(getDefaultTreatBitAsIntegerFor(DatabaseNamesConstants.CLOUDSQL_MYSQL));
    }

    @Test
    void testGetDefaultTreatBitAsInteger_falseForOtherPlatforms() {
        assertFalse(getDefaultTreatBitAsIntegerFor("oracle"));
    }

    @Test
    void testBuildDatabaseWriterSettings_fourArgOverload_hasNoDdlExecutionCallback() {
        DatabaseWriterSettings settings = factory.buildDatabaseWriterSettings(null, null, null, null);
        assertNull(settings.getDdlExecutionCallback());
    }

    @Test
    void testBuildDatabaseWriterSettings_setsFiltersErrorHandlersAndResolvedData() {
        List<IDatabaseWriterFilter> filters = Arrays.asList(mock(IDatabaseWriterFilter.class));
        List<IDatabaseWriterErrorHandler> errorHandlers = Arrays.asList(mock(IDatabaseWriterErrorHandler.class));
        List<ResolvedData> resolvedData = Arrays.asList(mock(ResolvedData.class));
        DatabaseWriterSettings settings = factory.buildDatabaseWriterSettings(symmetricDialect, filters, errorHandlers, null, resolvedData);
        assertEquals(filters, settings.getDatabaseWriterFilters());
        assertEquals(errorHandlers, settings.getDatabaseWriterErrorHandlers());
        assertEquals(resolvedData, settings.getResolvedData());
        assertNotNull(settings.getDdlExecutionCallback());
    }

    private boolean getDefaultTreatBitAsIntegerFor(String platformName) {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(platformName);
        when(symmetricDialect.getTargetPlatform()).thenReturn(platform);
        return factory.getDefaultTreatBitAsInteger();
    }

    private Object invokeProtected(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }
}
