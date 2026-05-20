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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;

public interface IStagedResource {
    public enum State {
        CREATE, DONE;

        public String getExtensionName() {
            return name().toLowerCase();
        }
    };

    public BufferedReader getReader();

    public BufferedWriter getWriter(long threshold); // supports large payloads

    public OutputStream getOutputStream();

    public OutputStream getOutputStream(boolean append);

    public InputStream getInputStream();

    public void close();

    public void closeReaders();

    public long getSize();

    public State getState();

    public String getPath();

    public void setState(State state);

    public long getLastUpdateTime();

    public void refreshLastUpdateTime();

    public boolean delete();

    public boolean exists();

    public boolean isInUse();

    public void dereference();

    public void reference();

    public boolean isMemoryOnlyResource();

    public boolean acquireLock(String serverInfo);

    public void copyTo(IStagedResource destination);

    public void setSmallContents(String contents); // only for small payloads

    public String getSmallContents();
    // copied from IStagingResourceLock

    long getLockAge();

    boolean isLockAcquired();

    String getLockFailureMessage();

    void setLockFailureMessage(String lockFailureMessage);

    void releaseLock();

    void breakLock();

    boolean lockExists();
}
