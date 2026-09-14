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
package org.jumpmind.symmetric.util;

import org.jumpmind.symmetric.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueThread {
    protected final Logger log = LoggerFactory.getLogger(QueueThread.class);
    protected String name;
    protected int threadId;
    protected boolean usingThreading;

    public QueueThread(String queue) {
        if (queue != null) {
            int index = queue.indexOf(Constants.DELIMITER_QUEUE_THREAD);
            if (index != -1 && queue.length() >= index + 1) {
                name = queue.substring(0, index);
                try {
                    threadId = Integer.parseInt(queue.substring(index + 1));
                    usingThreading = true;
                } catch (NumberFormatException e) {
                    log.warn("Invalid thread ID from queue {}", queue);
                }
            } else {
                name = queue;
            }
        }
    }

    public static String getQueueName(String queue) {
        int index = queue == null ? -1 : queue.indexOf(Constants.DELIMITER_QUEUE_THREAD);
        if (index != -1) {
            return queue.substring(0, index);
        }
        return queue;
    }

    public String getQueueName() {
        return name;
    }

    public int getThreadId() {
        return threadId;
    }

    public boolean isUsingThreading() {
        return usingThreading;
    }
}
