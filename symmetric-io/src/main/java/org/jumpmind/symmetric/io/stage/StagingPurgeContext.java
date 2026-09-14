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

import java.util.HashMap;
import java.util.Map;

public class StagingPurgeContext {
    private Map<String, Object> context = new HashMap<String, Object>();
    private long startTime;
    private long lastLogTime;
    private long purgedFileCount = 0;
    private long purgedFileSize = 0;
    private long purgedMemCount = 0;
    private long purgedMemSize = 0;

    public void incrementPurgedFileCount() {
        purgedFileCount++;
    }

    public void addPurgedFileBytes(long bytes) {
        this.purgedFileSize += bytes;
    }

    public void incrementPurgedMemoryCount() {
        purgedMemCount++;
    }

    public void addPurgedMemoryBytes(long bytes) {
        this.purgedMemCount += bytes;
    }

    public long getPurgedFileCount() {
        return purgedFileCount;
    }

    public void setPurgedFileCount(long purgedFileCount) {
        this.purgedFileCount = purgedFileCount;
    }

    public long getPurgedFileSize() {
        return purgedFileSize;
    }

    public void setPurgedFileSize(long purgedFileSize) {
        this.purgedFileSize = purgedFileSize;
    }

    public long getPurgedMemCount() {
        return purgedMemCount;
    }

    public void setPurgedMemCount(long purgedMemCount) {
        this.purgedMemCount = purgedMemCount;
    }

    public long getPurgedMemSize() {
        return purgedMemSize;
    }

    public void setPurgedMemSize(long purgedMemSize) {
        this.purgedMemSize = purgedMemSize;
    }

    public Object getContextValue(String key) {
        return context.get(key);
    }

    public Object putContextValue(String key, Object value) {
        return context.put(key, value);
    }

    public boolean getBoolean(String key) {
        return (Boolean) getContextValue(key);
    }

    public long getLong(String key) {
        return (Long) getContextValue(key);
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getLastLogTime() {
        return lastLogTime;
    }

    public void setLastLogTime(long lastLogTime) {
        this.lastLogTime = lastLogTime;
    }

    public boolean shouldLogStatus() {
        long now = System.currentTimeMillis();
        if (now - startTime > 60000
                && now - lastLogTime > 60000) {
            return true;
        } else {
            return false;
        }
    }
}
