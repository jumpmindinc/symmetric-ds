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
package org.jumpmind.symmetric.staging.fs;

import java.io.File;
import java.util.Iterator;

import org.apache.commons.io.FileUtils;
import org.jumpmind.symmetric.staging.api.IStagedResource;
import org.jumpmind.symmetric.staging.api.ResourceLocation;
import org.jumpmind.symmetric.staging.api.StagingConfig;
import org.jumpmind.symmetric.staging.api.StagingKey;
import org.jumpmind.symmetric.staging.api.StagingOptions;
import org.jumpmind.symmetric.staging.api.StorageKind;
import org.jumpmind.symmetric.staging.spi.AbstractStagingManager;
import org.jumpmind.symmetric.staging.spi.StorageBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemStagingManager extends AbstractStagingManager {
    private static final Logger fsLog = LoggerFactory.getLogger(FileSystemStagingManager.class);
    private final FileSystemBackend primaryBackend;
    private final FileSystemBackend scratchBackend;

    public FileSystemStagingManager(StagingConfig config) {
        super(config);
        if (config.getStagingDir() == null || config.getStagingDir().isBlank()) {
            throw new IllegalArgumentException("staging.dir is required for FILESYSTEM provider");
        }
        File primaryDir = new File(config.getStagingDir());
        this.primaryBackend = new FileSystemBackend(primaryDir);
        this.scratchBackend = new FileSystemBackend(new File(config.getScratchDir()));
        fsLog.info("FileSystemStagingManager initialized: primary={}, scratch={}",
                primaryDir.getAbsolutePath(), config.getScratchDir());
    }

    public ResourceLocation getPrimaryLocation() {
        return ResourceLocation.FILESYSTEM_PRIMARY;
    }

    @Override
    public StorageKind getStorageKind() {
        return StorageKind.FILESYSTEM;
    }

    @Override
    public boolean supportsRandomAccess() {
        return true;
    }

    @Override
    protected StorageBackend getBackend() {
        return primaryBackend;
    }

    @Override
    public IStagedResource find(StagingKey key) {
        IStagedResource cached = inUse.get(key);
        if (cached != null) {
            return cached;
        }
        if (primaryBackend.findExistingFile(key) != null) {
            FileSystemStagingResource resource = new FileSystemStagingResource(
                    key, config.defaultOptions(), primaryBackend, this, ResourceLocation.FILESYSTEM_PRIMARY);
            inUse.put(key, resource);
            return resource;
        }
        return null;
    }

    @Override
    public IStagedResource create(StagingOptions options, Object... path) {
        StagingKey key = new StagingKey(path);
        FileSystemStagingResource resource = new FileSystemStagingResource(
                key, options, primaryBackend, this, ResourceLocation.MEMORY);
        inUse.put(key, resource);
        return resource;
    }

    @Override
    public IStagedResource createScratchResource(StagingOptions options, Object... path) {
        StagingKey key = new StagingKey(path);
        FileSystemStagingResource resource = new FileSystemStagingResource(
                key, options, scratchBackend, this, ResourceLocation.FILESYSTEM_SCRATCH);
        inUse.put(key, resource);
        return resource;
    }

    @Override
    public long clean(long timeToLiveMs) {
        long now = System.currentTimeMillis();
        long deletedBytes = 0L;
        int deletedCount = 0;
        Iterator<StagingKey> iterator = listResources().iterator();
        while (iterator.hasNext()) {
            StagingKey key = iterator.next();
            IStagedResource resource = inUse.get(key);
            if (resource != null && resource.isInUse()) {
                continue;
            }
            File file = primaryBackend.findExistingFile(key);
            if (file == null) {
                continue;
            }
            long age = now - file.lastModified();
            if (age > timeToLiveMs) {
                long size = file.length();
                if (FileUtils.deleteQuietly(file)) {
                    deletedBytes += size;
                    deletedCount++;
                    inUse.remove(key);
                }
            }
        }
        if (deletedCount > 0) {
            fsLog.info("Cleaned {} staged resources, freed {} bytes", deletedCount, deletedBytes);
        }
        invalidateAvailabilityCache();
        return deletedBytes;
    }

    public File getStagingDirectory() {
        return primaryBackend.getRootDirectory();
    }
}
