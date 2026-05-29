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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ThresholdSpillWriter extends OutputStream {
    public interface SpillTarget {
        OutputStream openSpillTarget() throws IOException;

        void onSpilled();
    }

    private final long thresholdBytes;
    private final SpillTarget spillTarget;
    private ByteArrayOutputStream memoryBuffer;
    private OutputStream backend;
    private boolean spilled;
    private long bytesWritten;

    public ThresholdSpillWriter(long thresholdBytes, SpillTarget spillTarget) {
        this.thresholdBytes = thresholdBytes;
        this.spillTarget = spillTarget;
        this.memoryBuffer = new ByteArrayOutputStream();
        this.spilled = false;
    }

    public boolean isSpilled() {
        return spilled;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public byte[] getMemorySnapshot() {
        if (spilled || memoryBuffer == null) {
            return null;
        }
        return memoryBuffer.toByteArray();
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        bytesWritten += len;
        if (spilled) {
            backend.write(b, off, len);
            return;
        }
        if (memoryBuffer.size() + len > thresholdBytes) {
            initiateSpill();
            backend.write(b, off, len);
        } else {
            memoryBuffer.write(b, off, len);
        }
    }

    private void initiateSpill() throws IOException {
        backend = spillTarget.openSpillTarget();
        if (memoryBuffer != null && memoryBuffer.size() > 0) {
            backend.write(memoryBuffer.toByteArray());
        }
        memoryBuffer = null;
        spilled = true;
        spillTarget.onSpilled();
    }

    @Override
    public void flush() throws IOException {
        if (backend != null) {
            backend.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (backend != null) {
            backend.close();
            backend = null;
        }
    }
}
