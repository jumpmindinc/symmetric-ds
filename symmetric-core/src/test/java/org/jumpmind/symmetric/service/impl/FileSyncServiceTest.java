package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Types;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FileSyncServiceTest {
    private FileSyncService fileSyncService;
    private IParameterService parameterService;
    private ISqlTransaction sqlTransaction;
    private IDatabasePlatform platform;

    @BeforeEach
    void setUp() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        sqlTransaction = mock(ISqlTransaction.class);
        platform = mock(IDatabasePlatform.class);
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        ISymmetricDialect symmetricDialect = mock(AbstractSymmetricDialect.class);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        parameterService = mock(ParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        IExtensionService extensionService = mock(ExtensionService.class);
        ICacheManager cacheManager = mock(ICacheManager.class);
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getExtensionService()).thenReturn(extensionService);
        when(engine.getCacheManager()).thenReturn(cacheManager);
        fileSyncService = new FileSyncService(engine);
    }

    @Test
    void testSave_ntypesEnabled_usesNvarcharForFilePathColumns() {
        when(parameterService.is(ParameterConstants.MSSQL_USE_NTYPES_FOR_SYNC)).thenReturn(true);
        FileSnapshot snapshot = createTestSnapshot(LastEventType.CREATE);
        fileSyncService.save(sqlTransaction, snapshot);
        ArgumentCaptor<int[]> typesCaptor = ArgumentCaptor.forClass(int[].class);
        verify(sqlTransaction).prepareAndExecute(anyString(), any(Object[].class), typesCaptor.capture());
        int[] types = typesCaptor.getValue();
        // INSERT: 14 params — relative_dir at index 11, file_name at index 12
        assertArrayEquals(new int[] {
                Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.NVARCHAR, Types.NVARCHAR, Types.VARCHAR
        }, types, "INSERT types should use NVARCHAR for relative_dir (11) and file_name (12)");
    }

    @Test
    void testSave_ntypesDisabled_usesVarcharForFilePathColumns() {
        when(parameterService.is(ParameterConstants.MSSQL_USE_NTYPES_FOR_SYNC)).thenReturn(false);
        FileSnapshot snapshot = createTestSnapshot(LastEventType.CREATE);
        fileSyncService.save(sqlTransaction, snapshot);
        ArgumentCaptor<int[]> typesCaptor = ArgumentCaptor.forClass(int[].class);
        verify(sqlTransaction).prepareAndExecute(anyString(), any(Object[].class), typesCaptor.capture());
        int[] types = typesCaptor.getValue();
        assertArrayEquals(new int[] {
                Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC,
                Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR
        }, types, "INSERT types should use VARCHAR for all columns when ntypes disabled");
    }

    private FileSnapshot createTestSnapshot(LastEventType eventType) {
        FileSnapshot snapshot = new FileSnapshot();
        snapshot.setTriggerId("test-trigger");
        snapshot.setRouterId("test-router");
        snapshot.setRelativeDir(".");
        snapshot.setFileName("测试.txt");
        snapshot.setLastEventType(eventType);
        snapshot.setCrc32Checksum(12345L);
        snapshot.setFileSize(100L);
        snapshot.setFileModifiedTime(1000L);
        snapshot.setChannelId("filesync");
        snapshot.setReloadChannelId("reload");
        return snapshot;
    }
}
