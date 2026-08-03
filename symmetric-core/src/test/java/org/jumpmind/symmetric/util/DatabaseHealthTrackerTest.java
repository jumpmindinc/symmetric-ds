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
package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.symmetric.ApplicationHealthTracker;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.DbHealthCheckResult;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseHealthTrackerTest {
    private static final String ENGINE_NAME = "test-engine";
    private static final int FAILURE_THRESHOLD = 3;
    private static final long UNHEALTHY_WAIT_MS = 30_000;
    private ISqlTemplate sqlTemplate;
    private IParameterService parameterService;
    private long[] currentTimeMs;
    private DatabaseHealthTracker tracker;

    @BeforeEach
    void setUp() {
        sqlTemplate = mock(ISqlTemplate.class);
        parameterService = mock(IParameterService.class);
        when(parameterService.is(ParameterConstants.DB_HEALTH_CHECK_ENABLED, true)).thenReturn(true);
        when(parameterService.getInt(ParameterConstants.DB_HEALTH_CHECK_FAILURE_THRESHOLD, 5)).thenReturn(FAILURE_THRESHOLD);
        when(parameterService.getLong(ParameterConstants.DB_HEALTH_CHECK_TIMEOUT_SECONDS, 60)).thenReturn(10L);
        when(parameterService.getEngineName()).thenReturn(ENGINE_NAME);
        currentTimeMs = new long[] { 0 };
        tracker = new DatabaseHealthTracker(() -> sqlTemplate, parameterService, () -> currentTimeMs[0]);
    }

    @AfterEach
    void tearDown() {
        ApplicationHealthTracker.setTracker(null);
    }

    private void failConnectionTests() {
        doThrow(new SqlException("connection refused")).when(sqlTemplate).testConnection();
    }

    private void passConnectionTests() {
        doNothing().when(sqlTemplate).testConnection();
    }

    private void declareUnhealthy() {
        failConnectionTests();
        for (int callIndex = 0; callIndex < FAILURE_THRESHOLD; callIndex++) {
            tracker.isRuntimeDbHealthy();
        }
    }

    @Test
    void isRuntimeDbHealthy_firstTestSucceeds_recordsHealthyResult() {
        assertTrue(tracker.isRuntimeDbHealthy());
        DbHealthCheckResult dbCheckResult = tracker.getLastResult();
        assertNotNull(dbCheckResult);
        assertNotNull(dbCheckResult.recorded());
        assertTrue(dbCheckResult.isHealthy());
        assertEquals("OK", dbCheckResult.result());
    }

    @Test
    void isRuntimeDbHealthy_whileHealthy_testsConnectionOnEveryCall() {
        assertTrue(tracker.isRuntimeDbHealthy());
        assertTrue(tracker.isRuntimeDbHealthy());
        verify(sqlTemplate, times(2)).testConnection();
    }

    @Test
    void isRuntimeDbHealthy_beforeFirstTest_lastResultIsNull() {
        assertNull(tracker.getLastResult());
    }

    @Test
    void isRuntimeDbHealthy_failuresBelowThreshold_reportsHealthyAndKeepsTesting() {
        failConnectionTests();
        assertTrue(tracker.isRuntimeDbHealthy());
        assertTrue(tracker.isRuntimeDbHealthy());
        verify(sqlTemplate, times(2)).testConnection();
        assertFalse(tracker.getLastResult().isHealthy());
    }

    @Test
    void isRuntimeDbHealthy_failuresReachThreshold_reportsUnhealthy() {
        failConnectionTests();
        assertTrue(tracker.isRuntimeDbHealthy());
        assertTrue(tracker.isRuntimeDbHealthy());
        assertFalse(tracker.isRuntimeDbHealthy());
    }

    @Test
    void isRuntimeDbHealthy_whileUnhealthy_waitsBeforeRetesting() {
        declareUnhealthy();
        verify(sqlTemplate, times(FAILURE_THRESHOLD)).testConnection();
        assertFalse(tracker.isRuntimeDbHealthy());
        currentTimeMs[0] = UNHEALTHY_WAIT_MS - 1;
        assertFalse(tracker.isRuntimeDbHealthy());
        verify(sqlTemplate, times(FAILURE_THRESHOLD)).testConnection();
        currentTimeMs[0] = UNHEALTHY_WAIT_MS;
        assertFalse(tracker.isRuntimeDbHealthy());
        verify(sqlTemplate, times(FAILURE_THRESHOLD + 1)).testConnection();
    }

    @Test
    void isRuntimeDbHealthy_recoveryFirstTestSucceedsSecondFails_staysUnhealthy() {
        declareUnhealthy();
        currentTimeMs[0] = UNHEALTHY_WAIT_MS;
        doNothing().doThrow(new SqlException("connection refused")).when(sqlTemplate).testConnection();
        assertFalse(tracker.isRuntimeDbHealthy());
        currentTimeMs[0] = UNHEALTHY_WAIT_MS * 2 - 1;
        assertFalse(tracker.isRuntimeDbHealthy());
        verify(sqlTemplate, times(FAILURE_THRESHOLD + 2)).testConnection();
    }

    @Test
    void isRuntimeDbHealthy_recoveryTwoConsecutiveSuccesses_reportsHealthy() {
        declareUnhealthy();
        currentTimeMs[0] = UNHEALTHY_WAIT_MS;
        passConnectionTests();
        assertTrue(tracker.isRuntimeDbHealthy());
        assertTrue(tracker.getLastResult().isHealthy());
    }

    @Test
    void isRuntimeDbHealthy_afterRecovery_failuresNeedFullThresholdAgain() {
        declareUnhealthy();
        currentTimeMs[0] = UNHEALTHY_WAIT_MS;
        passConnectionTests();
        assertTrue(tracker.isRuntimeDbHealthy());
        failConnectionTests();
        assertTrue(tracker.isRuntimeDbHealthy());
        assertTrue(tracker.isRuntimeDbHealthy());
        assertFalse(tracker.isRuntimeDbHealthy());
    }

    @Test
    void isRuntimeDbHealthy_checkDisabled_returnsTrueWithoutTesting() {
        when(parameterService.is(ParameterConstants.DB_HEALTH_CHECK_ENABLED, true)).thenReturn(false);
        assertTrue(tracker.isRuntimeDbHealthy());
        verify(sqlTemplate, never()).testConnection();
    }

    @Test
    void isRuntimeDbHealthy_testFails_recordsExceptionMessage() {
        failConnectionTests();
        tracker.isRuntimeDbHealthy();
        DbHealthCheckResult dbCheckResult = tracker.getLastResult();
        assertFalse(dbCheckResult.isHealthy());
        assertTrue(dbCheckResult.result().contains("connection refused"));
    }

    @Test
    void isRuntimeDbHealthy_timeoutParameterNotPositive_usesDefaultWait() {
        when(parameterService.getLong(ParameterConstants.DB_HEALTH_CHECK_TIMEOUT_SECONDS, 60)).thenReturn(0L);
        long defaultWaitMs = FAILURE_THRESHOLD * 60 * 1000;
        declareUnhealthy();
        currentTimeMs[0] = defaultWaitMs - 1;
        assertFalse(tracker.isRuntimeDbHealthy());
        verify(sqlTemplate, times(FAILURE_THRESHOLD)).testConnection();
        currentTimeMs[0] = defaultWaitMs;
        assertFalse(tracker.isRuntimeDbHealthy());
        verify(sqlTemplate, times(FAILURE_THRESHOLD + 1)).testConnection();
    }

    @Test
    void isRuntimeDbHealthy_declaredUnhealthy_reportsEngineNotReady() {
        ApplicationHealthTracker appHealthTracker = new ApplicationHealthTracker();
        appHealthTracker.setEngineReadiness(ENGINE_NAME, true);
        ApplicationHealthTracker.setTracker(appHealthTracker);
        declareUnhealthy();
        assertEquals(Boolean.FALSE, appHealthTracker.getReadinessMap().get(ENGINE_NAME));
    }

    @Test
    void isRuntimeDbHealthy_recovered_reportsEngineReady() {
        ApplicationHealthTracker appHealthTracker = new ApplicationHealthTracker();
        appHealthTracker.setEngineReadiness(ENGINE_NAME, true);
        ApplicationHealthTracker.setTracker(appHealthTracker);
        declareUnhealthy();
        currentTimeMs[0] = UNHEALTHY_WAIT_MS;
        passConnectionTests();
        assertTrue(tracker.isRuntimeDbHealthy());
        assertEquals(Boolean.TRUE, appHealthTracker.getReadinessMap().get(ENGINE_NAME));
    }

    @Test
    void isRuntimeDbHealthy_engineNotTracked_readinessMapUntouched() {
        ApplicationHealthTracker appHealthTracker = new ApplicationHealthTracker();
        ApplicationHealthTracker.setTracker(appHealthTracker);
        declareUnhealthy();
        assertTrue(appHealthTracker.getReadinessMap().isEmpty());
    }

    @Test
    void isRuntimeDbHealthy_engineRemovedDuringOutage_recoveryDoesNotReAdd() {
        ApplicationHealthTracker appHealthTracker = new ApplicationHealthTracker();
        appHealthTracker.setEngineReadiness(ENGINE_NAME, true);
        ApplicationHealthTracker.setTracker(appHealthTracker);
        declareUnhealthy();
        appHealthTracker.stopTrackingEngine(ENGINE_NAME);
        currentTimeMs[0] = UNHEALTHY_WAIT_MS;
        passConnectionTests();
        assertTrue(tracker.isRuntimeDbHealthy());
        assertTrue(appHealthTracker.getReadinessMap().isEmpty());
    }
}
