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
package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;

import org.jumpmind.db.sql.SqlException;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.DbHealthCheckResult;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.impl.ParameterService;
import org.jumpmind.symmetric.util.IDatabaseHealthTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AbstractSymmetricEngineTest {
    private AbstractSymmetricEngine engine;

    @BeforeEach
    public void setup() {
        engine = mock(AbstractSymmetricEngine.class, Mockito.CALLS_REAL_METHODS);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = AbstractSymmetricEngine.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(engine, value);
    }

    @Test
    void testDetectStartupDbParametersDifferentFromLastStart_hashMatchesReturnsFalseAndDoesNotSave() throws Exception {
        IContextService contextService = mock(IContextService.class);
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0x9250fa82);
        when(contextService.getString(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH)).thenReturn("0x9250fa82");
        setField("contextService", contextService);
        setField("parameterService", parameterService);
        assertFalse(engine.detectStartupDbParametersDifferentFromLastStart());
        verify(contextService, never()).save(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void testDetectStartupDbParametersDifferentFromLastStart_hashDiffersReturnsTrueAndSaves() throws Exception {
        IContextService contextService = mock(IContextService.class);
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0x9250fa82);
        when(contextService.getString(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH)).thenReturn("0xdeadbeef");
        setField("contextService", contextService);
        setField("parameterService", parameterService);
        assertTrue(engine.detectStartupDbParametersDifferentFromLastStart());
        verify(contextService).save(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH, "0x9250fa82");
    }

    @Test
    void testDetectStartupDbParametersDifferentFromLastStart_hashMissingReturnsTrueAndSaves() throws Exception {
        IContextService contextService = mock(IContextService.class);
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0x9250fa82);
        when(contextService.getString(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH)).thenReturn(null);
        setField("contextService", contextService);
        setField("parameterService", parameterService);
        assertTrue(engine.detectStartupDbParametersDifferentFromLastStart());
        verify(contextService).save(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH, "0x9250fa82");
    }

    @Test
    void testDetectStartupDbParametersDifferentFromLastStart_sqlExceptionReturnsTrueAndDoesNotSave() throws Exception {
        IContextService contextService = mock(IContextService.class);
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0x9250fa82);
        when(contextService.getString(ContextConstants.STARTUP_DB_OBJECTS_SETUP_HASH))
                .thenThrow(new SqlException("relation does not exist"));
        setField("contextService", contextService);
        setField("parameterService", parameterService);
        assertTrue(engine.detectStartupDbParametersDifferentFromLastStart());
        verify(contextService, never()).save(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void testComputeCurrentDbParamsHash_returnsHexFormattedString() throws Exception {
        ParameterService parameterService = mock(ParameterService.class);
        when(parameterService.hashParameterValues(ParameterConstants.STARTUP_DB_OBJECTS_SETUP_PARAMS)).thenReturn(0xec461721);
        setField("parameterService", parameterService);
        assertEquals("0xec461721", engine.computeCurrentDbParamsHash());
    }

    @Test
    void testIsRuntimeDbHealthy_noTracker_returnsTrue() {
        assertTrue(engine.isRuntimeDbHealthy());
        assertNull(engine.getLastDbHealthCheckResult());
    }

    @Test
    void testIsRuntimeDbHealthy_delegatesToTracker() throws Exception {
        IDatabaseHealthTracker databaseHealthTracker = mock(IDatabaseHealthTracker.class);
        when(databaseHealthTracker.isRuntimeDbHealthy()).thenReturn(false);
        setField("databaseHealthTracker", databaseHealthTracker);
        assertFalse(engine.isRuntimeDbHealthy());
    }

    @Test
    void testGetLastDbHealthCheckResult_delegatesToTracker() throws Exception {
        IDatabaseHealthTracker databaseHealthTracker = mock(IDatabaseHealthTracker.class);
        DbHealthCheckResult result = new DbHealthCheckResult(Instant.now(), true, "OK");
        when(databaseHealthTracker.getLastResult()).thenReturn(result);
        setField("databaseHealthTracker", databaseHealthTracker);
        assertEquals(result, engine.getLastDbHealthCheckResult());
    }
}
