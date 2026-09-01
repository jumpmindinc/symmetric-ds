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

import java.util.List;

import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.jumpmind.symmetric.model.OutgoingBatch;

/**
 * The outcome of {@link org.jumpmind.symmetric.service.IFileSyncService#prepareFilesForPull}, carrying both what the servlet handler needs to set response
 * headers/status, and what {@link org.jumpmind.symmetric.service.IFileSyncService#writeFilesForPull} needs to stream the previously-staged bytes afterward.
 * Split from a single combined call so the handler can set headers on the servlet response <em>before</em> any bytes are written to it - setting a header on an
 * already-committed response is a silent no-op, which previously meant the {@code FileSync-Format} header was never actually sent to the client.
 * <p>
 * {@code resumeEtag} is non-null only when this response served (or attempted to serve) exactly one specific, previously-interrupted batch by request; it is
 * {@code null} for a normal, non-resume pull. {@code allRequestedBatches} is only meaningful for a normal (non-resume) pull - it is the full candidate list
 * {@code batches} was selected from, needed by {@link org.jumpmind.symmetric.service.IFileSyncService#writeFilesForPull} to mark them loaded.
 */
public class FileSyncPullResult {
    private final List<OutgoingBatch> batches;
    private final List<OutgoingBatch> allRequestedBatches;
    private final List<IStagedResource> stagedResources;
    private final boolean isEnvelopeFormatUsed;
    private final boolean isPartialContent;
    private final StagedResourceETag resumeEtag;
    private final long totalSize;
    private final long skipCount;

    private FileSyncPullResult(Builder builder) {
        this.batches = builder.batches;
        this.allRequestedBatches = builder.allRequestedBatches;
        this.stagedResources = builder.stagedResources;
        this.isEnvelopeFormatUsed = builder.isEnvelopeFormatUsed;
        this.isPartialContent = builder.isPartialContent;
        this.resumeEtag = builder.resumeEtag;
        this.totalSize = builder.totalSize;
        this.skipCount = builder.skipCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<OutgoingBatch> getBatches() {
        return batches;
    }

    public List<OutgoingBatch> getAllRequestedBatches() {
        return allRequestedBatches;
    }

    public List<IStagedResource> getStagedResources() {
        return stagedResources;
    }

    public boolean isEnvelopeFormatUsed() {
        return isEnvelopeFormatUsed;
    }

    public boolean isPartialContent() {
        return isPartialContent;
    }

    public StagedResourceETag getResumeEtag() {
        return resumeEtag;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public long getSkipCount() {
        return skipCount;
    }

    public static class Builder {
        private List<OutgoingBatch> batches;
        private List<OutgoingBatch> allRequestedBatches;
        private List<IStagedResource> stagedResources;
        private boolean isEnvelopeFormatUsed;
        private boolean isPartialContent;
        private StagedResourceETag resumeEtag;
        private long totalSize;
        private long skipCount;

        public Builder batches(List<OutgoingBatch> batches) {
            this.batches = batches;
            return this;
        }

        public Builder allRequestedBatches(List<OutgoingBatch> allRequestedBatches) {
            this.allRequestedBatches = allRequestedBatches;
            return this;
        }

        public Builder stagedResources(List<IStagedResource> stagedResources) {
            this.stagedResources = stagedResources;
            return this;
        }

        public Builder envelopeFormatUsed(boolean isEnvelopeFormatUsed) {
            this.isEnvelopeFormatUsed = isEnvelopeFormatUsed;
            return this;
        }

        public Builder partialContent(boolean isPartialContent) {
            this.isPartialContent = isPartialContent;
            return this;
        }

        public Builder resumeEtag(StagedResourceETag resumeEtag) {
            this.resumeEtag = resumeEtag;
            return this;
        }

        public Builder totalSize(long totalSize) {
            this.totalSize = totalSize;
            return this;
        }

        public Builder skipCount(long skipCount) {
            this.skipCount = skipCount;
            return this;
        }

        public FileSyncPullResult build() {
            return new FileSyncPullResult(this);
        }
    }
}
