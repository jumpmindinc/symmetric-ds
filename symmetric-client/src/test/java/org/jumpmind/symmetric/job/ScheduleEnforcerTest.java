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
    private static final long ONE_DAY_MS = 86400000L;
    private ScheduleEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new ScheduleEnforcer();
    }

    @Test
    void testIsPeriodicSchedule_withDigits() {
        assertTrue(enforcer.isPeriodicSchedule("60000"));
    }

    @Test
    void testIsPeriodicSchedule_withCron() {
        assertFalse(enforcer.isPeriodicSchedule("0/10 * * * * *"));
    }

    @Test
    void testIsPeriodicSchedule_withNull() {
        assertFalse(enforcer.isPeriodicSchedule(null));
    }

    @Test
    void testIsPeriodicSchedule_withEmpty() {
        assertFalse(enforcer.isPeriodicSchedule(""));
    }

    @Test
    void testIsCronSchedule_withCron() {
        assertTrue(enforcer.isCronSchedule("0/10 * * * * *"));
    }

    @Test
    void testIsCronSchedule_withDigits() {
        assertFalse(enforcer.isCronSchedule("60000"));
    }

    @Test
    void testIsCronSchedule_withNull() {
        assertFalse(enforcer.isCronSchedule(null));
    }

    @Test
    void testIsCronSchedule_withEmpty() {
        assertFalse(enforcer.isCronSchedule(""));
    }

    @Test
    void testGetPeriodicIntervalMs_withValidSchedule() {
        assertEquals(60000L, enforcer.getPeriodicIntervalMs("60000"));
    }

    @Test
    void testGetPeriodicIntervalMs_withCronSchedule() {
        assertEquals(-1L, enforcer.getPeriodicIntervalMs("0/10 * * * * *"));
    }

    @Test
    void testGetPeriodicIntervalMs_withNull() {
        assertEquals(-1L, enforcer.getPeriodicIntervalMs(null));
    }

    @Test
    void testGetCronIntervalMs_withEvery10Seconds() {
        long interval = enforcer.getCronIntervalMs("0/10 * * * * *");
        assertEquals(TEN_SECONDS_MS, interval);
    }

    @Test
    void testGetCronIntervalMs_withEveryHour() {
        long interval = enforcer.getCronIntervalMs("0 0 * * * *");
        assertEquals(ONE_HOUR_MS, interval);
    }

    @Test
    void testGetCronIntervalMs_withDailyAt1AM() {
        // Cron "0 0 1 * * *" runs at 1:00 AM every day = 24 hour interval
        long interval = enforcer.getCronIntervalMs("0 0 1 * * *");
        assertEquals(ONE_DAY_MS, interval);
    }

    @Test
    void testGetCronIntervalMs_withInvalidCron() {
        assertEquals(-1L, enforcer.getCronIntervalMs("invalid"));
    }

    @Test
    void testGetCronIntervalMs_withNull() {
        assertEquals(-1L, enforcer.getCronIntervalMs(null));
    }

    @Test
    void testGetIntervalMs_withPeriodicSchedule() {
        assertEquals(60000L, enforcer.getIntervalMs("60000"));
    }

    @Test
    void testGetIntervalMs_withCronSchedule() {
        assertEquals(TEN_SECONDS_MS, enforcer.getIntervalMs("0/10 * * * * *"));
    }

    @Test
    void testGetIntervalMs_withNull() {
        assertEquals(-1L, enforcer.getIntervalMs(null));
    }

    @Test
    void testEnforceMinimum_withPeriodicBelowMin() {
        String result = enforcer.enforceMinimum(String.valueOf(TEN_SECONDS_MS), ONE_HOUR_MS);
        assertEquals(String.valueOf(ONE_HOUR_MS), result);
    }

    @Test
    void testEnforceMinimum_withPeriodicAboveMin() {
        String result = enforcer.enforceMinimum(String.valueOf(TWO_HOURS_MS), ONE_HOUR_MS);
        assertEquals(String.valueOf(TWO_HOURS_MS), result);
    }

    @Test
    void testEnforceMinimum_withPeriodicEqualsMin() {
        String result = enforcer.enforceMinimum(String.valueOf(ONE_HOUR_MS), ONE_HOUR_MS);
        assertEquals(String.valueOf(ONE_HOUR_MS), result);
    }

    @Test
    void testEnforceMinimum_withNoMinimum() {
        String result = enforcer.enforceMinimum(String.valueOf(TEN_SECONDS_MS), 0);
        assertEquals(String.valueOf(TEN_SECONDS_MS), result);
    }

    @Test
    void testEnforceMinimum_withCronBelowMin() {
        // Cron every 10 seconds, minimum 1 hour
        String result = enforcer.enforceMinimum("0/10 * * * * *", ONE_HOUR_MS);
        assertEquals(String.valueOf(ONE_HOUR_MS), result);
    }

    @Test
    void testEnforceMinimum_withCronAboveMin() {
        // Cron every 2 hours, minimum 1 hour
        String result = enforcer.enforceMinimum("0 0 0/2 * * *", ONE_HOUR_MS);
        assertEquals("0 0 0/2 * * *", result);
    }

    @Test
    void testEnforceMinimum_withCronDailyAt1AM() {
        // Cron at 1:00 AM daily (24 hour interval), minimum 1 hour - should not be throttled
        String result = enforcer.enforceMinimum("0 0 1 * * *", ONE_HOUR_MS);
        assertEquals("0 0 1 * * *", result);
    }

    @Test
    void testEnforceMinimum_withCronNoMinimum() {
        String result = enforcer.enforceMinimum("0/10 * * * * *", 0);
        assertEquals("0/10 * * * * *", result);
    }

    @Test
    void testEnforceMinimum_withNullSchedule() {
        String result = enforcer.enforceMinimum(null, ONE_HOUR_MS);
        assertEquals(null, result);
    }

    @Test
    void testEnforceMinimum_withEmptySchedule() {
        String result = enforcer.enforceMinimum("", ONE_HOUR_MS);
        assertEquals("", result);
    }

    @Test
    void testExceedsScheduleLimit_withPeriodicBelowMin() {
        assertTrue(enforcer.exceedsScheduleLimit(String.valueOf(TEN_SECONDS_MS), ONE_HOUR_MS));
    }

    @Test
    void testExceedsScheduleLimit_withPeriodicAboveMin() {
        assertFalse(enforcer.exceedsScheduleLimit(String.valueOf(TWO_HOURS_MS), ONE_HOUR_MS));
    }

    @Test
    void testExceedsScheduleLimit_withCronBelowMin() {
        assertTrue(enforcer.exceedsScheduleLimit("0/10 * * * * *", ONE_HOUR_MS));
    }

    @Test
    void testExceedsScheduleLimit_withCronAboveMin() {
        assertFalse(enforcer.exceedsScheduleLimit("0 0 0/2 * * *", ONE_HOUR_MS));
    }

    @Test
    void testExceedsScheduleLimit_withCronDailyAt1AM() {
        // Cron at 1:00 AM daily (24 hour interval) should not exceed 1 hour minimum
        assertFalse(enforcer.exceedsScheduleLimit("0 0 1 * * *", ONE_HOUR_MS));
    }

    @Test
    void testExceedsScheduleLimit_withNoMinimum() {
        assertFalse(enforcer.exceedsScheduleLimit(String.valueOf(TEN_SECONDS_MS), 0));
    }

    @Test
    void testExceedsScheduleLimit_withNullSchedule() {
        assertFalse(enforcer.exceedsScheduleLimit(null, ONE_HOUR_MS));
    }
}
