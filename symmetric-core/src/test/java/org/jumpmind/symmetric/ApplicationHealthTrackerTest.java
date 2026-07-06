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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void getReadinessMapIsEmptyByDefault() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        Map<String, Boolean> readiness = tracker.getReadinessMap();
        assertNotNull(readiness);
        assertTrue(readiness.isEmpty());
    }

    @Test
    void getReadinessMapReturnsSameLiveMap() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        Map<String, Boolean> first = tracker.getReadinessMap();
        tracker.setEngineReadiness("engine-1", true);
        Map<String, Boolean> second = tracker.getReadinessMap();
        assertSame(first, second);
        assertEquals(1, second.size());
    }

    @Test
    void setEngineReadinessAddsEntryWithTrue() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        Map<String, Boolean> readiness = tracker.getReadinessMap();
        assertEquals(1, readiness.size());
        assertTrue(readiness.get("engine-1"));
    }

    @Test
    void setEngineReadinessAddsEntryWithFalse() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", false);
        assertFalse(tracker.getReadinessMap().get("engine-1"));
    }

    @Test
    void setEngineReadinessOverwritesExistingEntry() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-1", false);
        Map<String, Boolean> readiness = tracker.getReadinessMap();
        assertEquals(1, readiness.size());
        assertFalse(readiness.get("engine-1"));
    }

    @Test
    void setEngineReadinessTracksMultipleEnginesIndependently() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-2", false);
        tracker.setEngineReadiness("engine-3", true);
        Map<String, Boolean> readiness = tracker.getReadinessMap();
        assertEquals(3, readiness.size());
        assertTrue(readiness.get("engine-1"));
        assertFalse(readiness.get("engine-2"));
        assertTrue(readiness.get("engine-3"));
    }

    @Test
    void removeEngineRemovesExistingEntry() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.stopTrackingEngine("engine-1");
        assertTrue(tracker.getReadinessMap().isEmpty());
    }

    @Test
    void removeEngineLeavesOtherEnginesUntouched() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-2", false);
        tracker.stopTrackingEngine("engine-1");
        Map<String, Boolean> readiness = tracker.getReadinessMap();
        assertEquals(1, readiness.size());
        assertFalse(readiness.containsKey("engine-1"));
        assertFalse(readiness.get("engine-2"));
    }

    @Test
    void removeEngineIsNoOpForUnknownEngine() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.stopTrackingEngine("not-present");
        Map<String, Boolean> readiness = tracker.getReadinessMap();
        assertEquals(1, readiness.size());
        assertTrue(readiness.get("engine-1"));
    }

    @Test
    void removeEngineIsNoOpOnEmptyMap() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.stopTrackingEngine("engine-1");
        assertTrue(tracker.getReadinessMap().isEmpty());
    }

    @Test
    void isEngineReadyReturnsTrueForReadyEngine() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        assertTrue(tracker.isEngineReady("engine-1"));
    }

    @Test
    void isEngineReadyReturnsFalseForNotReadyEngine() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", false);
        assertFalse(tracker.isEngineReady("engine-1"));
    }

    @Test
    void isEngineReadyReflectsLatestUpdate() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        assertTrue(tracker.isEngineReady("engine-1"));
        tracker.setEngineReadiness("engine-1", false);
        assertFalse(tracker.isEngineReady("engine-1"));
    }

    @Test
    void isEngineReadyIsolatesEngines() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-2", false);
        assertTrue(tracker.isEngineReady("engine-1"));
        assertFalse(tracker.isEngineReady("engine-2"));
    }

    @Test
    void isReadyReturnsTrueWhenNoEnginesTracked() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        assertTrue(tracker.isReady());
    }

    @Test
    void isReadyReturnsTrueWhenAllEnginesReady() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-2", true);
        tracker.setEngineReadiness("engine-3", true);
        assertTrue(tracker.isReady());
    }

    @Test
    void isReadyReturnsFalseWhenAnyEngineNotReady() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-2", false);
        tracker.setEngineReadiness("engine-3", true);
        assertFalse(tracker.isReady());
    }

    @Test
    void isReadyReturnsFalseWhenAllEnginesNotReady() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", false);
        tracker.setEngineReadiness("engine-2", false);
        assertFalse(tracker.isReady());
    }

    @Test
    void isReadyFlipsBackToTrueAfterEngineRecovers() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-2", false);
        assertFalse(tracker.isReady());
        tracker.setEngineReadiness("engine-2", true);
        assertTrue(tracker.isReady());
    }

    @Test
    void isReadyReturnsTrueAfterRemovingTheOnlyNotReadyEngine() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-2", false);
        tracker.stopTrackingEngine("engine-2");
        assertTrue(tracker.isReady());
    }

    @Test
    void newTrackerIsAliveByDefault() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        assertTrue(tracker.isAlive());
    }

    @Test
    void setAliveRoundTripsBackToTrue() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setAlive(false);
        tracker.setAlive(true);
        assertTrue(tracker.isAlive());
    }

    @Test
    void setTrackerToNullClearsTracker() {
        ApplicationHealthTracker.setTracker(new ApplicationHealthTracker());
        ApplicationHealthTracker.setTracker(null);
        assertNull(ApplicationHealthTracker.getTracker());
    }

    @Test
    void setTrackerOverwritesPreviouslySetInstance() {
        ApplicationHealthTracker first = new ApplicationHealthTracker();
        ApplicationHealthTracker second = new ApplicationHealthTracker();
        ApplicationHealthTracker.setTracker(first);
        ApplicationHealthTracker.setTracker(second);
        assertSame(second, ApplicationHealthTracker.getTracker());
        assertNotSame(first, ApplicationHealthTracker.getTracker());
    }

    @Test
    void isEngineReadyThrowsForUnknownEngine() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        assertThrows(NullPointerException.class, () -> tracker.isEngineReady("unknown-engine"));
    }

    @Test
    void differentTrackerInstancesHaveIndependentReadinessMaps() {
        ApplicationHealthTracker first = new ApplicationHealthTracker();
        ApplicationHealthTracker second = new ApplicationHealthTracker();
        first.setEngineReadiness("engine-1", true);
        assertTrue(first.getReadinessMap().containsKey("engine-1"));
        assertTrue(second.getReadinessMap().isEmpty());
    }

    @Test
    void onShutdownSetsAliveToFalse() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.onShutdown();
        assertFalse(tracker.isAlive());
    }

    @Test
    void onShutdownWithNoTrackedEnginesStillSetsAliveFalse() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.onShutdown();
        assertFalse(tracker.isAlive());
        assertTrue(tracker.getReadinessMap().isEmpty());
    }

    @Test
    void onShutdownForcesAllTrackedEnginesToNotReady() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-2", true);
        tracker.onShutdown();
        assertFalse(tracker.isEngineReady("engine-1"));
        assertFalse(tracker.isEngineReady("engine-2"));
    }

    @Test
    void onShutdownPreservesEngineKeysAndOnlyFlipsTheirValue() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.setEngineReadiness("engine-2", false);
        tracker.onShutdown();
        Map<String, Boolean> readiness = tracker.getReadinessMap();
        assertEquals(2, readiness.size());
        assertTrue(readiness.containsKey("engine-1"));
        assertTrue(readiness.containsKey("engine-2"));
    }

    @Test
    void onShutdownMakesIsReadyFalseWhenEnginesWereTracked() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setEngineReadiness("engine-1", true);
        tracker.onShutdown();
        assertFalse(tracker.isReady());
    }

    @Test
    void onShutdownWithNoTrackedEnginesLeavesIsReadyTrue() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.onShutdown();
        assertTrue(tracker.isReady());
    }
}
