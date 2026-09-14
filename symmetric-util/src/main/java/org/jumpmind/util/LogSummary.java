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
package org.jumpmind.util;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.event.Level;

import com.google.gson.annotations.Expose;

public class LogSummary implements Comparable<LogSummary> {
    @Expose
    private Level level;
    @Expose
    private String mostRecentThreadName;
    private transient Throwable throwable;
    @Expose
    private long firstOccurranceTime;
    @Expose
    private long mostRecentTime;
    @Expose
    private int count;
    @Expose
    private String message;
    @Expose
    private String stackTrace;
    @Expose
    private Integer levelInt;

    public void setLevel(Level level) {
        this.level = level;
    }

    public Level getLevel() {
        return level;
    }

    public String getStackTrace() {
        if (this.stackTrace == null && this.throwable != null) {
            StringWriter st = new StringWriter();
            throwable.printStackTrace(new PrintWriter(st));
            this.stackTrace = st.toString();
        }
        return this.stackTrace;
    }

    public void setStackTrace(String st) {
        this.stackTrace = st;
    }

    public Integer getLevelInt() {
        return this.levelInt != null ? this.levelInt : this.level != null ? this.level.toInt() : 0;
    }

    public void setLevelInt(Integer levelInt) {
        this.levelInt = levelInt;
    }

    public long getFirstOccurranceTime() {
        return firstOccurranceTime;
    }

    public void setFirstOccurranceTime(long firstOccurranceDate) {
        this.firstOccurranceTime = firstOccurranceDate;
    }

    public long getMostRecentTime() {
        return mostRecentTime;
    }

    public void setMostRecentTime(long mostRecentDate) {
        this.mostRecentTime = mostRecentDate;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setMostRecentThreadName(String mostRecentThreadName) {
        this.mostRecentThreadName = mostRecentThreadName;
    }

    public String getMostRecentThreadName() {
        return mostRecentThreadName;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public int compareTo(LogSummary other) {
        if (mostRecentTime == other.mostRecentTime) {
            return 0;
        } else {
            return mostRecentTime > other.mostRecentTime ? 1 : -1;
        }
    }
}
