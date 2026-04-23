package org.jumpmind.db.platform.postgresql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.jumpmind.db.sql.SqlException;
import org.junit.jupiter.api.Test;

class PostgreSqlDatabasePlatformTest {
    private final PostgreSqlDatabasePlatform platform = mock(PostgreSqlDatabasePlatform.class, CALLS_REAL_METHODS);

    @Test
    void testIsMissingCitextExtensionError_citextDirectMessage() {
        assertTrue(platform.isMissingCitextExtensionError(
                new SqlException("ERROR: type \"citext\" does not exist")));
    }

    @Test
    void testIsMissingCitextExtensionError_unrelatedError() {
        assertFalse(platform.isMissingCitextExtensionError(
                new SqlException("ERROR: relation \"users\" already exists")));
    }

    @Test
    void testIsMissingCitextExtensionError_nullMessage() {
        assertFalse(platform.isMissingCitextExtensionError(new RuntimeException()));
    }
}
