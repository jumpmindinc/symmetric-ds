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
package org.jumpmind.symmetric.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleEnforcerTest {
    private static final long ONE_HOUR_MS = 3600000L;
    private static final long TEN_SECONDS_MS = 10000L;
    private static final long TWO_HOURS_MS = 7200000L;
    private ScheduleEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new ScheduleEnforcer();
    }
    // isPeriodicSchedule tests

    @Test
    void testIsPeriodicSchedule_withDigits_returnsTrue() {
        assertTrue(enforcer.isPeriodicSchedule("60000"));
    }

    @Test
    void testIsPeriodicSchedule_withCron_returnsFalse() {
        assertFalse(enforcer.isPeriodicSchedule("0/10 * * * * *"));
    }

    @Test
    void testIsPeriodicSchedule_withNull_returnsFalse() {
        assertFalse(enforcer.isPeriodicSchedule(null));
    }

    @Test
    void testIsPeriodicSchedule_withEmpty_returnsFalse() {
        assertFalse(enforcer.isPeriodicSchedule(""));
    }
    // isCronSchedule tests

    @Test
    void testIsCronSchedule_withCron_returnsTrue() {
        assertTrue(enforcer.isCronSchedule("0/10 * * * * *"));
    }

    @Test
    void testIsCronSchedule_withDigits_returnsFalse() {
        assertFalse(enforcer.isCronSchedule("60000"));
    }

    @Test
    void testIsCronSchedule_withNull_returnsFalse() {
        assertFalse(enforcer.isCronSchedule(null));
    }

    @Test
    void testIsCronSchedule_withEmpty_returnsFalse() {
        assertFalse(enforcer.isCronSchedule(""));
    }
    // getPeriodicIntervalMs tests

    @Test
    void testGetPeriodicIntervalMs_withValidSchedule_returnsInterval() {
        assertEquals(60000L, enforcer.getPeriodicIntervalMs("60000"));
    }

    @Test
    void testGetPeriodicIntervalMs_withCronSchedule_returnsNegative() {
        assertEquals(-1L, enforcer.getPeriodicIntervalMs("0/10 * * * * *"));
    }

    @Test
    void testGetPeriodicIntervalMs_withNull_returnsNegative() {
        assertEquals(-1L, enforcer.getPeriodicIntervalMs(null));
    }
    // getCronIntervalMs tests

    @Test
    void testGetCronIntervalMs_every10Seconds_returns10000() {
        long interval = enforcer.getCronIntervalMs("0/10 * * * * *");
        assertEquals(TEN_SECONDS_MS, interval);
    }

    @Test
    void testGetCronIntervalMs_everyHour_returns3600000() {
        long interval = enforcer.getCronIntervalMs("0 0 * * * *");
        assertEquals(ONE_HOUR_MS, interval);
    }

    @Test
    void testGetCronIntervalMs_invalidCron_returnsNegative() {
        assertEquals(-1L, enforcer.getCronIntervalMs("invalid"));
    }

    @Test
    void testGetCronIntervalMs_null_returnsNegative() {
        assertEquals(-1L, enforcer.getCronIntervalMs(null));
    }
    // getIntervalMs tests

    @Test
    void testGetIntervalMs_periodicSchedule_returnsInterval() {
        assertEquals(60000L, enforcer.getIntervalMs("60000"));
    }

    @Test
    void testGetIntervalMs_cronSchedule_returnsInterval() {
        assertEquals(TEN_SECONDS_MS, enforcer.getIntervalMs("0/10 * * * * *"));
    }

    @Test
    void testGetIntervalMs_null_returnsNegative() {
        assertEquals(-1L, enforcer.getIntervalMs(null));
    }
    // enforceMinimum tests - periodic schedules

    @Test
    void testEnforceMinimum_periodicBelowMin_returnsMinimum() {
        String result = enforcer.enforceMinimum(String.valueOf(TEN_SECONDS_MS), ONE_HOUR_MS);
        assertEquals(String.valueOf(ONE_HOUR_MS), result);
    }

    @Test
    void testEnforceMinimum_periodicAboveMin_returnsOriginal() {
        String result = enforcer.enforceMinimum(String.valueOf(TWO_HOURS_MS), ONE_HOUR_MS);
        assertEquals(String.valueOf(TWO_HOURS_MS), result);
    }

    @Test
    void testEnforceMinimum_periodicEqualsMin_returnsOriginal() {
        String result = enforcer.enforceMinimum(String.valueOf(ONE_HOUR_MS), ONE_HOUR_MS);
        assertEquals(String.valueOf(ONE_HOUR_MS), result);
    }

    @Test
    void testEnforceMinimum_noMinimum_returnsOriginal() {
        String result = enforcer.enforceMinimum(String.valueOf(TEN_SECONDS_MS), 0);
        assertEquals(String.valueOf(TEN_SECONDS_MS), result);
    }
    // enforceMinimum tests - cron schedules

    @Test
    void testEnforceMinimum_cronBelowMin_returnsMinimum() {
        // Cron every 10 seconds, minimum 1 hour
        String result = enforcer.enforceMinimum("0/10 * * * * *", ONE_HOUR_MS);
        assertEquals(String.valueOf(ONE_HOUR_MS), result);
    }

    @Test
    void testEnforceMinimum_cronAboveMin_returnsOriginal() {
        // Cron every 2 hours, minimum 1 hour
        String result = enforcer.enforceMinimum("0 0 0/2 * * *", ONE_HOUR_MS);
        assertEquals("0 0 0/2 * * *", result);
    }

    @Test
    void testEnforceMinimum_cronNoMinimum_returnsOriginal() {
        String result = enforcer.enforceMinimum("0/10 * * * * *", 0);
        assertEquals("0/10 * * * * *", result);
    }
    // enforceMinimum tests - edge cases

    @Test
    void testEnforceMinimum_nullSchedule_returnsNull() {
        String result = enforcer.enforceMinimum(null, ONE_HOUR_MS);
        assertEquals(null, result);
    }

    @Test
    void testEnforceMinimum_emptySchedule_returnsEmpty() {
        String result = enforcer.enforceMinimum("", ONE_HOUR_MS);
        assertEquals("", result);
    }
    // wasEnforced tests

    @Test
    void testWasEnforced_periodicBelowMin_returnsTrue() {
        assertTrue(enforcer.wasEnforced(String.valueOf(TEN_SECONDS_MS), ONE_HOUR_MS));
    }

    @Test
    void testWasEnforced_periodicAboveMin_returnsFalse() {
        assertFalse(enforcer.wasEnforced(String.valueOf(TWO_HOURS_MS), ONE_HOUR_MS));
    }

    @Test
    void testWasEnforced_cronBelowMin_returnsTrue() {
        assertTrue(enforcer.wasEnforced("0/10 * * * * *", ONE_HOUR_MS));
    }

    @Test
    void testWasEnforced_cronAboveMin_returnsFalse() {
        assertFalse(enforcer.wasEnforced("0 0 0/2 * * *", ONE_HOUR_MS));
    }

    @Test
    void testWasEnforced_noMinimum_returnsFalse() {
        assertFalse(enforcer.wasEnforced(String.valueOf(TEN_SECONDS_MS), 0));
    }

    @Test
    void testWasEnforced_nullSchedule_returnsFalse() {
        assertFalse(enforcer.wasEnforced(null, ONE_HOUR_MS));
    }
}
