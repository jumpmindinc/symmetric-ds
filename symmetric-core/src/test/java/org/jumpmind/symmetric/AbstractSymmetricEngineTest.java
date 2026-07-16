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

import java.lang.reflect.Field;

import org.jumpmind.db.platform.AbstractDatabasePlatform;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.DatabaseVersion;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    @Test
    public void testDedicatedOraclePlatform() throws Exception {
        setPlatform(createDedicatedPlatformMock("Oracle"));
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    public void testDedicatedSqlServerPlatform() throws Exception {
        setPlatform(createDedicatedPlatformMock("Microsoft SQL Server"));
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    public void testOracleOnGenericPlatform() throws Exception {
        setPlatform(createGenericPlatformMock("Oracle"));
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> engine.checkForProOnlyDatabase());
        assertTrue(ex.getMessage().contains("Oracle"));
    }

    @Test
    public void testSqlServerOnGenericPlatform() throws Exception {
        setPlatform(createGenericPlatformMock("Microsoft SQL Server"));
        SymmetricException ex = assertThrows(SymmetricException.class,
                () -> engine.checkForProOnlyDatabase());
        assertTrue(ex.getMessage().contains("Microsoft SQL Server"));
    }

    @Test
    public void testSupportedDatabaseOnGenericPlatform() throws Exception {
        setPlatform(createGenericPlatformMock("H2"));
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    public void testNullDatabaseVersion() throws Exception {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getDatabaseVersion()).thenReturn(null);
        setPlatform(platform);
        assertDoesNotThrow(() -> engine.checkForProOnlyDatabase());
    }

    @Test
    public void testNullDatabaseName() throws Exception {
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
