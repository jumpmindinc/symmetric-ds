package org.jumpmind.symmetric.service.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.IIndex;
import org.jumpmind.db.model.IndexColumn;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.model.UniqueIndex;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.config.TriggerFailureListener;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IClusterService;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IGroupletService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ISequenceService;
import org.jumpmind.symmetric.service.impl.TriggerRouterService.TriggerHistoryMapper;
import org.jumpmind.symmetric.service.impl.TriggerRouterService.TriggerTableSupportingInfo;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

public class TriggerRouterServiceTest {
    protected ICacheManager cacheManager;
    protected IClusterService clusterService;
    protected IConfigurationService configurationService;
    protected IStatisticManager statisticManager;
    protected IGroupletService groupletService;
    protected INodeService nodeService;
    protected ISequenceService sequenceService;
    protected IExtensionService extensionService;
    protected IParameterService parameterService;
    protected ISymmetricEngine engine;
    protected ISymmetricDialect symmetricDialect;
    protected IDatabasePlatform platform;
    protected ISqlTemplate sqlTemplate;
    protected ISqlTransaction sqlTransaction;
    protected String tablePrefix;
    protected TriggerFailureListener failureListener;

    @BeforeEach
    void setup() throws Exception {
        engine = mock(ISymmetricEngine.class);
        cacheManager = mock(ICacheManager.class);
        clusterService = mock(IClusterService.class);
        configurationService = mock(IConfigurationService.class);
        statisticManager = mock(IStatisticManager.class);
        groupletService = mock(IGroupletService.class);
        nodeService = mock(INodeService.class);
        sequenceService = mock(ISequenceService.class);
        extensionService = mock(IExtensionService.class);
        parameterService = mock(IParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        symmetricDialect = mock(ISymmetricDialect.class);
        sqlTransaction = mock(ISqlTransaction.class);
        failureListener = mock(TriggerFailureListener.class);
        tablePrefix = "test";
    }

    @Test
    void testTriggerRouterServiceConstructor() throws Exception {
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        doNothing().when(extensionService).addExtensionPoint(failureListener);
        new TriggerRouterService(engine);
    }

    @Test
    void testRefreshFromDatabase() throws Exception {
        Date date = new Date();
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        doNothing().when(extensionService).addExtensionPoint(failureListener);
        doReturn(date).when(sqlTemplate).queryForObject("null", Date.class);
        TriggerRouterService triggerRouterService = new TriggerRouterService(engine);
        boolean testRefreshFromDatabase = triggerRouterService.refreshFromDatabase();
        assertTrue(testRefreshFromDatabase);
    }

    @Test
    void testGetTriggers() throws Exception {
        List<Trigger> dummyTriggerList = new ArrayList<Trigger>();
        Trigger dummyTrigger = new Trigger();
        dummyTrigger.setTriggerId("test");
        dummyTrigger.setSourceCatalogName("$(cat)");
        dummyTrigger.setSourceSchemaName("$(schem)");
        dummyTrigger.setSourceTableName("$(table)");
        dummyTriggerList.add(dummyTrigger);
        TypedProperties replacements = new TypedProperties();
        replacements.put("schem", "schemTest");
        replacements.put("cat", "catTest");
        replacements.put("table", "tableTest");
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        doReturn(replacements).when(parameterService).getAllParameters();
        doNothing().when(extensionService).addExtensionPoint(failureListener);
        doReturn(dummyTriggerList).when(sqlTemplate).query(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(Object[].class));
        TriggerRouterService triggerRouterService = new TriggerRouterService(engine);
        List<Trigger> actualTriggerList = triggerRouterService.getTriggers(true);
        List<Trigger> expectedTriggerList = new ArrayList<Trigger>();
        Trigger expectedTrigger = new Trigger();
        expectedTrigger.setTriggerId("test");
        expectedTrigger.setSourceCatalogName("catTest");
        expectedTrigger.setSourceSchemaName("schemTest");
        expectedTrigger.setSourceTableName("tableTest");
        expectedTriggerList.add(expectedTrigger);
        assertEquals(expectedTriggerList, actualTriggerList);
        assertEquals(expectedTriggerList.get(0), actualTriggerList.get(0));
    }

    @Test
    void testDeleteTriggers() throws Exception {
        List<Trigger> dummyTriggerList = new ArrayList<Trigger>();
        List<TriggerRouter> dummyTriggerRouterList = new ArrayList<TriggerRouter>();
        Trigger dummyTrigger = new Trigger();
        dummyTrigger.setTriggerId("test");
        dummyTrigger.setSourceCatalogName("$(cat)");
        dummyTrigger.setSourceSchemaName("$(schem)");
        dummyTrigger.setSourceTableName("$(table)");
        dummyTriggerList.add(dummyTrigger);
        TriggerRouter dummyTriggerRouter = new TriggerRouter();
        dummyTriggerRouter.setTriggerId("testTriggerRouter");
        dummyTriggerRouter.setTrigger(dummyTrigger);
        dummyTriggerRouterList.add(dummyTriggerRouter);
        // initial mocks to get things setup for constructor
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(parameterService.getInt("data.flush.jdbc.batch.size")).thenReturn(10);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        doNothing().when(extensionService).addExtensionPoint(failureListener);
        doReturn(dummyTriggerList).when(sqlTemplate).query(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                (Object) ArgumentMatchers.any());
        TriggerRouterService triggerRouterService = new TriggerRouterService(engine);
        TriggerRouterService spyTriggerRouterService = spy(triggerRouterService);
        when(spyTriggerRouterService.getTriggerRouters(true)).thenReturn(dummyTriggerRouterList);
        spyTriggerRouterService.deleteTriggers(dummyTriggerList);
        verify(sqlTransaction, times(2)).prepare("null");
        verify(sqlTransaction, times(2)).addRow(ArgumentMatchers.any(), (Object[]) ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void testCreateTriggersOnChannelForTables() throws Exception {
        List<String> tables = new ArrayList<String>();
        tables.add("testTable");
        // block for get triggers. cant figure out why it doesnt let me just
        // return and instead goes into the method
        List<Trigger> dummyTriggerList = new ArrayList<Trigger>();
        Trigger dummyTrigger = new Trigger();
        Trigger dummyTrigger2 = new Trigger();
        dummyTrigger.setTriggerId("testTable");
        dummyTrigger.setSourceCatalogName("$(cat)");
        dummyTrigger.setSourceSchemaName("$(schem)");
        dummyTrigger.setSourceTableName("$(table)");
        dummyTrigger2.setTriggerId("testTable_0");
        dummyTrigger2.setSourceCatalogName("$(cat)");
        dummyTrigger2.setSourceSchemaName("$(schem)");
        dummyTrigger2.setSourceTableName("$(table)");
        dummyTriggerList.add(dummyTrigger);
        dummyTriggerList.add(dummyTrigger2);
        TypedProperties replacements = new TypedProperties();
        replacements.put("schem", "schemTest");
        replacements.put("cat", "catTest");
        replacements.put("table", "tableTest");
        // initial mocks to get things setup for constructor
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(parameterService.getInt("data.flush.jdbc.batch.size")).thenReturn(10);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(sqlTemplate.update(ArgumentMatchers.anyString(), (Object[]) ArgumentMatchers.any(), (int[]) ArgumentMatchers.any()))
                .thenReturn(1);
        doReturn(replacements).when(parameterService).getAllParameters();
        doNothing().when(extensionService).addExtensionPoint(failureListener);
        doReturn(dummyTriggerList).when(sqlTemplate).query(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                (Object) ArgumentMatchers.any());
        TriggerRouterService triggerRouterService = new TriggerRouterService(engine);
        TriggerRouterService spyTriggerRouterService = spy(triggerRouterService);
        spyTriggerRouterService.createTriggersOnChannelForTables("test", "cat", "schem", tables, "admin");
        // when(spyTriggerRouterService.getTriggers(true)).thenReturn(dummyTriggerList);
        doReturn(dummyTriggerList).when(spyTriggerRouterService).getTriggers();
    }

    @ParameterizedTest
    @CsvSource({ "INSERT," + 10 + ",true,false,false", "INSERT," + 30 + ",false,true,false", "UPDATE," + 10 + ",true,false,false",
            "UPDATE," + 30 + ",false,false,true", "DELETE," + 10 + ",true,false,false", "DELETE," + 30 + ",false,false,false",
            "INSERT," + 30 + ",true,true,false", "INSERT," + 2 + ",true,true,false" })
    void testGetTriggerName(String dmlType, int triggerNameLength, boolean isDupe, boolean isTableNameWildCarded, boolean isTableNameExpanded)
            throws Exception {
        List<String> mockTriggerNamesGeneratedThisSession = new ArrayList<String>();
        List<TriggerHistory> dummyActiveTriggerHistory = new ArrayList<TriggerHistory>();
        TriggerHistory dummyTriggerHist = new TriggerHistory();
        Trigger dummyTrigger = new Trigger();
        if (triggerNameLength >= 10) {
            dummyTriggerHist.setTriggerHistoryId(0);
            dummyTriggerHist.setTriggerId("testTable");
            dummyTriggerHist.setNameForDeleteTrigger("TESTINSER");
            dummyActiveTriggerHistory.add(dummyTriggerHist);
            dummyTrigger.setTriggerId("testTable");
            dummyTrigger.setSourceCatalogName("$(cat)");
            dummyTrigger.setSourceSchemaName("$(schem)");
            dummyTrigger.setSourceTableName("$(table)");
            if (isDupe) {
                dummyTrigger.setNameForInsertTrigger("TESTINSER");
                dummyTrigger.setNameForUpdateTrigger("testUpdateName");
                dummyTrigger.setNameForDeleteTrigger("testDeleteName");
            }
            if (isTableNameWildCarded) {
                dummyTrigger.setSourceTableName("*sourceTableName");
            }
            if (isTableNameExpanded) {
                dummyTrigger.setSourceTableNameExpanded(isTableNameExpanded);
            }
        } else {
            dummyTriggerHist.setTriggerHistoryId(0);
            dummyTriggerHist.setTriggerId("testTable");
            dummyTriggerHist.setNameForDeleteTrigger("TE");
            dummyActiveTriggerHistory.add(dummyTriggerHist);
            dummyTrigger.setTriggerId("testTable");
            dummyTrigger.setSourceCatalogName("$(cat)");
            dummyTrigger.setSourceSchemaName("$(schem)");
            dummyTrigger.setSourceTableName("$(table)");
            if (isDupe) {
                dummyTrigger.setNameForInsertTrigger("TE");
                dummyTrigger.setNameForUpdateTrigger("testUpdateName");
                dummyTrigger.setNameForDeleteTrigger("testDeleteName");
            }
            if (isTableNameWildCarded) {
                dummyTrigger.setSourceTableName("*sourceTableName");
            }
            if (isTableNameExpanded) {
                dummyTrigger.setSourceTableNameExpanded(isTableNameExpanded);
            }
        }
        TriggerHistory oldHist = new TriggerHistory();
        oldHist.setTriggerHistoryId(1);
        Table dummyTable = new Table();
        dummyTable.setName("testingTable");
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(parameterService.getInt("data.flush.jdbc.batch.size")).thenReturn(10);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(sqlTemplate.update(ArgumentMatchers.anyString(), (Object[]) ArgumentMatchers.any(), (int[]) ArgumentMatchers.any()))
                .thenReturn(1);
        when(parameterService.getString(ParameterConstants.RUNTIME_CONFIG_TRIGGER_PREFIX, tablePrefix)).thenReturn("test");
        when(parameterService.getNodeGroupId()).thenReturn("TestNodeGroup");
        TriggerRouterService triggerRouterService = new TriggerRouterService(engine);
        TriggerRouterService spyTriggerRouterService = spy(triggerRouterService);
        DataEventType dml = null;
        if (dmlType.equals("INSERT")) {
            dml = DataEventType.INSERT;
        } else if (dmlType.equals("UPDATE")) {
            dml = DataEventType.UPDATE;
        } else if (dmlType.equals("DELETE")) {
            dml = DataEventType.DELETE;
        }
        String actualTriggerName = spyTriggerRouterService.getTriggerName(dml, triggerNameLength, dummyTrigger, dummyTable,
                dummyActiveTriggerHistory, oldHist, mockTriggerNamesGeneratedThisSession);
        String expectedTriggerName;
        if (triggerNameLength == 30) {
            if (isDupe && isTableNameWildCarded) {
                // This only needs the Insert ID as that is the only one used in
                // this test
                expectedTriggerName = "TESTINSER1";
            } else {
                if (dmlType.equals("INSERT")) {
                    expectedTriggerName = "TEST_ON_I_FOR_TSTNGTBL";
                } else if (dmlType.equals("UPDATE")) {
                    expectedTriggerName = "TEST_ON_U_FOR_TSTNGTBL";
                } else {
                    expectedTriggerName = "TEST_ON_D_FOR_TSTTBL_TSTNDGRP";
                }
            }
        } else if (triggerNameLength == 10) {
            if (dmlType.equals("INSERT")) {
                expectedTriggerName = "TESTINSER";
            } else if (dmlType.equals("UPDATE")) {
                expectedTriggerName = "TESTUPDAT";
            } else {
                expectedTriggerName = "TESTDELET";
            }
        } else {
            if (isDupe && isTableNameWildCarded) {
                expectedTriggerName = "T1";
            } else {
                expectedTriggerName = "TE";
            }
        }
        assertEquals(expectedTriggerName, actualTriggerName);
    }

    @ParameterizedTest
    @CsvSource({ "INSERT", "UPDATE", "DELETE" })
    void testGetTriggerNameSimple(String dmlType) throws Exception {
        TriggerTableSupportingInfo mockTriggerTableSupportingInfo = mock(TriggerTableSupportingInfo.class);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(parameterService.getInt("data.flush.jdbc.batch.size")).thenReturn(10);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(sqlTemplate.update(ArgumentMatchers.anyString(), (Object[]) ArgumentMatchers.any(), (int[]) ArgumentMatchers.any()))
                .thenReturn(1);
        when(parameterService.getString(ParameterConstants.RUNTIME_CONFIG_TRIGGER_PREFIX, tablePrefix)).thenReturn("test");
        when(parameterService.getNodeGroupId()).thenReturn("TestNodeGroup");
        when(mockTriggerTableSupportingInfo.getInsertTriggerName()).thenReturn("testInsertTriggerName");
        when(mockTriggerTableSupportingInfo.getUpdateTriggerName()).thenReturn("testUpdateTriggerName");
        when(mockTriggerTableSupportingInfo.getDeleteTriggerName()).thenReturn("testDeleteTriggerName");
        TriggerRouterService triggerRouterService = new TriggerRouterService(engine);
        TriggerRouterService spyTriggerRouterService = spy(triggerRouterService);
        DataEventType dml = null;
        String expectedTriggerName = null;
        if (dmlType.equals("INSERT")) {
            dml = DataEventType.INSERT;
            expectedTriggerName = "testInsertTriggerName";
        } else if (dmlType.equals("UPDATE")) {
            dml = DataEventType.UPDATE;
            expectedTriggerName = "testUpdateTriggerName";
        } else if (dmlType.equals("DELETE")) {
            dml = DataEventType.DELETE;
            expectedTriggerName = "testDeleteTriggerName";
        }
        String actualTriggerName = spyTriggerRouterService.getTriggerName(dml, mockTriggerTableSupportingInfo);
        assertEquals(expectedTriggerName, actualTriggerName);
    }

    @Test
    void testFindTriggerHistoryForGenericSync() throws Exception {
        TriggerHistory dummyTriggerHist = new TriggerHistory();
        dummyTriggerHist.setTriggerHistoryId(2);
        dummyTriggerHist.setTriggerId("testTable");
        dummyTriggerHist.setNameForDeleteTrigger("TESTINSER");
        dummyTriggerHist.setSourceTableName("test_node");
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(parameterService.getInt("data.flush.jdbc.batch.size")).thenReturn(10);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(sqlTemplate.update(ArgumentMatchers.anyString(), (Object[]) ArgumentMatchers.any(), (int[]) ArgumentMatchers.any()))
                .thenReturn(1);
        when(parameterService.getString(ParameterConstants.RUNTIME_CONFIG_TRIGGER_PREFIX, tablePrefix)).thenReturn("test");
        when(parameterService.getNodeGroupId()).thenReturn("TestNodeGroup");
        when(sqlTemplate.queryForObject(ArgumentMatchers.anyString(), ArgumentMatchers.any(TriggerHistoryMapper.class), ArgumentMatchers.anyInt()))
                .thenReturn(dummyTriggerHist);
        TriggerRouterService triggerRouterService = new TriggerRouterService(engine);
        TriggerRouterService spyTriggerRouterService = spy(triggerRouterService);
        spyTriggerRouterService.getTriggerHistory(2);
        TriggerHistory actualTriggerHistory = spyTriggerRouterService.findTriggerHistoryForGenericSync();
        assertEquals(dummyTriggerHist, actualTriggerHistory);
    }

    @Test
    void testFindMatchingTriggers() throws Exception {
        List<Trigger> dummyTriggerList = new ArrayList<Trigger>();
        Trigger dummyTrigger = new Trigger();
        dummyTrigger.setTriggerId("test");
        dummyTrigger.setSourceCatalogName("cat");
        dummyTrigger.setSourceSchemaName("schem");
        dummyTrigger.setSourceTableName("table");
        dummyTriggerList.add(dummyTrigger);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getDefaultCatalog()).thenReturn("cat");
        when(platform.getDefaultSchema()).thenReturn("schem");
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(symmetricDialect.getTargetDialect()).thenReturn(symmetricDialect);
        when(parameterService.getInt("data.flush.jdbc.batch.size")).thenReturn(10);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(sqlTemplate.update(ArgumentMatchers.anyString(), (Object[]) ArgumentMatchers.any(), (int[]) ArgumentMatchers.any()))
                .thenReturn(1);
        when(parameterService.getString(ParameterConstants.RUNTIME_CONFIG_TRIGGER_PREFIX, tablePrefix)).thenReturn("table");
        when(parameterService.getNodeGroupId()).thenReturn("TestNodeGroup");
        TriggerRouterService triggerRouterService = new TriggerRouterService(engine);
        TriggerRouterService spyTriggerRouterService = spy(triggerRouterService);
        Collection<Trigger> actualMatchingTriggers = spyTriggerRouterService.findMatchingTriggers(dummyTriggerList, "cat", "schem", "table");
        Collection<Trigger> expectedMatchingTriggers = new ArrayList<Trigger>();
        expectedMatchingTriggers.add(dummyTrigger);
        assertArrayEquals(expectedMatchingTriggers.toArray(), actualMatchingTriggers.toArray());
        dummyTrigger.setTriggerId("testing");
        dummyTrigger.setSourceCatalogName("catalog");
        dummyTrigger.setSourceSchemaName("schema");
        dummyTrigger.setSourceTableName("tableName");
        assertNotEquals(expectedMatchingTriggers, actualMatchingTriggers);
        dummyTriggerList.clear();
        dummyTrigger = new Trigger();
        dummyTrigger.setTriggerId("test");
        dummyTrigger.setSourceCatalogName("");
        dummyTrigger.setSourceSchemaName("");
        dummyTrigger.setSourceTableName("table");
        dummyTriggerList.add(dummyTrigger);
        expectedMatchingTriggers.clear();
        expectedMatchingTriggers.add(dummyTrigger);
        actualMatchingTriggers = spyTriggerRouterService.findMatchingTriggers(dummyTriggerList, "cat", "schem", "table");
        assertArrayEquals(expectedMatchingTriggers.toArray(), actualMatchingTriggers.toArray());
        dummyTrigger.setSourceCatalogName(null);
        dummyTrigger.setSourceSchemaName(null);
        actualMatchingTriggers = spyTriggerRouterService.findMatchingTriggers(dummyTriggerList, "cat", "schem", "table");
        assertArrayEquals(expectedMatchingTriggers.toArray(), actualMatchingTriggers.toArray());
    }

    @Test
    void testFindTriggerHistories() throws Exception {
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(platform.getDefaultCatalog()).thenReturn("cat");
        when(platform.getDefaultSchema()).thenReturn("schem");
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(parameterService.getInt("data.flush.jdbc.batch.size")).thenReturn(10);
        when(sqlTemplate.startSqlTransaction()).thenReturn(sqlTransaction);
        when(sqlTemplate.update(ArgumentMatchers.anyString(), (Object[]) ArgumentMatchers.any(), (int[]) ArgumentMatchers.any()))
                .thenReturn(1);
        when(parameterService.getString(ParameterConstants.RUNTIME_CONFIG_TRIGGER_PREFIX, tablePrefix)).thenReturn("table");
        when(parameterService.getNodeGroupId()).thenReturn("TestNodeGroup");
        when(parameterService.is(ParameterConstants.DB_METADATA_IGNORE_CASE)).thenReturn(true);
        List<TriggerHistory> expectedList = new ArrayList<TriggerHistory>();
        TriggerHistory hist = new TriggerHistory();
        hist.setSourceTableName("table");
        expectedList.add(hist);
        TriggerRouterService triggerRouterService = new TriggerRouterService(engine) {
            public List<TriggerHistory> getActiveTriggerHistories() {
                return expectedList;
            }
        };
        TriggerRouterService spyTriggerRouterService = spy(triggerRouterService);
        Collection<TriggerHistory> actualList = spyTriggerRouterService.findTriggerHistories(null, null, "table");
        assertArrayEquals(expectedList.toArray(), actualList.toArray());
        actualList = spyTriggerRouterService.findTriggerHistories(null, null, "TABLE");
        assertArrayEquals(expectedList.toArray(), actualList.toArray());
        actualList = spyTriggerRouterService.findTriggerHistories("cat", "schem", "TABLE");
        assertArrayEquals(expectedList.toArray(), actualList.toArray());
        actualList = spyTriggerRouterService.findTriggerHistories("cat", "schem", "table");
        assertArrayEquals(expectedList.toArray(), actualList.toArray());
        actualList = spyTriggerRouterService.findTriggerHistories("cat", "schem", "Table");
        assertNotEquals(expectedList.size(), actualList.size());
        actualList = spyTriggerRouterService.findTriggerHistories("cat", null, "table");
        assertArrayEquals(expectedList.toArray(), actualList.toArray());
        actualList = spyTriggerRouterService.findTriggerHistories("", "schem", "table");
        assertArrayEquals(expectedList.toArray(), actualList.toArray());
    }

    /**
     * Builds a spy of the service with the minimal mock wiring needed to exercise getTriggerToTableSupportingInfo without touching the database.
     * getTablesForTrigger and getTriggerName are stubbed per-test so that only this method's own logic -- the primary key decision and the supporting-info
     * assembly -- is exercised.
     */
    private TriggerRouterService createSpyService() {
        when(engine.getCacheManager()).thenReturn(cacheManager);
        when(engine.getClusterService()).thenReturn(clusterService);
        when(engine.getConfigurationService()).thenReturn(configurationService);
        when(engine.getStatisticManager()).thenReturn(statisticManager);
        when(engine.getGroupletService()).thenReturn(groupletService);
        when(engine.getNodeService()).thenReturn(nodeService);
        when(engine.getSequenceService()).thenReturn(sequenceService);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn(tablePrefix);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform().getSqlTemplate()).thenReturn(sqlTemplate);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplate);
        return spy(new TriggerRouterService(engine));
    }

