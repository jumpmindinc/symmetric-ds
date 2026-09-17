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
package org.jumpmind.symmetric.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.kafka.KafkaPlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.JdbcBatchBulkDatabaseWriter;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BulkDataLoaderFactoryTest {
    private BulkDataLoaderFactory factory;
    private ISymmetricEngine engine;
    private IParameterService parameterService;

    @BeforeEach
    void setUp() {
        parameterService = mock(IParameterService.class);
        when(parameterService.is(anyString())).thenReturn(false);
        when(parameterService.is(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(parameterService.getLong(anyString())).thenReturn(0L);
        when(parameterService.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(parameterService.getString(anyString())).thenReturn(null);
        when(parameterService.getString(anyString(), any())).thenAnswer(inv -> inv.getArgument(1));
        engine = mock(ISymmetricEngine.class);
        when(engine.getParameterService()).thenReturn(parameterService);
        ISymmetricDialect targetDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(targetDialect.getPlatform()).thenReturn(platform);
        when(engine.getTargetDialect()).thenReturn(targetDialect);
        factory = new BulkDataLoaderFactory();
        factory.setSymmetricEngine(engine);
    }

    @Test
    void testGetTypeName() {
        assertEquals("bulk", factory.getTypeName());
    }

    @Test
    void testGetDataWriter_withBatchOverrideEnabled_returnsJdbcBatchBulkDatabaseWriter() {
        when(parameterService.is(ParameterConstants.JDBC_EXECUTE_BULK_BATCH_OVERRIDE, false)).thenReturn(true);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform symmetricPlatform = mock(IDatabasePlatform.class);
        when(symmetricPlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(symmetricDialect.getPlatform()).thenReturn(symmetricPlatform);
        when(symmetricDialect.getTablePrefix()).thenReturn("sym_");
        IDataWriter writer = factory.getDataWriter(null, null, symmetricDialect, null, null, null, null, null);
        assertTrue(writer instanceof JdbcBatchBulkDatabaseWriter);
    }

    @Test
    void testGetDataWriter_withBatchOverrideDisabled_returnsJdbcBatchBulkDatabaseWriter() {
        when(parameterService.is(ParameterConstants.JDBC_EXECUTE_BULK_BATCH_OVERRIDE, false)).thenReturn(false);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform symmetricPlatform = mock(IDatabasePlatform.class);
        when(symmetricPlatform.getDatabaseInfo()).thenReturn(new DatabaseInfo());
        when(symmetricDialect.getPlatform()).thenReturn(symmetricPlatform);
        when(symmetricDialect.getTablePrefix()).thenReturn("sym_");
        IDataWriter writer = factory.getDataWriter(null, null, symmetricDialect, null, null, null, null, null);
        assertTrue(writer instanceof JdbcBatchBulkDatabaseWriter);
    }

    @Test
    void testIsPlatformSupported_withKafkaPlatform_returnsFalse() {
        assertFalse(factory.isPlatformSupported(mock(KafkaPlatform.class)));
    }

    @Test
    void testIsPlatformSupported_withOtherPlatform_returnsTrue() {
        assertTrue(factory.isPlatformSupported(mock(IDatabasePlatform.class)));
    }

    @Test
    void testSetSymmetricEngine_setsEngineAndParameterService() {
        ISymmetricEngine newEngine = mock(ISymmetricEngine.class);
        IParameterService newParameterService = mock(IParameterService.class);
        when(newEngine.getParameterService()).thenReturn(newParameterService);
        factory.setSymmetricEngine(newEngine);
        assertEquals(newEngine, factory.engine);
        verify(newEngine).getParameterService();
    }
}
