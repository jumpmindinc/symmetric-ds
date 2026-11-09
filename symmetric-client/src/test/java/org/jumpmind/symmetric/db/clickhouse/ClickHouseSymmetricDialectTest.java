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
package org.jumpmind.symmetric.db.clickhouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.clickhouse.ClickHouseDdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClickHouseSymmetricDialectTest {
    private ClickHouseSymmetricDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new ClickHouseSymmetricDialect(createParameterService(), createPlatform());
    }

    @Test
    void testConstructor_createsTriggerTemplate() {
        assertNotNull(dialect.getTriggerTemplate());
    }

    @Test
    void testBinaryEncodingIsHex() {
        assertEquals(BinaryEncoding.HEX, dialect.getBinaryEncoding());
    }

    @Test
    void testCaptureIsNotSupported() {
        assertNull(dialect.getSyncTriggersExpression());
        assertFalse(dialect.doesTriggerExistOnPlatform(new StringBuilder(), null, null, "t", "trg"));
    }

    @Test
    void testRequiresAutoCommitFalseToSetFetchSize_isFalse() {
        assertFalse(dialect.requiresAutoCommitFalseToSetFetchSize());
    }

    private IParameterService createParameterService() {
        IParameterService parameterService = mock(ParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        return parameterService;
    }

    private IDatabasePlatform createPlatform() {
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.CLICKHOUSE);
        when(platform.getDdlBuilder()).thenReturn(new ClickHouseDdlBuilder());
        when(platform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        return platform;
    }
}
