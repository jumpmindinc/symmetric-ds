package org.jumpmind.db.platform.ase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ColumnTypes;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.util.BasicDataSourcePropertyConstants;
import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AseDatabasePlatformTest {
    private DataSource dataSource;
    private Connection connection;
    private DatabaseMetaData metaData;
    private AseDatabasePlatform platform;
    private SqlTemplateSettings sqlTemplateSettings;
    private String databaseUrl;
    private TypedProperties typedProperties;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        metaData = mock(DatabaseMetaData.class);
        databaseUrl = "jdbc\\:sybase\\:Tds\\:localhost\\:5000/symmetricroot";
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn(databaseUrl);
        when(metaData.getDatabaseMajorVersion()).thenReturn(2);
        sqlTemplateSettings = mock(SqlTemplateSettings.class);
        typedProperties = mock(TypedProperties.class);
        when(sqlTemplateSettings.getProperties()).thenReturn(typedProperties);
        when(typedProperties.get(BasicDataSourcePropertyConstants.DB_POOL_URL)).thenReturn(databaseUrl);
        platform = new AseDatabasePlatform(dataSource, sqlTemplateSettings);
    }

    @Test
    void testGetName() {
        assertEquals(DatabaseNamesConstants.ASE, platform.getName());
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withUnitext_shouldBeLob() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.LONGVARCHAR);
        column.setJdbcTypeName("unitext");
        boolean result = platform.canColumnBeUsedInWhereClause(column);
        assertFalse(result);
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withUnivarchar_shouldNotBeLob() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.VARCHAR);
        column.setJdbcTypeName("univarchar");
        boolean result = platform.canColumnBeUsedInWhereClause(column);
        assertTrue(result);
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withUnichar_shouldNotBeLob() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.CHAR);
        column.setJdbcTypeName("unichar");
        boolean result = platform.canColumnBeUsedInWhereClause(column);
        assertTrue(result);
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withLongNvarchar_shouldBeLob() {
        Column column = new Column();
        column.setJdbcTypeCode(ColumnTypes.LONGNVARCHAR);
        column.setJdbcTypeName("univarchar");
        boolean result = platform.canColumnBeUsedInWhereClause(column);
        assertFalse(result);
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withClobColumn() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.CLOB);
        assertFalse(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withBlobColumn() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.BLOB);
        assertFalse(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withBinaryColumn_treatBinaryAsLobTrue() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.BINARY);
        when(sqlTemplateSettings.isTreatBinaryAsLob()).thenReturn(true);
        assertFalse(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withBinaryColumn_treatBinaryAsLobFalse() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.BINARY);
        when(sqlTemplateSettings.isTreatBinaryAsLob()).thenReturn(false);
        assertTrue(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withFloatColumn() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.FLOAT);
        assertFalse(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withDoubleColumn() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.DOUBLE);
        assertFalse(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withRealColumn() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.REAL);
        assertFalse(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withIntegerColumn() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.INTEGER);
        assertTrue(platform.canColumnBeUsedInWhereClause(column));
    }

    @Test
    void testCanColumnBeUsedInWhereClause_withVarcharColumn() {
        Column column = new Column();
        column.setJdbcTypeCode(Types.VARCHAR);
        assertTrue(platform.canColumnBeUsedInWhereClause(column));
    }
}
