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
package org.jumpmind.symmetric.transport.http;

import org.jumpmind.symmetric.io.stage.StagedResourceETag;

/**
 * Identifies one batch pull that was interrupted partway through and is still eligible for a resumed retry. The {@code etag} is captured as soon as it is read
 * from the batch's proactive {@code ETAG} preamble line, since the connection can drop before the server gets a chance to send it again. {@code channelId} and
 * {@code binaryEncoding} are captured from the same preamble so a resumed ({@code 206}) response - which contains only the remaining row data - can reconstruct
 * the batch's identity locally.
 * <p>
 * {@code queue} lets {@link IHttpResumeCache#getPendingForNode(String, String)} restrict a lookup to the one queue that owns this batch, but it's not a
 * sufficient discriminator on its own: table-sync and file-sync channels commonly share a queue. {@code fileSync} is the unambiguous marker, set directly by
 * the registering code, that each resume consumer uses to recognize only the entries it registered itself.
 */
public class ResumeCacheEntry {
    private final String nodeId;
    private final long batchId;
    private final StagedResourceETag etag;
    private final long receivedCount;
    private final String channelId;
    private final String binaryEncoding;
    private final long cachedAtTime;
    private final String queue;
    private final boolean fileSync;

    private ResumeCacheEntry(Builder builder) {
        this.nodeId = builder.nodeId;
        this.batchId = builder.batchId;
        this.etag = builder.etag;
        this.receivedCount = builder.receivedCount;
        this.channelId = builder.channelId;
        this.binaryEncoding = builder.binaryEncoding;
        this.cachedAtTime = builder.cachedAtTime;
        this.queue = builder.queue;
        this.fileSync = builder.fileSync;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getNodeId() {
        return nodeId;
    }

    public long getBatchId() {
        return batchId;
    }

    public StagedResourceETag getEtag() {
        return etag;
    }

    public long getReceivedCount() {
        return receivedCount;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getBinaryEncoding() {
        return binaryEncoding;
    }

    public long getCachedAtTime() {
        return cachedAtTime;
    }

    public String getQueue() {
        return queue;
    }

    public boolean isFileSync() {
        return fileSync;
    }

    public static class Builder {
        private String nodeId;
        private long batchId;
        private StagedResourceETag etag;
        private long receivedCount;
        private String channelId;
        private String binaryEncoding;
        private long cachedAtTime;
        private String queue;
        private boolean fileSync;

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder batchId(long batchId) {
            this.batchId = batchId;
            return this;
        }

        public Builder etag(StagedResourceETag etag) {
            this.etag = etag;
            return this;
        }

        public Builder receivedCount(long receivedCount) {
            this.receivedCount = receivedCount;
            return this;
        }

        public Builder channelId(String channelId) {
            this.channelId = channelId;
            return this;
        }

        public Builder binaryEncoding(String binaryEncoding) {
            this.binaryEncoding = binaryEncoding;
            return this;
        }

        public Builder cachedAtTime(long cachedAtTime) {
            this.cachedAtTime = cachedAtTime;
            return this;
        }

        public Builder queue(String queue) {
            this.queue = queue;
            return this;
        }

        public Builder fileSync(boolean fileSync) {
            this.fileSync = fileSync;
            return this;
        }

        public ResumeCacheEntry build() {
            return new ResumeCacheEntry(this);
        }
    }
}
