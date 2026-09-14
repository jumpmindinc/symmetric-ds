/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.db.postgresql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.postgresql.PostgreSqlDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgreSqlSymmetricDialectTest {
    private PostgreSqlSymmetricDialect dialect;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setup() {
        IParameterService parameterService = mock(ParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.getDatabaseMajorVersion()).thenReturn(14);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(platform.getDdlBuilder()).thenReturn(new PostgreSqlDdlBuilder());
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        dialect = new PostgreSqlSymmetricDialect(parameterService, platform);
    }

    @Test
    void constructor_disablesPersistedAndNonPersistedGeneratedColumnSupport_belowVersion12() {
        setupWithMajorVersion(11);
        assertFalse(platform.getDatabaseInfo().isPersistedGeneratedColumnsSupported());
        assertFalse(platform.getDatabaseInfo().isNonPersistedGeneratedColumnsSupported());
    }

    @Test
    void constructor_enablesPersistedButNotNonPersistedGeneratedColumnSupport_atVersion14() {
        setupWithMajorVersion(14);
        assertTrue(platform.getDatabaseInfo().isPersistedGeneratedColumnsSupported());
        assertFalse(platform.getDatabaseInfo().isNonPersistedGeneratedColumnsSupported());
    }

    @Test
    void constructor_enablesPersistedAndNonPersistedGeneratedColumnSupport_atVersion18() {
        setupWithMajorVersion(18);
        assertTrue(platform.getDatabaseInfo().isPersistedGeneratedColumnsSupported());
        assertTrue(platform.getDatabaseInfo().isNonPersistedGeneratedColumnsSupported());
    }

    private void setupWithMajorVersion(int majorVersion) {
        IParameterService parameterService = mock(ParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.getDatabaseMajorVersion()).thenReturn(majorVersion);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(platform.getDdlBuilder()).thenReturn(new PostgreSqlDdlBuilder());
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        dialect = new PostgreSqlSymmetricDialect(parameterService, platform);
    }

    @Test
    void acquireDatabaseInstallLock_executesAdvisoryXactLockAndReturnsTransaction() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(transaction);
        ISqlTransaction result = dialect.acquireDatabaseInstallLock();
        assertSame(transaction, result);
        verify(transaction).execute(eq("select pg_advisory_xact_lock(" + PostgreSqlSymmetricDialect.DATABASE_INSTALL_LOCK_KEY + ")"));
        verify(transaction, never()).close();
    }

    @Test
    void acquireDatabaseInstallLock_closesTransactionAndRethrowsWhenLockExecuteFails() {
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        when(sqlTemplate.startSqlTransaction()).thenReturn(transaction);
        RuntimeException failure = new RuntimeException("could not acquire lock");
        when(transaction.execute(eq("select pg_advisory_xact_lock(" + PostgreSqlSymmetricDialect.DATABASE_INSTALL_LOCK_KEY + ")")))
                .thenThrow(failure);
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> dialect.acquireDatabaseInstallLock());
        assertSame(failure, thrown);
        verify(transaction).close();
    }
}
