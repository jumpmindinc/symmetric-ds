package org.jumpmind.symmetric.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IAlterDatabaseInterceptor;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.DatabaseWriterSettings;
import org.jumpmind.symmetric.io.data.writer.DynamicDefaultDatabaseWriter;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterErrorHandler;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;
import org.jumpmind.symmetric.io.data.writer.ResolvedData;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
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
    // The Cassandra- and Kafka-platform branches of getDataWriter() are not covered here: CassandraPlatform
    // can't even be mocked (its bytecode references the Cassandra driver, which isn't on this module's test
    // classpath) and constructing a real KafkaWriter needs org.apache.avro, which is likewise absent. Exercising
    // those branches would require adding those platform driver dependencies to symmetric-core's test classpath.

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
}
