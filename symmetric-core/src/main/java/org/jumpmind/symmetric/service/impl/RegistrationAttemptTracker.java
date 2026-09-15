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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks node registrations that are currently being processed so that only one registration per node runs at a time. Entries that were never finished, for
 * example because the thread died, are treated as abandoned once they are older than the maximum age and are cleared on the next start.
 */
public class RegistrationAttemptTracker {
    public record RegistrationAttempt(String registrationKey, long attemptTimeMs) {
        public boolean isExpired(long maxAgeMs, long nowMs) {
            return nowMs - attemptTimeMs >= maxAgeMs;
        }
    }

    private final ConcurrentMap<String, RegistrationAttempt> attempts = new ConcurrentHashMap<String, RegistrationAttempt>();

    public boolean start(String registrationKey, long maxAgeMs) {
        return start(registrationKey, maxAgeMs, System.currentTimeMillis());
    }

    public boolean start(String registrationKey, long maxAgeMs, long nowMs) {
        clearAbandoned(maxAgeMs, nowMs);
        return attempts.putIfAbsent(registrationKey, new RegistrationAttempt(registrationKey, nowMs)) == null;
    }

    public void finish(String registrationKey) {
        attempts.remove(registrationKey);
    }

    public boolean isInProgress(String registrationKey) {
        return attempts.containsKey(registrationKey);
    }

    public int size() {
        return attempts.size();
    }

    public List<RegistrationAttempt> clearAbandoned(long maxAgeMs, long nowMs) {
        List<RegistrationAttempt> abandoned = new ArrayList<RegistrationAttempt>();
        Iterator<Map.Entry<String, RegistrationAttempt>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            RegistrationAttempt attempt = iterator.next().getValue();
            if (attempt.isExpired(maxAgeMs, nowMs)) {
                iterator.remove();
                abandoned.add(attempt);
            }
        }
        return abandoned;
    }
}
