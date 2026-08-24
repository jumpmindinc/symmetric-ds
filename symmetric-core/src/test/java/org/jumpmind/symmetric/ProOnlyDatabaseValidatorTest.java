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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.AbstractDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.DatabaseVersion;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.junit.jupiter.api.Test;

class ProOnlyDatabaseValidatorTest {
    @Test
    void testDedicatedOraclePlatform() {
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(createDedicatedPlatformMock("Oracle")).validate());
    }

    @Test
    void testDedicatedSqlServerPlatform() {
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(createDedicatedPlatformMock("Microsoft SQL Server")).validate());
    }

    @Test
    void testOracleOnGenericPlatform() {
        IDatabasePlatform platform = createGenericPlatformMock("Oracle");
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> new ProOnlyDatabaseValidator(platform).validate());
        assertTrue(ex.getMessage().contains("Oracle"));
    }

    @Test
    void testSqlServerOnGenericPlatform() {
        IDatabasePlatform platform = createGenericPlatformMock("Microsoft SQL Server");
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> new ProOnlyDatabaseValidator(platform).validate());
        assertTrue(ex.getMessage().contains("Microsoft SQL Server"));
    }

    @Test
    void testSupportedDatabaseOnGenericPlatform() {
        IDatabasePlatform platform = createGenericPlatformMock("H2");
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(platform).validate());
    }

    @Test
    void testNullDatabaseVersion() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getDatabaseVersion()).thenReturn(null);
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(platform).validate());
    }

    @Test
    void testNullDatabaseName() {
        IDatabasePlatform platform = createGenericPlatformMock(null);
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(platform).validate());
    }

    @Test
    void testAuroraPostgresFallenBackToGenericPostgresPlatform() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AURORA_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.POSTGRESQL95);
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> new ProOnlyDatabaseValidator(platform).validate());
        assertTrue(ex.getMessage().contains("Aurora"));
    }

    @Test
    void testDedicatedAuroraPostgresPlatform() {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.AURORA_POSTGRESQL);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AURORA_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(platform).validate());
    }

    @Test
    void testCloudSqlPostgresFallenBackToGenericPostgresPlatform() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.CLOUDSQL_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.POSTGRESQL95);
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> new ProOnlyDatabaseValidator(platform).validate());
        assertTrue(ex.getMessage().contains("Cloud SQL"));
    }

    @Test
    void testAzurePostgresFallenBackToGenericPostgresPlatform() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AZURE_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.POSTGRESQL95);
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> new ProOnlyDatabaseValidator(platform).validate());
        assertTrue(ex.getMessage().contains("Azure"));
    }

    @Test
    void testDedicatedAzurePostgresPlatform() {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.AZURE_POSTGRESQL);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AZURE_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(platform).validate());
    }

    @Test
    void testDedicatedCloudSqlPostgresPlatform() {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.CLOUDSQL_POSTGRESQL);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.CLOUDSQL_POSTGRESQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(platform).validate());
    }

    @Test
    void testAuroraMysqlFallenBackToGenericMysqlPlatform() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AURORA_MYSQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.MYSQL);
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> new ProOnlyDatabaseValidator(platform).validate());
        assertTrue(ex.getMessage().contains("Aurora"));
    }

    @Test
    void testDedicatedAuroraMysqlPlatform() {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.AURORA_MYSQL);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.AURORA_MYSQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(platform).validate());
    }

    @Test
    void testCloudSqlMySqlFallenBackToGenericMySqlPlatform() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.CLOUDSQL_MYSQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.MYSQL);
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> new ProOnlyDatabaseValidator(platform).validate());
        assertTrue(ex.getMessage().contains("Cloud SQL"));
    }

    @Test
    void testDedicatedCloudSqlMySqlPlatform() {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.CLOUDSQL_MYSQL);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(DatabaseNamesConstants.CLOUDSQL_MYSQL);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        assertDoesNotThrow(() -> new ProOnlyDatabaseValidator(platform).validate());
    }

    private IDatabasePlatform createGenericPlatformMock(String dbName) {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(dbName);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        return platform;
    }

    private AbstractDatabasePlatform createDedicatedPlatformMock(String dbName) {
        AbstractDatabasePlatform platform = mock(AbstractDatabasePlatform.class);
        when(platform.isDedicatedPlatform()).thenReturn(true);
        DatabaseVersion dbVersion = new DatabaseVersion();
        dbVersion.setName(dbName);
        when(platform.getDatabaseVersion()).thenReturn(dbVersion);
        return platform;
    }
}