    private Trigger newTrigger(String triggerId, String tableName) {
        Trigger trigger = new Trigger();
        trigger.setTriggerId(triggerId);
        trigger.setSourceTableName(tableName);
        return trigger;
    }

    @Test
    void testGetTriggerToTableSupportingInfo_noPrimaryKey_synthesizesPrimaryKeys() {
        TriggerRouterService service = createSpyService();
        Trigger trigger = newTrigger("test_table", "test_table");
        List<Trigger> triggers = new ArrayList<Trigger>();
        triggers.add(trigger);
        Table sourceTable = new Table("test_table");
        sourceTable.addColumn(new Column("col1"));
        sourceTable.addColumn(new Column("col2"));
        Set<Table> tables = new HashSet<Table>();
        tables.add(sourceTable);
        Table allColumnsPkTable = new Table("test_table");
        Column pk1 = new Column("col1");
        pk1.setPrimaryKey(true);
        Column pk2 = new Column("col2");
        pk2.setPrimaryKey(true);
        allColumnsPkTable.addColumn(pk1);
        allColumnsPkTable.addColumn(pk2);
        doReturn(tables).when(service).getTablesForTrigger(any(Trigger.class), any(), anyBoolean(), any());
        doReturn("TRIGGER_NAME").when(service).getTriggerName(any(DataEventType.class), anyInt(), any(Trigger.class),
                any(Table.class), any(), any(), any());
        when(symmetricDialect.getMaxTriggerNameLength()).thenReturn(30);
        when(platform.makeAllColumnsPrimaryKeys(any(Table.class))).thenReturn(allColumnsPkTable);
        Map<String, List<TriggerTableSupportingInfo>> result = service.getTriggerToTableSupportingInfo(triggers,
                new ArrayList<TriggerHistory>(), false, mock(TriggerRouterContext.class));
        // A table with no primary key among the synced columns gets a synthetic key, and that result is the table carried
        // in the supporting info. (Which table object is handed to makeAllColumnsPrimaryKeys is covered separately.)
        verify(platform, times(1)).makeAllColumnsPrimaryKeys(any(Table.class));
        List<TriggerTableSupportingInfo> infos = result.get("test_table");
        assertNotNull(infos);
        assertEquals(1, infos.size());
        assertSame(allColumnsPkTable, infos.get(0).getTable());
    }

