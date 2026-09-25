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
package org.jumpmind.symmetric.extract;

import java.io.IOException;
import java.io.Writer;

/**
 * Wraps a destination {@link Writer}, discarding the first {@code skipCount} characters written to it and forwarding the rest, while counting the total number
 * of characters seen (skipped plus forwarded). This lets a single deterministic write pass serve both a full batch resend ({@code skipCount == 0}) and a
 * resumed, partial send ({@code skipCount > 0}) starting from the same point in the stream.
 * <p>
 * The count is in decoded characters of the underlying CSV text stream, not raw network bytes: the staged resource is read and written through
 * {@link java.io.Reader}/{@link java.io.Writer}, not {@link java.io.InputStream}/{@link java.io.OutputStream}, so an HTTP Range/Content-Range value used with
 * this class must agree on that same unit on both the client and server side. Since both sides read the exact same staged, UTF-8 file deterministically, this
 * is internally consistent even though it is not a literal byte offset per RFC 9110 Range semantics.
 */
public class CountingSkippingWriter extends Writer {
    private final Writer delegate;
    private final long skipCount;
    private long totalCount;

    public CountingSkippingWriter(Writer delegate, long skipCount) {
        this.delegate = delegate;
        this.skipCount = skipCount;
    }

    public long getTotalCount() {
        return totalCount;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        int writeOff = off;
        int writeLen = len;
        if (totalCount < skipCount) {
            long remainingToSkip = skipCount - totalCount;
            int skipInThisChunk = (int) Math.min(remainingToSkip, len);
            writeOff = off + skipInThisChunk;
            writeLen = len - skipInThisChunk;
        }
        if (writeLen > 0) {
            delegate.write(cbuf, writeOff, writeLen);
        }
        totalCount += len;
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
