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
package org.jumpmind.symmetric.io.data;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.util.Statistics;

public class Batch {
    public static final long UNKNOWN_BATCH_ID = -9999;
    public static final String DEFAULT_CHANNEL_ID = "default";

    public enum BatchType {
        EXTRACT, LOAD
    };

    protected long batchId = UNKNOWN_BATCH_ID;
    protected String sourceNodeId;
    protected String targetNodeId;
    protected boolean initialLoad;
    protected String channelId = DEFAULT_CHANNEL_ID;
    protected BinaryEncoding binaryEncoding;
    protected Date startTime;
    protected long lineCount;
    protected long dataReadMillis;
    protected long dataWriteMillis;
    protected boolean ignored = false;
    protected boolean common = false;
    protected boolean complete = false;
    protected BatchType batchType;
    protected Statistics statistics;
    protected boolean invalidRetry = false;
    protected boolean bulkLoaderFlag;

    public boolean isBulkLoaderFlag() {
        return bulkLoaderFlag;
    }

    public void setBulkLoaderFlag(boolean bulkLoaderFlag) {
        this.bulkLoaderFlag = bulkLoaderFlag;
    }

    protected Map<String, Long> timers = new HashMap<String, Long>();

    public Batch(BatchType batchType, long batchId, String channelId, BinaryEncoding binaryEncoding, String sourceNodeId, String targetNodeId, boolean common) {
        this.batchType = batchType;
        this.batchId = batchId;
        if (channelId != null) {
            this.channelId = channelId;
        }
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.binaryEncoding = binaryEncoding;
        this.common = common;
        this.startTime = new Date();
    }

    public Batch() {
        this.startTime = new Date();
    }

    public void setBatchId(long batchId) {
        this.batchId = batchId;
    }

    public long incrementLineCount() {
        return ++lineCount;
    }

    public void incrementDataReadMillis(long millis) {
        dataReadMillis += millis;
    }

    public void incrementDataWriteMillis(long millis) {
        dataWriteMillis += millis;
    }

    public void startTimer(String name) {
        timers.put(name, System.currentTimeMillis());
    }

    public long endTimer(String name) {
        Long startTime = (Long) timers.remove(name);
        if (startTime != null) {
            return System.currentTimeMillis() - startTime;
        } else {
            return 0l;
        }
    }

    public long getDataReadMillis() {
        return dataReadMillis;
    }

    public long getDataWriteMillis() {
        return dataWriteMillis;
    }

    public long getLineCount() {
        return lineCount;
    }

    public void setLineCount(long lineCount) {
        this.lineCount = lineCount;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getNodeBatchId() {
        String nodeId = batchType == BatchType.EXTRACT ? targetNodeId : sourceNodeId;
        return String.format("%s-%d", nodeId, batchId);
    }

    public long getBatchId() {
        return batchId;
    }

    public String getChannelId() {
        return channelId;
    }

    public boolean isInitialLoad() {
        return initialLoad;
    }

    public BinaryEncoding getBinaryEncoding() {
        return binaryEncoding;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setCommon(boolean commonFlag) {
        this.common = commonFlag;
    }

    public boolean isCommon() {
        return common;
    }

    public BatchType getBatchType() {
        return batchType;
    }

    public String getStagedLocation() {
        if (batchType == BatchType.EXTRACT) {
            return getStagedLocation(common, targetNodeId, batchId);
        } else {
            return getStagedLocation(common, sourceNodeId, batchId);
        }
    }

    public static String getStagedLocation(boolean common, String nodeId, long batchId) {
        return common ? "common/" + String.format("%03d", batchId % 1000) : nodeId;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setBinaryEncoding(BinaryEncoding binaryEncoding) {
        this.binaryEncoding = binaryEncoding;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public void setInvalidRetry(boolean invalidRetry) {
        this.invalidRetry = invalidRetry;
    }

    public boolean isInvalidRetry() {
        return invalidRetry;
    }

    public String encodeBinary(String value) {
        if (value != null) {
            if (binaryEncoding == BinaryEncoding.HEX) {
                value = new String(Hex.encodeHex(value.getBytes(Charset.defaultCharset())));
            } else if (binaryEncoding == BinaryEncoding.BASE64) {
                value = new String(Base64.encodeBase64(value.getBytes(Charset.defaultCharset())), Charset.defaultCharset());
            }
        }
        return value;
    }

    public byte[] decodeBinary(String value) {
        if (value != null) {
            try {
                if (binaryEncoding == BinaryEncoding.HEX) {
                    return Hex.decodeHex(value.toCharArray());
                } else if (binaryEncoding == BinaryEncoding.BASE64) {
                    return Base64.decodeBase64(value.getBytes(Charset.defaultCharset()));
                } else {
                    return value.getBytes(Charset.defaultCharset());
                }
            } catch (DecoderException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
