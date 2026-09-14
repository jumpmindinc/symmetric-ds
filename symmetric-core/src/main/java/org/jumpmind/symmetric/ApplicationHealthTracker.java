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
package org.jumpmind.symmetric;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ApplicationHealthTracker implements IApplicationHealthTracker {
    private static final AtomicReference<IApplicationHealthTracker> tracker = new AtomicReference<>();
    private volatile boolean alive = true;
    private final Map<String, Boolean> engineReadiness = new ConcurrentHashMap<>();

    public static void setTracker(IApplicationHealthTracker tracker) {
        ApplicationHealthTracker.tracker.set(tracker);
    }

    public static IApplicationHealthTracker getTracker() {
        return tracker.get();
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    @Override
    public Map<String, Boolean> getReadinessMap() {
        return engineReadiness;
    }

    @Override
    public void setEngineReadiness(String engineName, boolean ready) {
        engineReadiness.put(engineName, ready);
    }

    @Override
    public void stopTrackingEngine(String engineName) {
        engineReadiness.remove(engineName);
    }

    @Override
    public boolean isEngineReady(String engineName) {
        return engineReadiness.get(engineName);
    }

    @Override
    public boolean isReady() {
        boolean ready = true;
        for (Entry<String, Boolean> engine : engineReadiness.entrySet()) {
            ready &= engine.getValue();
        }
        return ready;
    }

    @Override
    public void onShutdown() {
        alive = false;
        for (String engineName : engineReadiness.keySet()) {
            engineReadiness.put(engineName, false);
        }
    }
}
