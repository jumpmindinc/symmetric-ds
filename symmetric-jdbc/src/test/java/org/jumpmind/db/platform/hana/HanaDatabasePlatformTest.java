package org.jumpmind.db.platform.hana;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HanaDatabasePlatformTest {
    private HanaDatabasePlatform platform;
    private SqlTemplateSettings settings;
    private DataSource dataSource;

    @BeforeEach
    void setup() {
        dataSource = mock(DataSource.class);
        settings = mock(SqlTemplateSettings.class);
        platform = new HanaDatabasePlatform(dataSource, settings);
    }

    @Test
    void testGetName() {
        assertEquals(DatabaseNamesConstants.HANA, platform.getName());
    }

    @Test
    void testGetDefaultSchema() {
        assertNull(platform.getDefaultSchema());
    }

    @Test
    void testGetDefaultCatalog() {
        assertNull(platform.getDefaultCatalog());
    }

    @Test
    void testCreateDdlBuilder() {
        assertNotNull(platform.createDdlBuilder());
    }

    @Test
    void testCreateDdlReader() {
        assertNotNull(platform.createDdlReader());
    }

    @Test
    void testGetSqlTemplate() {
        ISqlTemplate result = platform.getSqlTemplate();
        assertNotNull(result);
        assertTrue(result instanceof HanaSqlJdbcSqlTemplate);
    }

    @Test
    void testGetSqlTemplateDirty() {
        ISqlTemplate result = platform.getSqlTemplateDirty();
        assertNotNull(result);
        assertTrue(result instanceof JdbcSqlTemplate);
        JdbcSqlTemplate resultAsJdbcSqlTemplate = (JdbcSqlTemplate) result;
        assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                resultAsJdbcSqlTemplate.getIsolationLevel());
    }

    @Test
    void testSupportsLimitOffset() {
        assertTrue(platform.supportsLimitOffset());
    }

    @Test
    void testMassageForLimitOffset_withoutTerminatingSemicolon() {
        int limit = -1;
        int offset = 0;
        String sql = "select id from employees";
        assertEquals(sql + " limit " + limit + " offset " + offset,
                platform.massageForLimitOffset(sql, limit, offset));
    }

    @Test
    void testMassageForLimitOffset_withTerminatingSemicolon() {
        int limit = -1;
        int offset = 0;
        String sql = "select id from employees;";
        assertEquals(sql.substring(0, sql.length() - 1) + " limit " + limit + " offset " + offset,
                platform.massageForLimitOffset(sql, limit, offset));
    }

    @Test
    void testMassageForLimitOffset_withOnlyTerminatingSemicolon() {
        int limit = 0100;
        int offset = -1;
        assertEquals(" limit " + limit + " offset " + offset,
                platform.massageForLimitOffset(";", limit, offset));
    }

    @Test
    void testCanColumnBeUsedInWhereClause() {
        assertTrue(platform.canColumnBeUsedInWhereClause(new Column()));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withDouble() {
        Column testColumn = new Column();
        testColumn.setJdbcTypeCode(Types.DOUBLE);
        assertFalse(platform.canColumnBeUsedInWhereClause(testColumn));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withFloat() {
        Column testColumn = new Column();
        testColumn.setJdbcTypeCode(Types.FLOAT);
        assertFalse(platform.canColumnBeUsedInWhereClause(testColumn));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withReal() {
        Column testColumn = new Column();
        testColumn.setJdbcTypeCode(Types.REAL);
        assertFalse(platform.canColumnBeUsedInWhereClause(testColumn));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withClob() {
        Column testColumn = new Column();
        testColumn.setMappedTypeCode(Types.CLOB);
        assertFalse(platform.canColumnBeUsedInWhereClause(testColumn));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withBlob() {
        Column testColumn = new Column();
        testColumn.setMappedTypeCode(Types.BLOB);
        assertFalse(platform.canColumnBeUsedInWhereClause(testColumn));
    }
}
