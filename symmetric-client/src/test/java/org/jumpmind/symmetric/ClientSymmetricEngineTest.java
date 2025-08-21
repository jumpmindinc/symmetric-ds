package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.sql.SqlTemplateSettings.JdbcLobHandling;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.junit.jupiter.api.Test;

public class ClientSymmetricEngineTest {
    @Test
    void testCreateSqlTemplateSettings_setJdbcLobHandling_noParameter() {
        SqlTemplateSettings settings = ClientSymmetricEngine.createSqlTemplateSettings(new TypedProperties());
        assertEquals(JdbcLobHandling.PLAIN.name(), settings.getJdbcLobHandling().name());
    }

    @Test
    void testCreateSqlTemplateSettings_setJdbcLobHandling_withParameter() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(ParameterConstants.DBDIALECT_ORACLE_JDBC_LOB_HANDLING, "streamlob");
        SqlTemplateSettings settings = ClientSymmetricEngine.createSqlTemplateSettings(properties);
        assertEquals(JdbcLobHandling.STREAMLOB.name(), settings.getJdbcLobHandling().name());
    }

    @Test
    void testCreateSqlTemplateSettings_setJdbcLobHandling_fixTurkeyLocaleIssue() {
        Locale.setDefault(new Locale("TR", "tr"));
        TypedProperties properties = new TypedProperties();
        properties.setProperty(ParameterConstants.DBDIALECT_ORACLE_JDBC_LOB_HANDLING, "plain");
        SqlTemplateSettings settings = ClientSymmetricEngine.createSqlTemplateSettings(properties);
        assertEquals("PLAIN", settings.getJdbcLobHandling().name());
        Locale.setDefault(Locale.US); // reset locale
    }
}
