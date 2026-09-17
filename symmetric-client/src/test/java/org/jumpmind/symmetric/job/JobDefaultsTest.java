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
package org.jumpmind.symmetric.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JobDefaultsTest {
    @Test
    void testNewInstance_hasExpectedDefaultValues() {
        JobDefaults defaults = new JobDefaults();
        assertNull(defaults.getSchedule());
        assertTrue(defaults.isRequiresRegisteration());
        assertNull(defaults.getDescription());
        assertTrue(defaults.isEnabled());
    }

    @Test
    void testSchedule_setsValueAndReturnsSelf() {
        JobDefaults defaults = new JobDefaults();
        JobDefaults result = defaults.schedule("60000");
        assertSame(defaults, result);
        assertEquals("60000", defaults.getSchedule());
    }

    @Test
    void testEnabled_setsValueAndReturnsSelf() {
        JobDefaults defaults = new JobDefaults();
        JobDefaults result = defaults.enabled(false);
        assertSame(defaults, result);
        assertEquals(false, defaults.isEnabled());
    }

    @Test
    void testRequiresRegisteration_setsValueAndReturnsSelf() {
        JobDefaults defaults = new JobDefaults();
        JobDefaults result = defaults.requiresRegisteration(false);
        assertSame(defaults, result);
        assertEquals(false, defaults.isRequiresRegisteration());
    }

    @Test
    void testDescription_setsValueAndReturnsSelf() {
        JobDefaults defaults = new JobDefaults();
        JobDefaults result = defaults.description("Purge incoming data");
        assertSame(defaults, result);
        assertEquals("Purge incoming data", defaults.getDescription());
    }

    @Test
    void testGetJobNameParameter_withNull_returnsNull() {
        assertNull(JobDefaults.getJobNameParameter(null));
    }

    @Test
    void testGetJobNameParameter_withMixedCaseAndSpaces_returnsLowerCaseDotted() {
        assertEquals("my.job", JobDefaults.getJobNameParameter("My Job"));
    }

    @Test
    void testGetStartParameter_returnsFormattedString() {
        assertEquals("start.my.job.job", JobDefaults.getStartParameter("My Job"));
    }

    @Test
    void testGetPeriodicParameter_returnsFormattedString() {
        assertEquals("job.my.job.period.time.ms", JobDefaults.getPeriodicParameter("My Job"));
    }

    @Test
    void testGetCronParameter_returnsFormattedString() {
        assertEquals("job.my.job.cron", JobDefaults.getCronParameter("My Job"));
    }

    @Test
    void testScheduleConstants_haveExpectedValues() {
        assertEquals("60000", JobDefaults.EVERY_MINUTE);
        assertEquals("3600000", JobDefaults.EVERY_HOUR);
        assertEquals("900000", JobDefaults.EVERY_FIFTEEN_MINUTES);
    }
}
