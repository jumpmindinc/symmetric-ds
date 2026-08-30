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
package org.jumpmind.symmetric.staging.memory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class ThresholdSpillWriterTest {
    @Test
    void belowThreshold_staysInMemory() throws IOException {
        AtomicBoolean spilled = new AtomicBoolean(false);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        ThresholdSpillWriter writer = new ThresholdSpillWriter(100L, new ThresholdSpillWriter.SpillTarget() {
            @Override
            public OutputStream openSpillTarget() {
                return sink;
            }

            @Override
            public void onSpilled() {
                spilled.set(true);
            }
        });
        writer.write("short".getBytes());
        writer.close();
        assertFalse(spilled.get());
        assertFalse(writer.isSpilled());
        assertNotNull(writer.getMemorySnapshot());
        assertArrayEquals("short".getBytes(), writer.getMemorySnapshot());
    }

    @Test
    void aboveThreshold_spillsAndPreservesContent() throws IOException {
        AtomicBoolean spilled = new AtomicBoolean(false);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        ThresholdSpillWriter writer = new ThresholdSpillWriter(4L, new ThresholdSpillWriter.SpillTarget() {
            @Override
            public OutputStream openSpillTarget() {
                return sink;
            }

            @Override
            public void onSpilled() {
                spilled.set(true);
            }
        });
        writer.write("abcdef".getBytes());
        writer.close();
        assertTrue(spilled.get());
        assertTrue(writer.isSpilled());
        assertNull(writer.getMemorySnapshot());
        assertArrayEquals("abcdef".getBytes(), sink.toByteArray());
    }

    @Test
    void multipleWrites_belowThresholdAccumulate() throws IOException {
        ThresholdSpillWriter writer = new ThresholdSpillWriter(1024L, new ThresholdSpillWriter.SpillTarget() {
            @Override
            public OutputStream openSpillTarget() {
                return new ByteArrayOutputStream();
            }

            @Override
            public void onSpilled() {
            }
        });
        writer.write("aa".getBytes());
        writer.write("bb".getBytes());
        writer.close();
        assertArrayEquals("aabb".getBytes(), writer.getMemorySnapshot());
    }

    @Test
    void exactlyAtThreshold_doesNotSpill() throws IOException {
        ThresholdSpillWriter writer = new ThresholdSpillWriter(4L, new ThresholdSpillWriter.SpillTarget() {
            @Override
            public OutputStream openSpillTarget() {
                throw new IllegalStateException("Should not spill");
            }

            @Override
            public void onSpilled() {
            }
        });
        writer.write("abcd".getBytes());
        writer.close();
        assertFalse(writer.isSpilled());
    }
}
