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
package org.jumpmind.symmetric.observability.metrics;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Generic thread-safe map whose keys are derived from entry values via {@link #generateEntryKey}. Subclasses define the entry type and key strategy;
 * this class owns the backing store and common access operations.
 *
 * @param <E> entry type stored in the map
 */
public abstract class AbstractKeyedMetricsMap<E> {
    private final ConcurrentHashMap<String, E> entries = new ConcurrentHashMap<>();

    /** Returns the string key that identifies {@code entry} in the map. */
    protected abstract String generateEntryKey(E entry);

    /**
     * Returns the existing entry for {@code key}, or creates and stores a new one using {@code factory} if absent.
     * Uses {@link ConcurrentHashMap#computeIfAbsent} so the factory is called at most once per key.
     */
    protected E getOrCreate(String key, Supplier<E> factory) {
        return entries.computeIfAbsent(key, k -> factory.get());
    }

    /** Returns the entry for {@code key}, or empty if absent. */
    public Optional<E> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    /** Stores {@code entry} under the key returned by {@link #generateEntryKey}, replacing any existing entry. */
    public void put(E entry) {
        entries.put(generateEntryKey(entry), entry);
    }

    /** Returns an unmodifiable view of all entries. */
    public Collection<E> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** Returns an unmodifiable view of all keys. */
    public Set<String> keys() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    public void remove(String key) {
        entries.remove(key);
    }

    public int size() {
        return entries.size();
    }
}
