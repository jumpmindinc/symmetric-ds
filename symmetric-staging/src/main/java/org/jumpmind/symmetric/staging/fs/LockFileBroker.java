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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import org.apache.commons.io.FileUtils;
import org.jumpmind.symmetric.staging.api.IStagingLock;
import org.jumpmind.symmetric.staging.api.StagingKey;
import org.jumpmind.symmetric.staging.spi.LockBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockFileBroker implements LockBroker {
    static final String LOCK_EXTENSION = ".lock";
    private static final Logger log = LoggerFactory.getLogger(LockFileBroker.class);
    private final File rootDirectory;

    public LockFileBroker(File rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    @Override
    public IStagingLock tryAcquire(StagingKey key, String serverInfo, long ttlMs) {
        File lockFile = new File(rootDirectory, key.asPath() + LOCK_EXTENSION);
        File parent = lockFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        boolean acquired = false;
        try {
            acquired = lockFile.createNewFile();
            if (acquired) {
                FileUtils.write(lockFile, serverInfo, StandardCharsets.UTF_8, false);
            }
        } catch (IOException ex) {
            log.debug("Failed to acquire lock {}: {}", lockFile, ex.getMessage());
        }
        FileSystemStagingLock lock = new FileSystemStagingLock(lockFile, serverInfo, ttlMs, acquired);
        if (!acquired) {
            populateFailureMessage(lock);
        }
        return lock;
    }

    @Override
    public boolean breakIfExpired(StagingKey key, long ttlMs) {
        File lockFile = new File(rootDirectory, key.asPath() + LOCK_EXTENSION);
        if (!lockFile.exists()) {
            return false;
        }
        try {
            FileTime modified = Files.getLastModifiedTime(lockFile.toPath());
            long age = System.currentTimeMillis() - modified.toMillis();
            if (age > ttlMs) {
                boolean deleted = lockFile.delete();
                if (deleted) {
                    log.info("Broke expired lock {} (age={}ms ttl={}ms)", lockFile, age, ttlMs);
                }
                return deleted;
            }
        } catch (IOException ex) {
            log.warn("Cannot inspect lock {}: {}", lockFile, ex.getMessage());
        }
        return false;
    }

    private void populateFailureMessage(FileSystemStagingLock lock) {
        File lockFile = lock.getLockFile();
        if (lockFile.exists()) {
            try {
                String existing = FileUtils.readFileToString(lockFile, StandardCharsets.UTF_8);
                lock.setFailureMessage("Lock file exists: " + existing);
            } catch (IOException ex) {
                lock.setFailureMessage("Lock file exists but contents unreadable: " + ex.getMessage());
            }
        } else {
            lock.setFailureMessage("Lock file does not exist but could not be created. Check directory permissions.");
        }
    }
}
