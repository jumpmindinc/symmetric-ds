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
package org.jumpmind.symmetric.staging.lock;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.jumpmind.symmetric.staging.api.IStagingLock;
import org.jumpmind.symmetric.staging.api.LockLostException;

public class LockHeartbeatOutputStream extends FilterOutputStream {
    private final IStagingLock lock;
    private final long refreshIntervalMs;
    private long lastRefreshMs;

    public LockHeartbeatOutputStream(OutputStream delegate, IStagingLock lock) {
        this(delegate, lock, defaultRefreshInterval(lock));
    }

    public LockHeartbeatOutputStream(OutputStream delegate, IStagingLock lock, long refreshIntervalMs) {
        super(delegate);
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (lock == null) {
            throw new IllegalArgumentException("lock must not be null");
        }
        this.lock = lock;
        this.refreshIntervalMs = Math.max(1L, refreshIntervalMs);
        this.lastRefreshMs = System.currentTimeMillis();
    }

    static long defaultRefreshInterval(IStagingLock lock) {
        long ttl = lock.getTtlMs();
        return ttl <= 0 ? 60_000L : Math.max(1_000L, ttl / 3);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        maybeTouch();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        maybeTouch();
    }

    private void maybeTouch() throws IOException {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs < refreshIntervalMs) {
            return;
        }
        if (!lock.isStillValid()) {
            throw new LockLostException("Staging lock lost during write (another node took over)");
        }
        lock.touch();
        lastRefreshMs = now;
    }

    @Override
    public void close() throws IOException {
        try {
            flush();
        } catch (IOException ignored) {
        }
        super.close();
    }
}
