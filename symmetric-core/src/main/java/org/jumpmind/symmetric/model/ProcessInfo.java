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
package org.jumpmind.symmetric.model;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Date;

import org.jumpmind.extension.IProcessInfoListener;
import org.jumpmind.util.AppUtils;

public class ProcessInfo implements Serializable, Comparable<ProcessInfo>, Cloneable {
    private static final long serialVersionUID = 1L;

    public static enum ProcessStatus {
        NEW("New"), QUERYING("Querying"), EXTRACTING("Extracting"), LOADING("Loading"), TRANSFERRING("Transferring"), ACKING(
                "Acking"), PROCESSING("Processing"), OK("Ok"), ERROR("Error"), CREATING("Creating");

        private String description;

        ProcessStatus(String description) {
            this.description = description;
        }

        public ProcessStatus fromDesciption(String description) {
            for (ProcessStatus status : ProcessStatus.values()) {
                if (status.description.equals(description)) {
                    return status;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return description;
        }
    };

    private ProcessInfoKey key;
    private ProcessStatus status = ProcessStatus.NEW;
    private long currentDataCount;
    private long totalDataCount = 0;
    private long totalBatchCount;
    private long currentBatchId;
    private long currentBatchCount;
    private long currentRowCount;
    private String currentChannelId;
    private String currentTableName;
    private transient Thread thread;
    private Date currentBatchStartTime;
    private long currentLoadId;
    private Date startTime = new Date();
    private Date lastStatusChangeTime = new Date();
    private Date endTime;
    private IProcessInfoListener listener;
    private boolean bulkLoadFlag = false;

    public ProcessInfo() {
        this(new ProcessInfoKey("", "", null));
    }

    public ProcessInfo(ProcessInfoKey key) {
        this.key = key;
        thread = Thread.currentThread();
    }

    public String getSourceNodeId() {
        return this.key.getSourceNodeId();
    }

    public String getTargetNodeId() {
        return this.key.getTargetNodeId();
    }

    public ProcessType getProcessType() {
        return this.key.getProcessType();
    }

    public ProcessInfoKey getKey() {
        return key;
    }

    public void setKey(ProcessInfoKey key) {
        this.key = key;
    }

    public ProcessStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessStatus status) {
        this.status = status;
        this.lastStatusChangeTime = new Date();
        if (isStatusFinal(status)) {
            this.endTime = new Date();
        }
    }

    public boolean isStatusFinal() {
        return isStatusFinal(this.status);
    }

    public static boolean isStatusFinal(ProcessStatus status) {
        return status == ProcessStatus.OK || status == ProcessStatus.ERROR;
    }

    public long getCurrentDataCount() {
        return currentDataCount;
    }

    public void setCurrentDataCount(long dataCount) {
        this.currentDataCount = dataCount;
        if (listener != null) {
            listener.changeDataCount(totalDataCount, currentDataCount);
        }
    }

    public long getCurrentRowCount() {
        return currentRowCount;
    }

    public void setCurrentRowCount(long rowCount) {
        this.currentRowCount = rowCount;
    }

    public long getTotalBatchCount() {
        return totalBatchCount;
    }

    public void setTotalBatchCount(long batchCount) {
        this.totalBatchCount = batchCount;
    }

    public void incrementCurrentDataCount() {
        this.currentDataCount++;
        if (totalDataCount < currentDataCount) {
            totalDataCount = currentDataCount;
        }
        if (listener != null) {
            listener.changeDataCount(totalDataCount, currentDataCount);
        }
    }

    public void incrementBatchCount() {
        this.totalBatchCount++;
    }

    public void incrementCurrentBatchCount() {
        this.currentBatchCount++;
    }

    public long getCurrentBatchCount() {
        return currentBatchCount;
    }

    public void setCurrentBatchCount(long currentBatchCount) {
        this.currentBatchCount = currentBatchCount;
    }

    public long getCurrentBatchId() {
        return currentBatchId;
    }

    public void setCurrentBatchId(long currentBatchId) {
        this.currentBatchId = currentBatchId;
        this.currentBatchStartTime = new Date();
    }

