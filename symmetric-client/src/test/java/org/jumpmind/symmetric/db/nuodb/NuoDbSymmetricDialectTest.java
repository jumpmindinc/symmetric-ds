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
package org.jumpmind.symmetric.db.nuodb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NuoDbSymmetricDialectTest {
    private static final String TRIGGER_EXISTS_SQL = "select count(*) from system.triggers where triggername = ? and tablename = ?";
    private IParameterService parameterService;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        DatabaseInfo databaseInfo = new DatabaseInfo();
        IDdlBuilder ddlBuilder = mock(IDdlBuilder.class);
        when(ddlBuilder.getDatabaseInfo()).thenReturn(databaseInfo);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(platform.getDatabaseInfo()).thenReturn(databaseInfo);
    }

    @Test
    void doesTriggerExistOnPlatform_bindsSchemaName() {
        String schema = "s'1";
        when(sqlTemplate.queryForInt(TRIGGER_EXISTS_SQL + " and schema = ?", "SYM_ON_I", "item", schema)).thenReturn(1);
        NuoDbSymmetricDialect dialect = new NuoDbSymmetricDialect(parameterService, platform);
        assertTrue(dialect.doesTriggerExistOnPlatform(null, null, schema, "item", "SYM_ON_I"));
    }

    @Test
    void doesTriggerExistOnPlatform_omitsSchemaFilterWhenSchemaMissing() {
        when(sqlTemplate.queryForInt(TRIGGER_EXISTS_SQL, "SYM_ON_I", "item")).thenReturn(0);
        NuoDbSymmetricDialect dialect = new NuoDbSymmetricDialect(parameterService, platform);
        assertFalse(dialect.doesTriggerExistOnPlatform(null, null, null, "item", "SYM_ON_I"));
    }
}