    @Test
    void testGetTriggerToTableSupportingInfo_withPrimaryKey_doesNotSynthesizePrimaryKeys() {
        TriggerRouterService service = createSpyService();
        Trigger trigger = newTrigger("test_table", "test_table");
        List<Trigger> triggers = new ArrayList<Trigger>();
        triggers.add(trigger);
        Table sourceTable = new Table("test_table");
        Column idColumn = new Column("id");
        idColumn.setPrimaryKey(true);
        sourceTable.addColumn(idColumn);
        sourceTable.addColumn(new Column("name"));
        Set<Table> tables = new HashSet<Table>();
        tables.add(sourceTable);
        doReturn(tables).when(service).getTablesForTrigger(any(Trigger.class), any(), anyBoolean(), any());
        doReturn("TRIGGER_NAME").when(service).getTriggerName(any(DataEventType.class), anyInt(), any(Trigger.class),
                any(Table.class), any(), any(), any());
        when(symmetricDialect.getMaxTriggerNameLength()).thenReturn(30);
        Map<String, List<TriggerTableSupportingInfo>> result = service.getTriggerToTableSupportingInfo(triggers,
                new ArrayList<TriggerHistory>(), false, mock(TriggerRouterContext.class));
        // A real primary key is present among the synced columns, so no synthetic key is generated and the table is unchanged.
        verify(platform, never()).makeAllColumnsPrimaryKeys(any(Table.class));
        assertSame(sourceTable, result.get("test_table").get(0).getTable());
    }