    public void setCurrentLoadId(long loadId) {
        this.currentLoadId = loadId;
    }

    public long getCurrentLoadId() {
        return currentLoadId;
    }

    public String getQueue() {
        String queue = key.getQueue();
        if (queue == null) {
            queue = "";
        }
        return queue;
    }

    public String getCurrentChannelId() {
        return currentChannelId;
    }

    public void setCurrentChannelId(String currentChannelId) {
        this.currentChannelId = currentChannelId;
    }

    public Thread getThread() {
        return thread;
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public void setCurrentTableName(String currentTableName) {
        this.currentTableName = currentTableName;
    }

    public String getCurrentTableName() {
        return currentTableName;
    }

    public Date getLastStatusChangeTime() {
        return lastStatusChangeTime;
    }

    public Date getCurrentBatchStartTime() {
        if (currentBatchStartTime == null) {
            return startTime;
        } else {
            return currentBatchStartTime;
        }
    }

    public void setCurrentBatchStartTime(Date currentBatchStartTime) {
        this.currentBatchStartTime = currentBatchStartTime;
    }

    @Override
    public String toString() {
        return String.format("%s,status=%s,startTime=%s", key.toString(), status.toString(), startTime.toString());
    }

    public String showInError(String identityNodeId) {
        if (status == ProcessStatus.ERROR) {
            switch (key.getProcessType()) {
                case PUSH_JOB_EXTRACT:
                case PUSH_JOB_TRANSFER:
                case PULL_HANDLER_EXTRACT:
                case PULL_HANDLER_TRANSFER:
                    return key.getTargetNodeId();
                case PULL_JOB_LOAD:
                case PULL_JOB_TRANSFER:
                case PUSH_HANDLER_LOAD:
                case PUSH_HANDLER_TRANSFER:
                case ROUTER_JOB:
                case ROUTER_READER:
                case GAP_DETECT:
                    return key.getSourceNodeId();
                default:
                    return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public int compareTo(ProcessInfo o) {
        if (status == ProcessStatus.ERROR && o.status != ProcessStatus.ERROR) {
            return -1;
        } else if (o.status == ProcessStatus.ERROR && status != ProcessStatus.ERROR) {
            return 1;
        } else if (status != ProcessStatus.OK && o.status == ProcessStatus.OK) {
            return -1;
        } else if (o.status != ProcessStatus.OK && status == ProcessStatus.OK) {
            return 1;
        } else {
            return o.startTime.compareTo(startTime);
        }
    }

    public ThreadData getThreadData() {
        if (thread != null && thread.isAlive()) {
            return getThreadData(thread.threadId());
        } else {
            return null;
        }
    }

    public static ThreadData getThreadData(long threadId) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        ThreadInfo info = threadBean.getThreadInfo(threadId, 100);
        if (info != null) {
            String threadName = info.getThreadName();
            return new ThreadData(threadName, AppUtils.formatStackTrace(info.getStackTrace()));
        } else {
            return null;
        }
    }

    public long getTotalDataCount() {
        return totalDataCount;
    }

    public void setTotalDataCount(long totalDataCount) {
        this.totalDataCount = totalDataCount;
    }

    public void setListener(IProcessInfoListener listener) {
        this.listener = listener;
    }

    public boolean isBulkLoadFlag() {
        return bulkLoadFlag;
    }

    public void setBulkLoadFlag(boolean bulkLoadFlag) {
        this.bulkLoadFlag = bulkLoadFlag;
    }

    static public class ThreadData {
        public ThreadData(String threadName, String stackTrace) {
            this.threadName = threadName;
            this.stackTrace = stackTrace;
        }

        private String threadName;
        private String stackTrace;

        public String getStackTrace() {
            return stackTrace;
        }

        public String getThreadName() {
            return threadName;
        }
    }

    public ProcessInfo copy() {
        try {
            return (ProcessInfo) this.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ProcessInfo other = (ProcessInfo) obj;
        return key.equals(other.getKey());
    }
}
