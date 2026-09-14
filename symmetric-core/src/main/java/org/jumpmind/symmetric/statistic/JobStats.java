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
package org.jumpmind.symmetric.statistic;

import java.util.Date;

import org.jumpmind.util.AppUtils;

public class JobStats extends AbstractNodeHostStats {
    private String jobName;
    private long processedCount;
    private String targetNodeId;
    private int targetNodeCount;
    private boolean errorFlag;
    private String errorMessage;

    public JobStats() {
    }

    public JobStats(String nodeId, String hostName, Date startTime, Date endTime, String jobName) {
        super(nodeId, hostName, startTime, endTime);
        this.jobName = jobName;
    }

    public JobStats(String targetNodeId, int targetNodeCount, long startTime, long endTime, String jobName,
            long processedCount) {
        this(jobName, startTime, endTime, processedCount);
        this.targetNodeId = targetNodeId;
        this.targetNodeCount = targetNodeCount;
    }

    public JobStats(String jobName, long startTime, long endTime, long processedCount) {
        this(null, null, new Date(startTime), new Date(endTime), jobName);
        this.processedCount = processedCount;
    }

    public JobStats(String jobName, long startTime, long endTime, long processedCount, Exception e) {
        this(null, null, new Date(startTime), new Date(endTime), jobName);
        this.processedCount = processedCount;
        this.errorFlag = true;
        this.errorMessage = e.getClass().getName() + ": " + e.getMessage() + "\r\n" + AppUtils.formatStackTrace(e.getStackTrace(), 50, false);
    }

    public JobStats(String jobName, long startTime, long endTime, long processedCount, String errorMessage) {
        this(null, null, new Date(startTime), new Date(endTime), jobName);
        this.processedCount = processedCount;
        if (errorMessage != null && !errorMessage.isBlank()) {
            this.errorFlag = true;
            this.errorMessage = errorMessage;
        }
    }

    public JobStats(JobStats source) {
        super(source.getNodeId(), source.getHostName(), source.getStartTime(), source.getEndTime());
        this.jobName = source.getJobName();
        this.processedCount = source.getProcessedCount();
        this.targetNodeId = source.getTargetNodeId();
        this.targetNodeCount = source.getTargetNodeCount();
        this.errorFlag = source.isErrorFlag();
        this.errorMessage = source.getErrorMessage();
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String channelId) {
        this.jobName = channelId;
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public void setProcessedCount(long processedCount) {
        this.processedCount = processedCount;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public int getTargetNodeCount() {
        return targetNodeCount;
    }

    public void setTargetNodeCount(int targetNodeCount) {
        this.targetNodeCount = targetNodeCount;
    }

    public boolean isErrorFlag() {
        return errorFlag;
    }

    public void setErrorFlag(boolean errorFlag) {
        this.errorFlag = errorFlag;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}