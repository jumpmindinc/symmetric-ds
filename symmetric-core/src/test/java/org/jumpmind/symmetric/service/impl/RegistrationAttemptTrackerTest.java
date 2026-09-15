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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jumpmind.symmetric.service.impl.RegistrationAttemptTracker.RegistrationAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationAttemptTrackerTest {
    private static final String NODE_KEY = "11498-2";
    private static final String OTHER_NODE_KEY = "11498-3";
    private static final long MAX_AGE_MS = 30000;
    private static final long START_TIME_MS = 1000000;
    private RegistrationAttemptTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new RegistrationAttemptTracker();
    }

    @Test
    void startAcquiresKeyOnlyOnce() {
        assertTrue(tracker.start(NODE_KEY, MAX_AGE_MS, START_TIME_MS));
        assertFalse(tracker.start(NODE_KEY, MAX_AGE_MS, START_TIME_MS + 1));
        assertTrue(tracker.isInProgress(NODE_KEY));
        assertEquals(1, tracker.size());
    }

    @Test
    void startAllowsDifferentKeysConcurrently() {
        assertTrue(tracker.start(NODE_KEY, MAX_AGE_MS, START_TIME_MS));
        assertTrue(tracker.start(OTHER_NODE_KEY, MAX_AGE_MS, START_TIME_MS));
        assertEquals(2, tracker.size());
    }

    @Test
    void finishReleasesKey() {
        tracker.start(NODE_KEY, MAX_AGE_MS, START_TIME_MS);
        tracker.finish(NODE_KEY);
        assertFalse(tracker.isInProgress(NODE_KEY));
        assertTrue(tracker.start(NODE_KEY, MAX_AGE_MS, START_TIME_MS + 1));
    }

    @Test
    void startClearsAbandonedAttemptThatReachedMaxAge() {
        tracker.start(NODE_KEY, MAX_AGE_MS, START_TIME_MS);
        assertFalse(tracker.start(NODE_KEY, MAX_AGE_MS, START_TIME_MS + MAX_AGE_MS - 1));
        assertTrue(tracker.start(NODE_KEY, MAX_AGE_MS, START_TIME_MS + MAX_AGE_MS));
        assertEquals(1, tracker.size());
    }

    @Test
    void clearAbandonedReturnsOnlyExpiredAttempts() {
        tracker.start(NODE_KEY, MAX_AGE_MS, START_TIME_MS);
        tracker.start(OTHER_NODE_KEY, MAX_AGE_MS, START_TIME_MS + 1);
        List<RegistrationAttempt> abandoned = tracker.clearAbandoned(MAX_AGE_MS, START_TIME_MS + MAX_AGE_MS);
        assertEquals(1, abandoned.size());
        assertEquals(NODE_KEY, abandoned.get(0).registrationKey());
        assertEquals(START_TIME_MS, abandoned.get(0).attemptTimeMs());
        assertFalse(tracker.isInProgress(NODE_KEY));
        assertTrue(tracker.isInProgress(OTHER_NODE_KEY));
    }

    @Test
    void attemptExpiresOnceMaxAgeIsReached() {
        RegistrationAttempt attempt = new RegistrationAttempt(NODE_KEY, START_TIME_MS);
        assertFalse(attempt.isExpired(MAX_AGE_MS, START_TIME_MS + MAX_AGE_MS - 1));
        assertTrue(attempt.isExpired(MAX_AGE_MS, START_TIME_MS + MAX_AGE_MS));
    }

    @Test
    void attemptExpiresImmediatelyWhenMaxAgeIsZero() {
        RegistrationAttempt attempt = new RegistrationAttempt(NODE_KEY, START_TIME_MS);
        assertTrue(attempt.isExpired(0, START_TIME_MS));
    }
}
