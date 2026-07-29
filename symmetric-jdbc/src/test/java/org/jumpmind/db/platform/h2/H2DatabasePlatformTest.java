package org.jumpmind.db.platform.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class H2DatabasePlatformTest {
    private DataSource dataSource;
    private Connection connection;
    private DatabaseMetaData metaData;
    private H2DatabasePlatform platform;
    private SqlTemplateSettings sqlTemplateSettings;
    private H2JdbcSqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseMajorVersion()).thenReturn(2);
        sqlTemplateSettings = mock(SqlTemplateSettings.class);
        sqlTemplate = mock(H2JdbcSqlTemplate.class);
        platform = new H2DatabasePlatform(dataSource, sqlTemplateSettings) {
            {
                this.sqlTemplate = mock(H2JdbcSqlTemplate.class);
            }
        };
    }

    @Test
    void testGetName() {
        assertEquals(DatabaseNamesConstants.H2, platform.getName());
    }

    @Test
    void testGetClassName() {
        assertEquals(H2DatabasePlatform.class.getName(), platform.getClassName());
    }

    @Test
    void testShutdown() {
        when(sqlTemplate.update("SHUTDOWN COMPACT")).thenReturn(0);
        platform.shutdown();
        verify(platform.getSqlTemplate(), times(1)).update("SHUTDOWN COMPACT");
    }
}
