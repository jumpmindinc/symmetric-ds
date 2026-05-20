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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationHealthTrackerTest {
    @BeforeEach
    void resetTracker() {
        ApplicationHealthTracker.setTracker(null);
    }

    @Test
    void defaultsAliveAndNotReady() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        assertTrue(tracker.isAlive());
        assertFalse(tracker.isReady());
    }

    @Test
    void setAliveToFalse() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setAlive(false);
        assertFalse(tracker.isAlive());
    }

    @Test
    void setReadyToTrue() {
        ApplicationHealthTracker tracker = new ApplicationHealthTracker();
        tracker.setReady(true);
        assertTrue(tracker.isReady());
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
}
