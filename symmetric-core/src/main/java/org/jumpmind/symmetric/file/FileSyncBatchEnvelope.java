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
package org.jumpmind.symmetric.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.jumpmind.symmetric.io.stage.StagedResourceETag;

/**
 * A lightweight header written immediately before each batch's complete, independently-staged zip bytes in a {@code FileSync-Format}-tagged pull response, so
 * several batches can be bundled into one response while still letting the reader know exactly where one batch's zip ends and the next one's header begins — a
 * purely length-based framing, no entry-by-entry inspection required.
 * <p>
 * Wire shape is one UTF-8 text line, {@code <batchId>,<zipByteLength>,<etagJson>}, followed by exactly {@code zipByteLength} raw zip bytes. The ETag JSON
 * itself may contain commas, so only the first two commas are treated as delimiters; everything after the second comma is taken verbatim as the ETag JSON.
 */
public class FileSyncBatchEnvelope {
    private final long batchId;
    private final long length;
    private final StagedResourceETag etag;

    public FileSyncBatchEnvelope(long batchId, long length, StagedResourceETag etag) {
        this.batchId = batchId;
        this.length = length;
        this.etag = etag;
    }

    public long getBatchId() {
        return batchId;
    }

    public long getLength() {
        return length;
    }

    public StagedResourceETag getEtag() {
        return etag;
    }

    public static void writeHeader(OutputStream out, long batchId, long length, StagedResourceETag etag) throws IOException {
        String line = batchId + "," + length + "," + etag.toJson() + "\n";
        out.write(line.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads one envelope header line from {@code in}, one byte at a time so as to never consume bytes past the header's trailing newline — the caller must read
     * exactly {@link #getLength()} bytes immediately afterward, so any over-read here would corrupt the following batch's zip content.
     *
     * @return the parsed header, or {@code null} at a clean end of stream (no more batches follow)
     */
    public static FileSyncBatchEnvelope readHeader(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            line.append((char) b);
        }
        if (b == -1 && line.isEmpty()) {
            return null;
        }
        String headerLine = line.toString();
        int firstComma = headerLine.indexOf(',');
        int secondComma = firstComma < 0 ? -1 : headerLine.indexOf(',', firstComma + 1);
        if (firstComma < 0 || secondComma < 0) {
            throw new IOException("Malformed file sync envelope header: " + headerLine);
        }
        long batchId = Long.parseLong(headerLine.substring(0, firstComma));
        long length = Long.parseLong(headerLine.substring(firstComma + 1, secondComma));
        StagedResourceETag etag = StagedResourceETag.fromJson(headerLine.substring(secondComma + 1));
        return new FileSyncBatchEnvelope(batchId, length, etag);
    }
}