    @Test
    void testGetTriggerToTableSupportingInfo_buildsSupportingInfoForEachTrigger() {
        TriggerRouterService service = createSpyService();
        Trigger trigger = newTrigger("test_table", "test_table");
        List<Trigger> triggers = new ArrayList<Trigger>();
        triggers.add(trigger);
        Table sourceTable = new Table("test_table");
        Column idColumn = new Column("id");
        idColumn.setPrimaryKey(true);
        sourceTable.addColumn(idColumn);
        Set<Table> tables = new HashSet<Table>();
        tables.add(sourceTable);
        doReturn(tables).when(service).getTablesForTrigger(any(Trigger.class), any(), anyBoolean(), any());
        doReturn("MY_TRIGGER").when(service).getTriggerName(any(DataEventType.class), anyInt(), any(Trigger.class),
                any(Table.class), any(), any(), any());
        when(symmetricDialect.getMaxTriggerNameLength()).thenReturn(30);
        Map<String, List<TriggerTableSupportingInfo>> result = service.getTriggerToTableSupportingInfo(triggers,
                new ArrayList<TriggerHistory>(), false, mock(TriggerRouterContext.class));
        assertEquals(1, result.size());
        List<TriggerTableSupportingInfo> infos = result.get("test_table");
        assertNotNull(infos);
        assertEquals(1, infos.size());
        TriggerTableSupportingInfo info = infos.get(0);
        assertEquals("test_table", info.getTriggerId());
        assertEquals("MY_TRIGGER", info.getInsertTriggerName());
        assertEquals("MY_TRIGGER", info.getUpdateTriggerName());
        assertEquals("MY_TRIGGER", info.getDeleteTriggerName());
        assertSame(sourceTable, info.getTable());
    }

