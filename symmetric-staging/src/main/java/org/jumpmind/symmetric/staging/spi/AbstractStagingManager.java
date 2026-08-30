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
package org.jumpmind.symmetric.staging.spi;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.symmetric.staging.api.IStagedResource;
import org.jumpmind.symmetric.staging.api.IStagingLock;
import org.jumpmind.symmetric.staging.api.IStagingManager;
import org.jumpmind.symmetric.staging.api.StagingConfig;
import org.jumpmind.symmetric.staging.api.StagingKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractStagingManager implements IStagingManager {
    private static final long AVAILABILITY_CACHE_TTL_MS = 5_000L;
    protected static final Logger log = LoggerFactory.getLogger(AbstractStagingManager.class);
    protected final StagingConfig config;
    protected final Path scratchDir;
    protected final long lowFreeSpaceBytes;
    protected final Map<StagingKey, IStagedResource> inUse = new ConcurrentHashMap<>();
    private volatile CachedAvailability cachedAvailability;

    protected AbstractStagingManager(StagingConfig config) {
        this.config = config;
        this.scratchDir = Paths.get(config.getScratchDir());
        this.lowFreeSpaceBytes = config.getLowFreeSpaceMegabytes() * 1024L * 1024L;
        ensureDirectoryExists(scratchDir.toFile());
    }

    @Override
    public final boolean isLocalStorageAvailable() {
        CachedAvailability snapshot = cachedAvailability;
        long now = System.currentTimeMillis();
        if (snapshot != null && now - snapshot.timestampMs < AVAILABILITY_CACHE_TTL_MS) {
            return snapshot.available;
        }
        return refreshAvailability(now).available;
    }

    @Override
    public final String getLocalStorageFailureReason() {
        CachedAvailability snapshot = cachedAvailability;
        if (snapshot == null) {
            snapshot = refreshAvailability(System.currentTimeMillis());
        }
        return snapshot.failureReason;
    }

    protected final void invalidateAvailabilityCache() {
        this.cachedAvailability = null;
    }

    private CachedAvailability refreshAvailability(long now) {
        File scratchFile = scratchDir.toFile();
        if (!scratchFile.exists() || !scratchFile.isDirectory()) {
            return store(new CachedAvailability(false,
                    "staging.scratch.dir does not exist: " + scratchFile.getAbsolutePath(), now));
        }
        if (!Files.isWritable(scratchDir)) {
            return store(new CachedAvailability(false,
                    "staging.scratch.dir is not writable: " + scratchFile.getAbsolutePath(), now));
        }
        long usable = scratchFile.getUsableSpace();
        if (lowFreeSpaceBytes > 0 && usable <= lowFreeSpaceBytes) {
            return store(new CachedAvailability(false,
                    "staging.scratch.dir free space " + usable + " bytes is at or below threshold "
                            + lowFreeSpaceBytes + " bytes", now));
        }
        return store(new CachedAvailability(true, null, now));
    }

    private CachedAvailability store(CachedAvailability snapshot) {
        this.cachedAvailability = snapshot;
        return snapshot;
    }

    protected static void ensureDirectoryExists(File directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            throw new IllegalStateException("Cannot create directory " + directory.getAbsolutePath());
        }
    }

    @Override
    public final IStagedResource find(Object... path) {
        return find(new StagingKey(path));
    }

    @Override
    public IStagingLock acquireLock(String serverInfo, long ttlMs, Object... path) {
        return getBackend().lockBroker().tryAcquire(new StagingKey(path), serverInfo, ttlMs);
    }

    @Override
    public boolean breakExpiredLock(long ttlMs, Object... path) {
        return getBackend().lockBroker().breakIfExpired(new StagingKey(path), ttlMs);
    }

    @Override
    public Set<StagingKey> listResources() {
        Set<StagingKey> keys = ConcurrentHashMap.newKeySet();
        for (StagingKey key : getBackend().list()) {
            keys.add(key);
        }
        return keys;
    }

    @Override
    public boolean verifyChecksum(StagingKey key) {
        IStagedResource resource = find(key);
        if (resource == null) {
            return true;
        }
        return org.jumpmind.symmetric.staging.checksum.ChecksumVerifier.verify(resource);
    }

    @Override
    public File getScratchDirectory() {
        return scratchDir.toFile();
    }

    public void removeResource(StagingKey key) {
        inUse.remove(key);
    }

    protected abstract StorageBackend getBackend();

    private static final class CachedAvailability {
        final boolean available;
        final String failureReason;
        final long timestampMs;

        CachedAvailability(boolean available, String failureReason, long timestampMs) {
            this.available = available;
            this.failureReason = failureReason;
            this.timestampMs = timestampMs;
        }
    }
}
