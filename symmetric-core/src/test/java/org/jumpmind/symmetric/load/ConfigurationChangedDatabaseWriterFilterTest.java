package org.jumpmind.symmetric.load;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ConfigurationChangedHelper;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigurationChangedDatabaseWriterFilterTest {

    private static final String SUFFIX = ConfigurationChangedHelper.class.getSimpleName();
    private static final String CTX_KEY_RESYNC_ALLOWED = "ResyncAllowed." + SUFFIX;
    private static final String CTX_KEY_RESYNC_NEEDED = "Resync." + SUFFIX;
    private ConfigurationChangedDatabaseWriterFilter filter;
    private ISymmetricEngine engine;
    private DataContext context;
    private ParameterService parameterService;
    private CsvData data;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(ParameterService.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(parameterService.getTablePrefix()).thenReturn("");
        filter = new ConfigurationChangedDatabaseWriterFilter(engine);
        context = new DataContext();
        data = new CsvData();
    }

    @Test
    void testAfterWriteForAlterSqlEvent() {
        String[] testData = new String[] {
                "INSERT INTO TEST VALUES (1);\n " + "ALTER TABLE TEST ADD NEW_COL VARCHAR(25);\n" + "INSERT INTO TEST VALUES (2);" };
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
        callAfterWriteWithSqlEvent(testData, true);
        assertEquals(true, context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testAfterWriteForCreateSqlEvent() {
        String[] testData = new String[] {
                "INSERT INTO OTHER VALUES (1);\n " + "CREATE TABLE TEST (ID int);\n" + "INSERT INTO ANOTHER VALUES (1);" };
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
        callAfterWriteWithSqlEvent(testData, true);
        assertEquals(true, context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testAfterWriteForSqlEventWithResyncAllowed() {
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
        callAfterWriteWithSqlEvent(new String[] { "CREATE TABLE TEST (ID int);" }, true);
        assertEquals(true, context.get(CTX_KEY_RESYNC_NEEDED));
    }

    @Test
    void testAfterWriteForSqlEventWithResyncNotAllowed() {
        callAfterWriteWithSqlEvent(new String[] { "CREATE TABLE TEST (ID int);" }, false);
        assertNull(context.get(CTX_KEY_RESYNC_NEEDED));
    }

    private void callAfterWriteWithSqlEvent(String[] parsedData, boolean isTriggerResyncAllowed) {
        context.put(CTX_KEY_RESYNC_ALLOWED, isTriggerResyncAllowed);
        data.putParsedData(CsvData.ROW_DATA, parsedData);
        data.setDataEventType(DataEventType.SQL);
        filter.afterWrite(context, null, data);
    }
}
