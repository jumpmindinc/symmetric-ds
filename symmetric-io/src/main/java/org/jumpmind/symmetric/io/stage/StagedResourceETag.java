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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Identifies a specific version of a staged resource's content, so a resumed transfer can tell whether a partially-received copy is still valid to append to,
 * or stale and needing a full resend. Based on the resource's stable {@link IStagedResource#getGenerationTime()}, not
 * {@link IStagedResource#getLastUpdateTime()}, which is refreshed on every access and would make every ETag comparison a false mismatch.
 */
public class StagedResourceETag {
    public static final int CURRENT_VERSION = 1;
    private static final Logger log = LoggerFactory.getLogger(StagedResourceETag.class);
    private static final Gson GSON = new Gson();
    private int version = CURRENT_VERSION;
    private long generationTime;
    private long size;

    public StagedResourceETag() {
    }

    public StagedResourceETag(long generationTime, long size) {
        this.generationTime = generationTime;
        this.size = size;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public long getGenerationTime() {
        return generationTime;
    }

    public void setGenerationTime(long generationTime) {
        this.generationTime = generationTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Never throws. A stale, malformed, or future-incompatible {@code If-ETag} value should be treated as "no match, do a full resend" rather than fail the
     * request.
     */
    public static StagedResourceETag fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            StagedResourceETag etag = GSON.fromJson(json, StagedResourceETag.class);
            if (etag == null || etag.version != CURRENT_VERSION) {
                return null;
            }
            return etag;
        } catch (JsonSyntaxException e) {
            log.debug("Ignoring unparseable staged resource ETag: {}", json, e);
            return null;
        }
    }

    @Override
    public int hashCode() {
        return Long.hashCode(version * 31L + generationTime * 31L + size);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StagedResourceETag)) {
            return false;
        }
        StagedResourceETag other = (StagedResourceETag) obj;
        return version == other.version && generationTime == other.generationTime && size == other.size;
    }

    @Override
    public String toString() {
        return toJson();
    }
}
