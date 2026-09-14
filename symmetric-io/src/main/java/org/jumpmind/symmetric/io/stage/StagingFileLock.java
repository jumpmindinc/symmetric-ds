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
package org.jumpmind.symmetric.io.stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StagingFileLock {
    private static final Logger log = LoggerFactory.getLogger(StagingFileLock.class);
    boolean acquired = false;
    private File lockFile;
    private String lockFailureMessage;

    public long getLockAge() {
        if (lockFile != null) {
            FileTime lastModifiedTime;
            try {
                lastModifiedTime = Files.getLastModifiedTime(lockFile.toPath());
                long age = System.currentTimeMillis() - lastModifiedTime.toMillis();
                return age < 0 ? 42 : age;
            } catch (IOException ex) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to get last modified time for file " + lockFile, ex);
                }
                return 0;
            }
        } else {
            return 0;
        }
    }

    public boolean isAcquired() {
        return acquired;
    }

    public void setAcquired(boolean acquired) {
        this.acquired = acquired;
    }

    public File getLockFile() {
        return lockFile;
    }

    public void setLockFile(File lockFile) {
        this.lockFile = lockFile;
    }

    public String getLockFailureMessage() {
        return lockFailureMessage;
    }

    public void setLockFailureMessage(String lockFailureMessage) {
        this.lockFailureMessage = lockFailureMessage;
    }

    public void releaseLock() {
        int retries = 5;
        boolean ok = false;
        do {
            ok = lockFile.delete();
            if (!ok) {
                try {
                    Thread.sleep(1000);
                } catch (Exception ex) {
                    // no action.
                }
            }
        } while (!ok && retries-- > 0);
        if (ok) {
            log.debug("Lock {} released successfully.", lockFile);
        } else {
            boolean exists = lockFile.exists();
            log.warn("Failed to release lock {} exists={}", lockFile, exists);
        }
    }

    public void breakLock() {
        if (lockFile.delete()) {
            log.info("Lock {} broken successfully.", lockFile);
        } else {
            log.warn("Failed to break lock {}", lockFile);
        }
    }

    @Override
    public String toString() {
        return String.format("%s [%s]", super.toString(), lockFile);
    }
}
