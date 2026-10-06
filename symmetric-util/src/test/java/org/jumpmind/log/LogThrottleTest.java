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
package org.jumpmind.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jumpmind.log.LogThrottle.ThrottledLogMessageLevel;
import org.junit.jupiter.api.Test;

class LogThrottleTest {
    private static final long START_TIME = 1000;
    private static final long INFO_INTERVAL_MS = 30000;
    private static final long DEBUG_INTERVAL_MS = 5000;

    @Test
    void testNoneBeforeAnyIntervalWithDebugEnabled() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, true);
        assertEquals(ThrottledLogMessageLevel.NONE, throttle.getLevel(START_TIME + DEBUG_INTERVAL_MS));
    }

    @Test
    void testDebugAfterDebugIntervalWhenDebugEnabled() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, true);
        assertEquals(ThrottledLogMessageLevel.DEBUG, throttle.getLevel(START_TIME + DEBUG_INTERVAL_MS + 1));
    }

    @Test
    void testNoneAfterDebugIntervalWhenDebugDisabled() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, false);
        assertEquals(ThrottledLogMessageLevel.NONE, throttle.getLevel(START_TIME + DEBUG_INTERVAL_MS + 1));
    }

    @Test
    void testNoneAtInfoIntervalBoundaryWhenDebugDisabled() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, false);
        assertEquals(ThrottledLogMessageLevel.NONE, throttle.getLevel(START_TIME + INFO_INTERVAL_MS));
    }

    @Test
    void testInfoAfterInfoInterval() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, true);
        assertEquals(ThrottledLogMessageLevel.INFO, throttle.getLevel(START_TIME + INFO_INTERVAL_MS + 1));
    }

    @Test
    void testMarkDebugLoggedRestartsOnlyDebugInterval() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, true);
        long debugTime = START_TIME + DEBUG_INTERVAL_MS + 1;
        throttle.markLogged(ThrottledLogMessageLevel.DEBUG, debugTime);
        assertEquals(ThrottledLogMessageLevel.NONE, throttle.getLevel(debugTime + DEBUG_INTERVAL_MS));
        assertEquals(ThrottledLogMessageLevel.DEBUG, throttle.getLevel(debugTime + DEBUG_INTERVAL_MS + 1));
        assertEquals(ThrottledLogMessageLevel.INFO, throttle.getLevel(START_TIME + INFO_INTERVAL_MS + 1));
    }

    @Test
    void testMarkInfoLoggedRestartsBothIntervals() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, true);
        long infoTime = START_TIME + INFO_INTERVAL_MS + 1;
        throttle.markLogged(ThrottledLogMessageLevel.INFO, infoTime);
        assertEquals(ThrottledLogMessageLevel.NONE, throttle.getLevel(infoTime + DEBUG_INTERVAL_MS));
        assertEquals(ThrottledLogMessageLevel.DEBUG, throttle.getLevel(infoTime + DEBUG_INTERVAL_MS + 1));
        assertEquals(ThrottledLogMessageLevel.INFO, throttle.getLevel(infoTime + INFO_INTERVAL_MS + 1));
    }

    @Test
    void testMarkNoneLoggedChangesNothing() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, true);
        throttle.markLogged(ThrottledLogMessageLevel.NONE, START_TIME + INFO_INTERVAL_MS);
        assertEquals(ThrottledLogMessageLevel.INFO, throttle.getLevel(START_TIME + INFO_INTERVAL_MS + 1));
    }

    @Test
    void testForInfoOnlyIsDueOnlyAfterInfoInterval() {
        LogThrottle throttle = LogThrottle.forInfoOnly(START_TIME, INFO_INTERVAL_MS);
        assertEquals(false, throttle.isInfoDue(START_TIME + INFO_INTERVAL_MS));
        assertEquals(true, throttle.isInfoDue(START_TIME + INFO_INTERVAL_MS + 1));
        assertEquals(ThrottledLogMessageLevel.NONE, throttle.getLevel(START_TIME + INFO_INTERVAL_MS));
    }

    @Test
    void testIsDebugDueAfterDebugIntervalIncludingWhenInfoIsDue() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, true);
        assertEquals(false, throttle.isDebugDue(START_TIME + DEBUG_INTERVAL_MS));
        assertEquals(true, throttle.isDebugDue(START_TIME + DEBUG_INTERVAL_MS + 1));
        assertEquals(true, throttle.isDebugDue(START_TIME + INFO_INTERVAL_MS + 1));
    }

    @Test
    void testIsDebugDueFalseWhenDebugDisabled() {
        LogThrottle throttle = new LogThrottle(START_TIME, INFO_INTERVAL_MS, DEBUG_INTERVAL_MS, false);
        assertEquals(false, throttle.isDebugDue(START_TIME + DEBUG_INTERVAL_MS + 1));
    }
}