    private Table buildTableWithUniqueIndexOnExcludedColumn() {
        Table sourceTable = new Table("test_table");
        sourceTable.addColumn(new Column("id"));
        sourceTable.addColumn(new Column("status"));
        UniqueIndex uniqueIndex = new UniqueIndex("status_uidx");
        uniqueIndex.addColumn(new IndexColumn("status"));
        sourceTable.addIndex(uniqueIndex);
        return sourceTable;
    }

    @Test
    void testGetTriggerToTableSupportingInfo_excludedUniqueIndexColumn_filtersColumnsBeforeSynthesizingPk() {
        TriggerRouterService service = createSpyService();
        Trigger trigger = newTrigger("test_table", "test_table");
        trigger.setExcludedColumnNames("status");
        List<Trigger> triggers = new ArrayList<Trigger>();
        triggers.add(trigger);
        Set<Table> tables = new HashSet<Table>();
        tables.add(buildTableWithUniqueIndexOnExcludedColumn());
        Table synthesized = new Table("test_table");
        Column synthesizedPk = new Column("id");
        synthesizedPk.setPrimaryKey(true);
        synthesized.addColumn(synthesizedPk);
        ArgumentCaptor<Table> captor = ArgumentCaptor.forClass(Table.class);
        doReturn(tables).when(service).getTablesForTrigger(any(Trigger.class), any(), anyBoolean(), any());
        doReturn("TRIGGER_NAME").when(service).getTriggerName(any(DataEventType.class), anyInt(), any(Trigger.class),
                any(Table.class), any(), any(), any());
        when(symmetricDialect.getMaxTriggerNameLength()).thenReturn(30);
        when(platform.makeAllColumnsPrimaryKeys(captor.capture())).thenReturn(synthesized);
        service.getTriggerToTableSupportingInfo(triggers, new ArrayList<TriggerHistory>(), false, mock(TriggerRouterContext.class));
        verify(platform, times(1)).makeAllColumnsPrimaryKeys(any(Table.class));
        Table passedToSynthesis = captor.getValue();
        assertNotNull(passedToSynthesis.getColumnWithName("id"));
        assertNull(passedToSynthesis.getColumnWithName("status"));
        assertEquals(0, passedToSynthesis.getUniqueIndices().length);
    }

