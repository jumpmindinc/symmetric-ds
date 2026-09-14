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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

public class LogSummaryAppender extends AppenderBase<ILoggingEvent> {
    protected Map<String, Map<String, LogSummary>> errorsByEngineByMessage = new ConcurrentHashMap<>();
    protected Map<String, Map<String, LogSummary>> warningByEngineByMessage = new ConcurrentHashMap<>();

    public LogSummaryAppender(String name) {
        setName(name);
    }

    @Override
    protected void append(ILoggingEvent event) {
        Map<String, Map<String, LogSummary>> summaries = null;
        if (event.getLevel() == Level.ERROR) {
            summaries = errorsByEngineByMessage;
        } else if (event.getLevel() == Level.WARN) {
            summaries = warningByEngineByMessage;
        }
        if (summaries == null) {
            return;
        }
        String engineName = event.getMDCPropertyMap().get("engineName");
        if (StringUtils.isBlank(engineName)) {
            return;
        }
        Map<String, LogSummary> summariesForEngine = summaries.computeIfAbsent(engineName,
                k -> new ConcurrentHashMap<>());
        String message = extractMessage(event);
        LogSummary summary = summariesForEngine.computeIfAbsent(message, k -> {
            LogSummary newSummary = new LogSummary();
            newSummary.setMessage(message);
            newSummary.setFirstOccurranceTime(event.getTimeStamp());
            return newSummary;
        });
        summary.setLevel(convertFromLevel(event.getLevel()));
        summary.setMostRecentTime(event.getTimeStamp());
        summary.setCount(summary.getCount() + 1);
        IThrowableProxy proxy = event.getThrowableProxy();
        if (proxy != null) {
            summary.setStackTrace(ThrowableProxyUtil.asString(proxy));
        }
        summary.setMostRecentThreadName(event.getThreadName());
    }

    private String extractMessage(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (StringUtils.isNotEmpty(message) && !"null".equals(message)) {
            return message;
        }
        IThrowableProxy proxy = event.getThrowableProxy();
        if (proxy != null) {
            return proxy.getClassName() + ": " + proxy.getMessage();
        }
        return "Unhandled error";
    }

    private org.slf4j.event.Level convertFromLevel(Level level) {
        if (level == Level.TRACE) {
            return org.slf4j.event.Level.TRACE;
        }
        if (level == Level.DEBUG) {
            return org.slf4j.event.Level.DEBUG;
        }
        if (level == Level.INFO) {
            return org.slf4j.event.Level.INFO;
        }
        if (level == Level.WARN) {
            return org.slf4j.event.Level.WARN;
        }
        return org.slf4j.event.Level.ERROR;
    }

    public List<LogSummary> getLogSummaries(String engineName, Level level) {
        Map<String, Map<String, LogSummary>> summaries = null;
        if (level == Level.ERROR) {
            summaries = errorsByEngineByMessage;
        } else if (level == Level.WARN) {
            summaries = warningByEngineByMessage;
        }
        List<LogSummary> list = new ArrayList<>();
        if (summaries != null && summaries.get(engineName) != null) {
            list.addAll(summaries.get(engineName).values());
            Collections.sort(list, Comparator.comparingLong(LogSummary::getMostRecentTime));
        }
        return list;
    }

    public void clearAll(String engineName) {
        errorsByEngineByMessage.remove(engineName);
        warningByEngineByMessage.remove(engineName);
    }

    public void purgeOlderThan(long time) {
        purgeOlderThan(time, errorsByEngineByMessage);
        purgeOlderThan(time, warningByEngineByMessage);
    }

    protected void purgeOlderThan(long time,
            Map<String, Map<String, LogSummary>> logSummaryByEngineByMessage) {
        Collection<Map<String, LogSummary>> all = logSummaryByEngineByMessage.values();
        for (Map<String, LogSummary> map : all) {
            Set<String> keys = map.keySet();
            for (String key : keys) {
                LogSummary summary = map.get(key);
                if (summary != null && summary.getMostRecentTime() < time) {
                    map.remove(key);
                }
            }
        }
    }
}
