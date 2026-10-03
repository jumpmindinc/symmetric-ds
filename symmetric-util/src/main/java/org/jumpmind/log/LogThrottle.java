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

/**
 * Decides at which log level a repeating message is written: INFO once per info interval, DEBUG once per debug interval in between, otherwise not at all. The
 * throttle itself never returns TRACE; {@link ThrottledLogger} adds it when the wrapped logger has TRACE enabled.
 */
public class LogThrottle {
    public enum ThrottledLogMessageLevel {
        INFO, DEBUG, TRACE, NONE
    }

    private final long infoIntervalMs;
    private final long debugIntervalMs;
    private final boolean isDebugWhenMinIntervalNotMet;
    private long lastInfoTime;
    private long lastDebugTime;

    public LogThrottle(long startTime, long infoIntervalMs, long debugIntervalMs, boolean isDebugWhenMinIntervalNotMet) {
        this.lastInfoTime = startTime;
        this.lastDebugTime = startTime;
        this.infoIntervalMs = infoIntervalMs;
        this.debugIntervalMs = debugIntervalMs;
        this.isDebugWhenMinIntervalNotMet = isDebugWhenMinIntervalNotMet;
    }

    public static LogThrottle forInfoOnly(long startTime, long infoIntervalMs) {
        return new LogThrottle(startTime, infoIntervalMs, 0, false);
    }

    public ThrottledLogMessageLevel getLevel(long currentTime) {
        if (isElapsed(lastInfoTime, currentTime, infoIntervalMs)) {
            return ThrottledLogMessageLevel.INFO;
        }
        if (isDebugWhenMinIntervalNotMet && isElapsed(lastDebugTime, currentTime, debugIntervalMs)) {
            return ThrottledLogMessageLevel.DEBUG;
        }
        return ThrottledLogMessageLevel.NONE;
    }

    public boolean isInfoDue(long currentTime) {
        return getLevel(currentTime) == ThrottledLogMessageLevel.INFO;
    }

    public boolean isDebugDue(long currentTime) {
        return isDebugWhenMinIntervalNotMet && isElapsed(lastDebugTime, currentTime, debugIntervalMs);
    }

    public void markLogged(ThrottledLogMessageLevel level, long currentTime) {
        if (level == ThrottledLogMessageLevel.INFO) {
            lastInfoTime = currentTime;
            lastDebugTime = currentTime;
        } else if (level == ThrottledLogMessageLevel.DEBUG) {
            lastDebugTime = currentTime;
        }
    }

    private boolean isElapsed(long lastTime, long currentTime, long intervalMs) {
        return currentTime - lastTime > intervalMs;
    }
}
