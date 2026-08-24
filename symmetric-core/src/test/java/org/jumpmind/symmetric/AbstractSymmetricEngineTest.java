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
package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

import org.jumpmind.db.platform.AbstractDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.DatabaseVersion;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.DbHealthCheckResult;
import org.jumpmind.symmetric.model.StartupParameter.Source;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.jumpmind.symmetric.util.IDatabaseHealthTracker;
import org.jumpmind.symmetric.util.TypedPropertiesFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class AbstractSymmetricEngineTest {
    private AbstractSymmetricEngine engine;

    @BeforeEach
    public void setup() {
        engine = mock(AbstractSymmetricEngine.class, Mockito.CALLS_REAL_METHODS);
    }

    private void setPlatform(IDatabasePlatform platform) throws Exception {
        Field platformField = AbstractSymmetricEngine.class.getDeclaredField("platform");
        platformField.setAccessible(true);
        platformField.set(engine, platform);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = AbstractSymmetricEngine.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(engine, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Source> callFindKnownEnginePropertiesFileSources() throws Exception {
        Method method = AbstractSymmetricEngine.class.getDeclaredMethod("findKnownEnginePropertiesFileSources");
        method.setAccessible(true);
        return (Map<String, Source>) method.invoke(engine);
    }

    @Test
    void testFindKnownEnginePropertiesFileSources_typedPropertiesFactoryWithFile_tagsFileKeysAsEnginePropertiesFile(@TempDir File tempDir)
            throws Exception {
        File enginePropertiesFile = new File(tempDir, "engine.properties");
        Properties fileContents = new Properties();
        fileContents.setProperty("engine.name", "myengine");
        try (FileOutputStream out = new FileOutputStream(enginePropertiesFile)) {
            fileContents.store(out, null);
        }
        TypedPropertiesFactory propertiesFactory = new TypedPropertiesFactory();
        propertiesFactory.init(enginePropertiesFile, null);
        setField("propertiesFactory", propertiesFactory);
        Map<String, Source> knownFileSources = callFindKnownEnginePropertiesFileSources();
        assertEquals(Source.ENGINE_PROPERTIES_FILE, knownFileSources.get("engine.name"));
    }

    @Test
    void testFindKnownEnginePropertiesFileSources_typedPropertiesFactoryNoFile_returnsEmptyMap() throws Exception {
        TypedPropertiesFactory propertiesFactory = new TypedPropertiesFactory();
        propertiesFactory.init(null, null);
        setField("propertiesFactory", propertiesFactory);
        assertTrue(callFindKnownEnginePropertiesFileSources().isEmpty());
    }

    @Test
    void testFindKnownEnginePropertiesFileSources_typedPropertiesFactoryFileDoesNotExist_returnsEmptyMap(@TempDir File tempDir) throws Exception {
        TypedPropertiesFactory propertiesFactory = new TypedPropertiesFactory();
        propertiesFactory.init(new File(tempDir, "does-not-exist.properties"), null);
        setField("propertiesFactory", propertiesFactory);
        assertTrue(callFindKnownEnginePropertiesFileSources().isEmpty());
    }

    @Test
    void testFindKnownEnginePropertiesFileSources_nonTypedPropertiesFactoryImplementation_returnsEmptyMap() throws Exception {
        ITypedPropertiesFactory propertiesFactory = mock(ITypedPropertiesFactory.class);
        setField("propertiesFactory", propertiesFactory);
        assertTrue(callFindKnownEnginePropertiesFileSources().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testGetSupplementalStartupParameterMetaData_noProviderRegistered_returnsEmptyMap() throws Exception {
        Method method = AbstractSymmetricEngine.class.getDeclaredMethod("getSupplementalStartupParameterMetaData");
        method.setAccessible(true);
        Map<String, ParameterMetaData> metaData = (Map<String, ParameterMetaData>) method.invoke(engine);
        assertTrue(metaData.isEmpty());
    }

    @Test
    void testDetectStartupDbParametersDifferentFromLastStart_hashMatchesReturnsFalseAndDoesNotSave() throws Exception {
        IContextService contextService = mock(IContextService.class);
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0x9250fa82);
        when(contextService.getString(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH)).thenReturn("0x9250fa82");
        setField("contextService", contextService);
        setField("parameterService", parameterService);
        assertFalse(engine.detectStartupDbParametersDifferentFromLastStart());
        verify(contextService, never()).save(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void testDetectStartupDbParametersDifferentFromLastStart_hashDiffersReturnsTrueAndSaves() throws Exception {
        IContextService contextService = mock(IContextService.class);
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0x9250fa82);
        when(contextService.getString(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH)).thenReturn("0xdeadbeef");
        setField("contextService", contextService);
        setField("parameterService", parameterService);
        assertTrue(engine.detectStartupDbParametersDifferentFromLastStart());
        verify(contextService).save(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH, "0x9250fa82");
    }

    @Test
    void testDetectStartupDbParametersDifferentFromLastStart_hashMissingReturnsTrueAndSaves() throws Exception {
        IContextService contextService = mock(IContextService.class);
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0x9250fa82);
        when(contextService.getString(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH)).thenReturn(null);
        setField("contextService", contextService);
        setField("parameterService", parameterService);
        assertTrue(engine.detectStartupDbParametersDifferentFromLastStart());
        verify(contextService).save(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH, "0x9250fa82");
    }

    @Test
    void testDetectStartupDbParametersDifferentFromLastStart_sqlExceptionReturnsTrueAndDoesNotSave() throws Exception {
        IContextService contextService = mock(IContextService.class);
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0x9250fa82);
        when(contextService.getString(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH))
                .thenThrow(new SqlException("relation does not exist"));
        setField("contextService", contextService);
        setField("parameterService", parameterService);
        assertTrue(engine.detectStartupDbParametersDifferentFromLastStart());
        verify(contextService, never()).save(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void testComputeCurrentDbParamsHash_returnsHexFormattedString() throws Exception {
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0xec461721);
        setField("parameterService", parameterService);
        assertEquals("0xec461721", engine.computeCurrentDbParamsHash());
    }

    @Test
    void testIsRuntimeDbHealthy_noTracker_returnsTrue() {
        assertTrue(engine.isRuntimeDbHealthy());
        assertNull(engine.getLastDbHealthCheckResult());
    }

    @Test
    void testIsRuntimeDbHealthy_delegatesToTracker() throws Exception {
        IDatabaseHealthTracker databaseHealthTracker = mock(IDatabaseHealthTracker.class);
        when(databaseHealthTracker.isRuntimeDbHealthy()).thenReturn(false);
        setField("databaseHealthTracker", databaseHealthTracker);
        assertFalse(engine.isRuntimeDbHealthy());
    }

    @Test
    void testGetLastDbHealthCheckResult_delegatesToTracker() throws Exception {
        IDatabaseHealthTracker databaseHealthTracker = mock(IDatabaseHealthTracker.class);
        DbHealthCheckResult result = new DbHealthCheckResult(Instant.now(), true, "OK");
        when(databaseHealthTracker.getLastResult()).thenReturn(result);
        setField("databaseHealthTracker", databaseHealthTracker);
        assertEquals(result, engine.getLastDbHealthCheckResult());
    }

    @Test
    void testDedicatedOraclePlatform() throws Exception {
        setPlatform(createDedicatedPlatformMock("Oracle"));
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    void testDedicatedSqlServerPlatform() throws Exception {
        setPlatform(createDedicatedPlatformMock("Microsoft SQL Server"));
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    void testOracleOnGenericPlatform() throws Exception {
        setPlatform(createGenericPlatformMock("Oracle"));
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> engine.checkForProOnlyDatabase());
        assertTrue(ex.getMessage().contains("Oracle"));
    }

    @Test
    void testSqlServerOnGenericPlatform() throws Exception {
        setPlatform(createGenericPlatformMock("Microsoft SQL Server"));
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> engine.checkForProOnlyDatabase());
        assertTrue(ex.getMessage().contains("Microsoft SQL Server"));
    }

    @Test
    void testSupportedDatabaseOnGenericPlatform() throws Exception {
        setPlatform(createGenericPlatformMock("H2"));
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    void testNullDatabaseVersion() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getDatabaseVersion()).thenReturn(null);
        setPlatform(platform);
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    void testNullDatabaseName() throws Exception {
        setPlatform(createGenericPlatformMock(null));
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    void testAuroraPostgresFallenBackToGenericPostgresPlatform() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AURORA_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.POSTGRESQL95);
        setPlatform(platform);
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> engine.checkForProOnlyDatabase());
        assertTrue(ex.getMessage().contains("Aurora"));
    }

    @Test
    void testDedicatedAuroraPostgresPlatform() throws Exception {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.AURORA_POSTGRESQL);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AURORA_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        setPlatform(platform);
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    void testCloudSqlPostgresFallenBackToGenericPostgresPlatform() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.CLOUDSQL_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.POSTGRESQL95);
        setPlatform(platform);
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> engine.checkForProOnlyDatabase());
        assertTrue(ex.getMessage().contains("Cloud SQL"));
    }

    @Test
    void testDedicatedCloudSqlPostgresPlatform() throws Exception {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.CLOUDSQL_POSTGRESQL);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.CLOUDSQL_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        setPlatform(platform);
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    void testAuroraMysqlFallenBackToGenericMysqlPlatform() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AURORA_MYSQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.MYSQL);
        setPlatform(platform);
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> engine.checkForProOnlyDatabase());
        assertTrue(ex.getMessage().contains("Aurora"));
    }

    @Test
    void testDedicatedAuroraMysqlPlatform() throws Exception {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.AURORA_MYSQL);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AURORA_MYSQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        setPlatform(platform);
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    void testCloudSqlMySqlFallenBackToGenericMySqlPlatform() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.CLOUDSQL_MYSQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.MYSQL);
        setPlatform(platform);
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> engine.checkForProOnlyDatabase());
        assertTrue(ex.getMessage().contains("Cloud SQL"));
    }

    @Test
    void testDedicatedCloudSqlMySqlPlatform() throws Exception {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.CLOUDSQL_MYSQL);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.CLOUDSQL_MYSQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        setPlatform(platform);
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    /**
     * Creates a mock platform that simulates a generic (non-dedicated) platform with the given database name reported in its DatabaseVersion.
     */
    private IDatabasePlatform createGenericPlatformMock(String dbName) {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(dbName);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        return platform;
    }

    /**
     * Creates a mock platform that simulates a dedicated (Pro) platform with the given database name.
     */
    private AbstractDatabasePlatform createDedicatedPlatformMock(String dbName) {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(dbName);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        return platform;
    }
}
