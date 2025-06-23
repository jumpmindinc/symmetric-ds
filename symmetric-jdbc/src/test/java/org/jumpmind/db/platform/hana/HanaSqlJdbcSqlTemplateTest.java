package org.jumpmind.db.platform.hana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HanaSqlJdbcSqlTemplateTest {
    private HanaSqlJdbcSqlTemplate template;
    private DataSource dataSource;
    private SqlTemplateSettings settings;

    @BeforeEach
    void setup() {
        dataSource = mock(DataSource.class);
        settings = mock(SqlTemplateSettings.class);
        template = new HanaSqlJdbcSqlTemplate(dataSource, settings, null, new DatabaseInfo());
    }

    @Test
    void testGetSelectLastInsertIdSql() {
        assertEquals("select current_identity_value() FROM dummy;", template.getSelectLastInsertIdSql(""));
    }

    @Test
    void testAllowsNullForIdentityColumn() {
        assertFalse(template.allowsNullForIdentityColumn());
    }

    @Test
    void testGetDatabaseProductName() {
        assertEquals(DatabaseNamesConstants.HANA, template.getDatabaseProductName());
    }
}
