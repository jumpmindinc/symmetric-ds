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
package org.jumpmind.symmetric.observability.repository;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffer of long surrogate keys for quick allocation of several keys at once
 */
public class SurrogateLongKeyBuffer {
    public static final long SURROGATE_KEY_UNASSIGNED = -1l;
    public static final long SURROGATE_KEY_BUFFER_SIZE = 10l;
    private long start = SURROGATE_KEY_UNASSIGNED;
    private long end = SURROGATE_KEY_UNASSIGNED;
    private final AtomicLong nextKey = new AtomicLong(SURROGATE_KEY_UNASSIGNED);

    public SurrogateLongKeyBuffer() {
    }

    public SurrogateLongKeyBuffer(long start) {
        this.start = start;
        this.end = start + SURROGATE_KEY_BUFFER_SIZE - 1;
        this.nextKey.set(start);
    }

    public SurrogateLongKeyBuffer(long start, long nextKey) {
        moveTo(start, nextKey);
    }

    public long capacity() {
        return SURROGATE_KEY_BUFFER_SIZE;
    }

    public static long roundDownToBufferStart(long value) {
        return SurrogateLongKeyBuffer.SURROGATE_KEY_BUFFER_SIZE * ( value / SurrogateLongKeyBuffer.SURROGATE_KEY_BUFFER_SIZE);
    }

    public static long roundUpToNextBufferStart(long value) {
        return SurrogateLongKeyBuffer.SURROGATE_KEY_BUFFER_SIZE - value % SurrogateLongKeyBuffer.SURROGATE_KEY_BUFFER_SIZE;
    }


    public long size() {
        return this.end + 1 - this.nextKey.get();
    }

    /**
     * Must check isAvailable() before using value from this method!
     */
    public long getNextValue() {
        long currentValue = this.nextKey.get();
        if (currentValue == SURROGATE_KEY_UNASSIGNED) {
            throw new ExceptionInInitializerError("Surrogate keys buffer has not been initialized");
        }
        if (currentValue > this.end) {
            throw new IllegalStateException(String.format("No surrogate keys available in buffer! Current=%d, start=%d, end=%d", currentValue, start, end));
        }
        this.nextKey.addAndGet(1);
        return currentValue;
    }

    public long peekNextValue() {
        long currentValue = this.nextKey.get();
        if (currentValue == SURROGATE_KEY_UNASSIGNED) {
            throw new ExceptionInInitializerError("Surrogate keys buffer has not been initialized");
        }
        return currentValue;
    }

    public boolean isAvailable() {
        long currentValue = this.nextKey.get();
        return (currentValue != SURROGATE_KEY_UNASSIGNED && currentValue <= this.end);
    }

    public void moveTo(long start, long nextKey) {
        this.start = start;
        this.end = start + SURROGATE_KEY_BUFFER_SIZE - 1;
        this.nextKey.set(nextKey);
        if (start < 0) {
            throw new ExceptionInInitializerError(String.format("Value for start cannot be negative for surrogate keys buffer. start=%d, nextKey=%d", start,
                    nextKey));
        }
        if (nextKey > this.end + 1) {
            throw new ExceptionInInitializerError(String.format("Value for nextKey cannot exceed end value for surrogate keys buffer. Current=%d, end=%d",
                    nextKey, this.end));
        }
        if (nextKey < start) {
            throw new ExceptionInInitializerError(String.format("Value for nextKey cannot be below start value for surrogate keys buffer. Current=%d, start=%d",
                    nextKey, start));
        }
    }
}
