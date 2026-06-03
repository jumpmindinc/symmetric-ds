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
package org.jumpmind.symmetric.io.stage;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jumpmind.symmetric.staging.api.IStagingLock;
import org.jumpmind.symmetric.staging.api.IStreamCipherProvider;
import org.jumpmind.symmetric.staging.api.StagingKey;
import org.jumpmind.symmetric.staging.api.StagingOptions;

public class LegacyStagingManagerAdapter implements IStagingManager {
    public static final long DEFAULT_LOCK_TTL_MS = 300_000L;
    private final org.jumpmind.symmetric.staging.api.IStagingManager delegate;
    private final IStreamCipherProvider cipher;
    private final long lockTtlMs;
    private final boolean checksumEnabled;

    public LegacyStagingManagerAdapter(org.jumpmind.symmetric.staging.api.IStagingManager delegate) {
        this(delegate, null, DEFAULT_LOCK_TTL_MS, false);
    }

    public LegacyStagingManagerAdapter(org.jumpmind.symmetric.staging.api.IStagingManager delegate,
            IStreamCipherProvider cipher, long lockTtlMs) {
        this(delegate, cipher, lockTtlMs, false);
    }

    public LegacyStagingManagerAdapter(org.jumpmind.symmetric.staging.api.IStagingManager delegate,
            IStreamCipherProvider cipher, long lockTtlMs, boolean checksumEnabled) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
        this.cipher = cipher;
        this.lockTtlMs = lockTtlMs;
        this.checksumEnabled = checksumEnabled;
    }

    public org.jumpmind.symmetric.staging.api.IStagingManager getDelegate() {
        return delegate;
    }

    public IStreamCipherProvider getCipher() {
        return cipher;
    }

    public long getLockTtlMs() {
        return lockTtlMs;
    }

    @Override
    public IStagedResource find(Object... path) {
        return adapt(delegate.find(path));
    }

    @Override
    public IStagedResource find(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        return adapt(delegate.find(StagingKey.ofPath(path)));
    }

    @Override
    public IStagedResource create(Object... path) {
        return adapt(delegate.create(StagingOptions.defaults(), path));
    }

    @Override
    public IStagedResource createScratchResource(Object... path) {
        return adapt(delegate.createScratchResource(StagingOptions.plain(), path));
    }

    @Override
    public long clean(long timeToLiveInMs) {
        return delegate.clean(timeToLiveInMs);
    }

    @Override
    public Set<String> getResourceReferences() {
        Set<String> result = new LinkedHashSet<>();
        for (StagingKey key : delegate.listResources()) {
            result.add(key.asPath());
        }
        return result;
    }

    @Override
    public IStagingLock acquireFileLock(String serverInfo, Object... path) {
        return delegate.acquireLock(serverInfo, lockTtlMs, path);
    }

    @Override
    public File getStagingDirectory() {
        if (delegate instanceof org.jumpmind.symmetric.staging.fs.FileSystemStagingManager) {
            return ((org.jumpmind.symmetric.staging.fs.FileSystemStagingManager) delegate).getStagingDirectory();
        }
        return delegate.getScratchDirectory();
    }

    @Override
    public File getScratchDirectory() {
        return delegate.getScratchDirectory();
    }

    protected IStagedResource adapt(org.jumpmind.symmetric.staging.api.IStagedResource resource) {
        return resource == null ? null : new LegacyStagedResourceAdapter(resource, cipher, checksumEnabled);
    }
}
