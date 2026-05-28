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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import org.jumpmind.symmetric.staging.api.IStagingLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemStagingLock implements IStagingLock {
    private static final Logger log = LoggerFactory.getLogger(FileSystemStagingLock.class);
    private final File lockFile;
    private final String serverInfo;
    private final long ttlMs;
    private final long createdAtMs;
    private volatile boolean acquired;
    private volatile String failureMessage;

    public FileSystemStagingLock(File lockFile, String serverInfo, long ttlMs, boolean acquired) {
        this.lockFile = lockFile;
        this.serverInfo = serverInfo;
        this.ttlMs = ttlMs;
        this.acquired = acquired;
        this.createdAtMs = System.currentTimeMillis();
    }

    @Override
    public boolean isAcquired() {
        return acquired;
    }

    public void setAcquired(boolean acquired) {
        this.acquired = acquired;
    }

    @Override
    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public File getLockFile() {
        return lockFile;
    }

    public String getServerInfo() {
        return serverInfo;
    }

    @Override
    public long getAgeMs() {
        if (!lockFile.exists()) {
            return 0L;
        }
        try {
            FileTime modified = Files.getLastModifiedTime(lockFile.toPath());
            long age = System.currentTimeMillis() - modified.toMillis();
            return age < 0 ? 0L : age;
        } catch (IOException ex) {
            log.debug("Cannot read mtime for {}: {}", lockFile, ex.getMessage());
            return 0L;
        }
    }

    @Override
    public long getTtlMs() {
        return ttlMs;
    }

    @Override
    public void release() {
        if (!acquired) {
            return;
        }
        boolean ok = false;
        for (int attempt = 0; attempt < 5 && !ok; attempt++) {
            ok = lockFile.delete();
            if (!ok) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (ok) {
            acquired = false;
            log.debug("Released lock {}", lockFile);
        } else {
            log.warn("Failed to release lock {} exists={}", lockFile, lockFile.exists());
        }
    }

    @Override
    public void breakLock() {
        if (lockFile.delete()) {
            log.info("Broke lock {} successfully", lockFile);
        } else {
            log.warn("Failed to break lock {}", lockFile);
        }
        acquired = false;
    }

    @Override
    public void touch() {
        if (!acquired || !lockFile.exists()) {
            return;
        }
        try {
            Files.setLastModifiedTime(lockFile.toPath(), FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ex) {
            log.debug("Failed to touch lock {}: {}", lockFile, ex.getMessage());
        }
    }

    @Override
    public boolean isStillValid() {
        if (!acquired) {
            return false;
        }
        if (!lockFile.exists()) {
            return false;
        }
        return getAgeMs() <= ttlMs;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    @Override
    public String toString() {
        return "FileSystemStagingLock[" + lockFile + ", acquired=" + acquired + "]";
    }
}
