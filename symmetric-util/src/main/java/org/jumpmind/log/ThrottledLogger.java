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
package org.jumpmind.log;

import org.jumpmind.log.LogThrottle.ThrottledLogMessageLevel;
import org.slf4j.Logger;

/**
 * Writes a message only when its {@link LogThrottle} allows that log level at the given time. When TRACE is enabled on the wrapped logger, getLevel returns
 * TRACE instead of NONE, isDebugDue is always true, and infoOrDebug writes at TRACE between intervals.
 */
public class ThrottledLogger {
    private final Logger log;
    private final LogThrottle throttle;

    public ThrottledLogger(Logger log, LogThrottle throttle) {
        this.log = log;
        this.throttle = throttle;
    }

    public static ThrottledLogger forInfoOnly(Logger log, long startTime, long infoIntervalMs) {
        return new ThrottledLogger(log, LogThrottle.forInfoOnly(startTime, infoIntervalMs));
    }

    public ThrottledLogMessageLevel getLevel(long currentTime) {
        ThrottledLogMessageLevel level = throttle.getLevel(currentTime);
        if (level == ThrottledLogMessageLevel.NONE && log.isTraceEnabled()) {
            return ThrottledLogMessageLevel.TRACE;
        }
        return level;
    }

    public boolean isInfoDue(long currentTime) {
        return throttle.isInfoDue(currentTime);
    }

    public boolean isDebugDue(long currentTime) {
        return log.isTraceEnabled() || throttle.isDebugDue(currentTime);
    }

    public void info(long currentTime, String format, Object... arguments) {
        if (throttle.getLevel(currentTime) == ThrottledLogMessageLevel.INFO) {
            log.info(format, arguments);
            throttle.markLogged(ThrottledLogMessageLevel.INFO, currentTime);
        }
    }

    /**
     * Helper method logs an INFO level message if the INFO-interval has elapsed. Otherwise it logs a DEBUG level message if the debug-interval has elapsed.
     * Otherwise it logs TRACE (if enabled) or nothing. Updates internal timestamp on the logThrottle, only if an INFO or DEBUG message was logged. Recommended
     * to gate with isDebugDue on busy systems.
     */
    public void infoOrDebug(long currentTime, String format, Object... arguments) {
        ThrottledLogMessageLevel level = getLevel(currentTime);
        if (level == ThrottledLogMessageLevel.INFO) {
            log.info(format, arguments);
            throttle.markLogged(level, currentTime);
        } else if (level == ThrottledLogMessageLevel.DEBUG) {
            log.debug(format, arguments);
            throttle.markLogged(level, currentTime);
        } else if (level == ThrottledLogMessageLevel.TRACE) {
            // For super-verbose debugging purposes only as it will affect performance on busy systems!
            log.trace(format, arguments);
        }
    }

    public void debug(long currentTime, String format, Object... arguments) {
        if (throttle.getLevel(currentTime) == ThrottledLogMessageLevel.DEBUG) {
            log.debug(format, arguments);
            throttle.markLogged(ThrottledLogMessageLevel.DEBUG, currentTime);
        }
    }
}
