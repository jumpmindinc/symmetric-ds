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
package org.jumpmind.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory for creating, prioritizing, and naming threads with specified prefix. */
public class CustomizableThreadFactory implements ThreadFactory {
    private final Logger log = LoggerFactory.getLogger(getClass());
    AtomicInteger threadNumber = new AtomicInteger(1);
    String namePrefix;
    int threadPriority;

    public CustomizableThreadFactory(String threadNamePrefix) {
        this(threadNamePrefix, Thread.NORM_PRIORITY);
    }

    public CustomizableThreadFactory(String threadNamePrefix, int threadPriority) {
        this.namePrefix = threadNamePrefix;
        this.threadPriority = threadPriority;
    }

    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        try {
            thread.setName(namePrefix + "-" + threadNumber.getAndIncrement());
            if (thread.isDaemon()) {
                thread.setDaemon(false);
            }
            if (thread.getPriority() != threadPriority) {
                thread.setPriority(threadPriority);
            }
        } catch (Exception ex) {
            log.error("Error occurred while customizing thread", ex);
        }
        return thread;
    }
}
