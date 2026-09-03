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
package org.jumpmind.symmetric.db.mariadb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.mariadb.MariaDBDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.Test;

class MariaDBSymmetricDialectTest {
    @Test
    void constructor_disablesGeneratedAndPersistedColumnSupport_belowVersion52() {
        IDatabasePlatform platform = createPlatform("5.1.0-MariaDB");
        new MariaDBSymmetricDialect(createParameterService(), platform);
        assertFalse(platform.getDatabaseInfo().isGeneratedColumnsSupported());
        assertFalse(platform.getDatabaseInfo().isPersistedGeneratedColumnsSupported());
    }

    @Test
    void constructor_enablesGeneratedAndPersistedColumnSupport_atVersion52() {
        IDatabasePlatform platform = createPlatform("10.6.12-MariaDB");
        new MariaDBSymmetricDialect(createParameterService(), platform);
        assertTrue(platform.getDatabaseInfo().isGeneratedColumnsSupported());
        assertTrue(platform.getDatabaseInfo().isPersistedGeneratedColumnsSupported());
    }

    @Test
    void constructor_neverEnablesNonPersistedGeneratedColumnsIndexSupport() {
        IDatabasePlatform platform = createPlatform("10.6.12-MariaDB");
        new MariaDBSymmetricDialect(createParameterService(), platform);
        assertFalse(platform.getDatabaseInfo().isNonPersistedGeneratedColumnsIndexSupported());
    }

    private IParameterService createParameterService() {
        IParameterService parameterService = mock(ParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getString(DataSourceProperties.DB_POOL_URL)).thenReturn("jdbc:mariadb://localhost/test");
        return parameterService;
    }

    private IDatabasePlatform createPlatform(String productVersion) {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.getDatabaseProductVersion()).thenReturn(productVersion);
        when(sqlTemplate.queryForString(anyString())).thenReturn("InnoDB");
        when(platform.getName()).thenReturn(DatabaseNamesConstants.MARIADB);
        when(platform.getDdlBuilder()).thenReturn(new MariaDBDdlBuilder());
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        return platform;
    }
}
