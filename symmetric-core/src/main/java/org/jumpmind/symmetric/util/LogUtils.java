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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.apache.commons.lang3.StringUtils;

public final class LogUtils {
    private static final Logger log = LoggerFactory.getLogger(LogUtils.class);

    private LogUtils() {
    }

    /**
     * Store contextual information (like an engine name, user ID, or session ID) in a thread-local map that logging frameworks automatically include in every
     * log message produced by that thread.
     */
    public static void setTreadLogContext(String contextId, String value) {
        if (StringUtils.isBlank(value)) {
            removeTreadLogContext(contextId);
            log.debug("Removed logging context due to empty value: {}", contextId);
        }
        MDC.put(contextId, value);
        log.debug("Added logging context: {}={}", contextId, value);
    }

    /**
     * Clears contextual information (like an engine name, user ID, or session ID) from the thread-local map that logging frameworks automatically include in
     * every log message produced by that thread.
     */
    public static void removeTreadLogContext(String contextId) {
        MDC.remove(contextId);
        log.debug("Removed logging context: {}", contextId);
    }
}
