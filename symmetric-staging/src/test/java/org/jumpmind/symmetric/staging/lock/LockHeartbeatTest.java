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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jumpmind.symmetric.staging.api.IStagingLock;
import org.jumpmind.symmetric.staging.api.LockLostException;
import org.junit.jupiter.api.Test;

class LockHeartbeatTest {
    @Test
    void output_touchesAtRefreshInterval() throws IOException {
        TestLock lock = new TestLock(true);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        LockHeartbeatOutputStream out = new LockHeartbeatOutputStream(sink, lock, 1L);
        out.write("aaaa".getBytes());
        Thread.yield();
        try {
            Thread.sleep(5L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        out.write("bbbb".getBytes());
        out.close();
        assertEquals("aaaabbbb", sink.toString());
        assertTrue(lock.touchCount.get() >= 1, "expected at least one touch on a slow refresh interval");
    }

    @Test
    void output_throwsWhenLockLost() throws IOException {
        TestLock lock = new TestLock(false);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        LockHeartbeatOutputStream out = new LockHeartbeatOutputStream(sink, lock, 1L);
        try {
            Thread.sleep(5L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        assertThrows(LockLostException.class, () -> out.write("xxxx".getBytes()));
    }

    @Test
    void input_touchesDuringRead() throws IOException {
        TestLock lock = new TestLock(true);
        LockHeartbeatInputStream in = new LockHeartbeatInputStream(new ByteArrayInputStream("hello".getBytes()), lock, 1L);
        try {
            Thread.sleep(5L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        byte[] buf = new byte[5];
        int n = in.read(buf);
        in.close();
        assertEquals(5, n);
        assertTrue(lock.touchCount.get() >= 1);
    }

    @Test
    void input_throwsWhenLockLost() throws IOException {
        TestLock lock = new TestLock(false);
        LockHeartbeatInputStream in = new LockHeartbeatInputStream(new ByteArrayInputStream("hello".getBytes()), lock, 1L);
        try {
            Thread.sleep(5L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        assertThrows(LockLostException.class, () -> in.read(new byte[5]));
    }

    private static final class TestLock implements IStagingLock {
        final AtomicInteger touchCount = new AtomicInteger(0);
        final boolean valid;

        TestLock(boolean valid) {
            this.valid = valid;
        }

        @Override
        public boolean isAcquired() {
            return true;
        }

        @Override
        public String getFailureMessage() {
            return null;
        }

        @Override
        public long getAgeMs() {
            return 0L;
        }

        @Override
        public long getTtlMs() {
            return 30_000L;
        }

        @Override
        public void release() {
        }

        @Override
        public void breakLock() {
        }

        @Override
        public void touch() {
            touchCount.incrementAndGet();
        }

        @Override
        public boolean isStillValid() {
            return valid;
        }
    }
}