    @Test
    void testGetTriggerToTableSupportingInfo_excludedUniqueIndexColumn_producesNonBlankPrimaryKey() {
        TriggerRouterService service = createSpyService();
        Trigger trigger = newTrigger("test_table", "test_table");
        trigger.setExcludedColumnNames("status");
        List<Trigger> triggers = new ArrayList<Trigger>();
        triggers.add(trigger);
        Set<Table> tables = new HashSet<Table>();
        tables.add(buildTableWithUniqueIndexOnExcludedColumn());
        doReturn(tables).when(service).getTablesForTrigger(any(Trigger.class), any(), anyBoolean(), any());
        doReturn("TRIGGER_NAME").when(service).getTriggerName(any(DataEventType.class), anyInt(), any(Trigger.class),
                any(Table.class), any(), any(), any());
        when(symmetricDialect.getMaxTriggerNameLength()).thenReturn(30);
        when(platform.makeAllColumnsPrimaryKeys(any(Table.class))).thenAnswer(invocation -> {
            Table passed = invocation.getArgument(0);
            Table result = passed.copy();
            IIndex[] uniqueIndices = result.getUniqueIndices();
            if (uniqueIndices != null && uniqueIndices.length > 0) {
                for (IndexColumn indexColumn : uniqueIndices[0].getColumns()) {
                    Column column = result.getColumnWithName(indexColumn.getName());
                    if (column != null) {
                        column.setPrimaryKey(true);
                    }
                }
            } else {
                for (Column column : result.getColumns()) {
                    column.setPrimaryKey(true);
                }
                result.setMadeAllColumnsPrimaryKey(true);
            }
            return result;
        });
        Map<String, List<TriggerTableSupportingInfo>> supportingInfo = service.getTriggerToTableSupportingInfo(triggers,
                new ArrayList<TriggerHistory>(), false, mock(TriggerRouterContext.class));
        Table resultTable = supportingInfo.get("test_table").get(0).getTable();
        Column[] pkColumns = trigger.filterExcludedAndIncludedColumns(trigger.getSyncKeysColumnsForTable(resultTable));
        String pkColumnNames = Table.getCommaDeliminatedColumns(pkColumns);
        assertFalse(pkColumnNames.isEmpty());
        assertEquals(1, pkColumns.length);
        assertEquals("id", pkColumns[0].getName());
    }
}
