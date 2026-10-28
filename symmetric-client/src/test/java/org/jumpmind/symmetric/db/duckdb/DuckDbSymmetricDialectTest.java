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
package org.jumpmind.symmetric.db.duckdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.duckdb.DuckDbDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DuckDbSymmetricDialectTest {
    private DuckDbSymmetricDialect dialect;
    private IParameterService parameterService;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setup() {
        parameterService = mock(ParameterService.class);
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(sqlTemplate.getDatabaseMajorVersion()).thenReturn(1);
        when(parameterService.getTablePrefix()).thenReturn("");
        when(platform.getDdlBuilder()).thenReturn(new DuckDbDdlBuilder());
        dialect = new DuckDbSymmetricDialect(parameterService, platform);
    }

    @Test
    void testGetTriggerTemplate() {
        assertInstanceOf(DuckDbTriggerTemplate.class, dialect.getTriggerTemplate());
    }

    @Test
    void testGetSyncTriggersExpression() {
        assertNull(dialect.getSyncTriggersExpression());
    }

    @Test
    void testGetBinaryEncoding() {
        assertEquals(BinaryEncoding.HEX, dialect.getBinaryEncoding());
    }

    @Test
    void testDoesTriggerExistOnPlatform() {
        assertFalse(dialect.doesTriggerExistOnPlatform(null, null, null, null, null));
    }
}
