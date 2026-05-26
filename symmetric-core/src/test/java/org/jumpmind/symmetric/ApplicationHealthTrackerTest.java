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
package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationHealthTrackerTest {
    @BeforeEach
    void resetTracker() {
        ApplicationHealthTracker.setTracker(null);
    }

    @Test
    void setAliveToFalse() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setAlive(false);
        assertFalse(tracker.isAlive());
    }

    @Test
    void staticTrackerStartsNull() {
        assertNull(ApplicationHealthTracker.getTracker());
    }

    @Test
    void staticTrackerRoundTrip() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        ApplicationHealthTracker.setTracker(tracker);
        assertSame(tracker, ApplicationHealthTracker.getTracker());
        assertNotNull(ApplicationHealthTracker.getTracker());
    }

    @Test
    void getEngineReadinessIsEmptyByDefault() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        Map<String, Boolean> readiness = tracker.getEngineReadiness();
        assertNotNull(readiness);
        assertTrue(readiness.isEmpty());
    }

    @Test
    void getEngineReadinessReturnsSameLiveMap() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        Map<String, Boolean> first = tracker.getEngineReadiness();
        tracker.setEngineReady("engine-1", true);
        Map<String, Boolean> second = tracker.getEngineReadiness();
        assertSame(first, second);
        assertEquals(1, second.size());
    }

    @Test
    void setEngineReadyAddsEntryWithTrue() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReady("engine-1", true);
        Map<String, Boolean> readiness = tracker.getEngineReadiness();
        assertEquals(1, readiness.size());
        assertTrue(readiness.get("engine-1"));
    }

    @Test
    void setEngineReadyAddsEntryWithFalse() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReady("engine-1", false);
        assertFalse(tracker.getEngineReadiness().get("engine-1"));
    }

    @Test
    void setEngineReadyOverwritesExistingEntry() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReady("engine-1", true);
        tracker.setEngineReady("engine-1", false);
        Map<String, Boolean> readiness = tracker.getEngineReadiness();
        assertEquals(1, readiness.size());
        assertFalse(readiness.get("engine-1"));
    }

    @Test
    void setEngineReadyTracksMultipleEnginesIndependently() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReady("engine-1", true);
        tracker.setEngineReady("engine-2", false);
        tracker.setEngineReady("engine-3", true);
        Map<String, Boolean> readiness = tracker.getEngineReadiness();
        assertEquals(3, readiness.size());
        assertTrue(readiness.get("engine-1"));
        assertFalse(readiness.get("engine-2"));
        assertTrue(readiness.get("engine-3"));
    }

    @Test
    void removeEngineRemovesExistingEntry() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReady("engine-1", true);
        tracker.removeEngine("engine-1");
        assertTrue(tracker.getEngineReadiness().isEmpty());
    }

    @Test
    void removeEngineLeavesOtherEnginesUntouched() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReady("engine-1", true);
        tracker.setEngineReady("engine-2", false);
        tracker.removeEngine("engine-1");
        Map<String, Boolean> readiness = tracker.getEngineReadiness();
        assertEquals(1, readiness.size());
        assertFalse(readiness.containsKey("engine-1"));
        assertFalse(readiness.get("engine-2"));
    }

    @Test
    void removeEngineIsNoOpForUnknownEngine() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReady("engine-1", true);
        tracker.removeEngine("not-present");
        Map<String, Boolean> readiness = tracker.getEngineReadiness();
        assertEquals(1, readiness.size());
        assertTrue(readiness.get("engine-1"));
    }

    @Test
    void removeEngineIsNoOpOnEmptyMap() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.removeEngine("engine-1");
        assertTrue(tracker.getEngineReadiness().isEmpty());
    }
}
