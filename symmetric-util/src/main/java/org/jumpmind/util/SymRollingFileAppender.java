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

import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.CRC32;

import ch.qos.logback.classic.Level;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.rolling.RollingFileAppender;

public class SymRollingFileAppender extends RollingFileAppender<ILoggingEvent> {
    private int historySize = 2048;
    private Map<String, String> loggedEventKeys;

    @Override
    public void start() {
        loggedEventKeys = new LinkedHashMap<String, String>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Entry<String, String> eldest) {
                return size() >= historySize;
            }
        };
        super.start();
    }

    @Override
    public void rollover() {
        loggedEventKeys.clear();
        super.rollover();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isDebugOrBelow(event)) {
            String key = toKey(event);
            if (key != null) {
                if (loggedEventKeys.containsKey(key)) {
                    event = suppressStackTrace(event, key);
                } else {
                    event = appendKey(event, key);
                    loggedEventKeys.put(key, null);
                }
            }
        }
        super.append(event);
    }

    private boolean isDebugOrBelow(ILoggingEvent event) {
        return event != null && event.getLevel() != null && !event.getLevel().isGreaterOrEqual(Level.INFO);
    }

    protected String toKey(ILoggingEvent event) {
        IThrowableProxy proxy = event.getThrowableProxy();
        if (proxy == null || proxy.getStackTraceElementProxyArray() == null) {
            return null;
        }
        try {
            String className = proxy.getClassName();
            int periodIndex = className.lastIndexOf('.');
            String simpleName = periodIndex >= 0 ? className.substring(periodIndex + 1) : className;
            StringBuilder buff = new StringBuilder(128);
            buff.append(simpleName);
            if (proxy.getStackTraceElementProxyArray().length == 0) {
                buff.append("-jvm-optimized");
            }
            buff.append(":");
            buff.append(getThrowableHash(proxy.getStackTraceElementProxyArray(), proxy.getMessage()));
            return buff.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    protected long getThrowableHash(StackTraceElementProxy[] elements, String message) throws UnsupportedEncodingException {
        CRC32 crc = new CRC32();
        if (message != null) {
            crc.update(message.getBytes("UTF8"));
        }
        for (StackTraceElementProxy element : elements) {
            StackTraceElement stackTraceElement = element.getStackTraceElement();
            crc.update((stackTraceElement.getClassName() + stackTraceElement.getMethodName()
                    + stackTraceElement.getLineNumber()).getBytes("UTF8"));
        }
        return crc.getValue();
    }

    protected ILoggingEvent appendKey(ILoggingEvent event, String key) {
        String message = buildMessageWithKey(event, key, ".init");
        return new WrappedLoggingEvent(event) {
            @Override
            public String getFormattedMessage() {
                return message;
            }
        };
    }

    protected ILoggingEvent suppressStackTrace(ILoggingEvent event, String key) {
        String message = buildMessageWithKey(event, key, null);
        return new WrappedLoggingEvent(event) {
            @Override
            public IThrowableProxy getThrowableProxy() {
                return null;
            }

            @Override
            public String getFormattedMessage() {
                return message;
            }
        };
    }

    protected String buildMessageWithKey(ILoggingEvent event, String key, String prefix) {
        StringBuilder buff = new StringBuilder(128);
        if (event.getFormattedMessage() != null) {
            buff.append(event.getFormattedMessage()).append(" ");
        }
        buff.append("StackTraceKey");
        if (prefix != null) {
            buff.append(prefix);
        }
        buff.append(" [").append(key).append("]");
        return buff.toString();
    }

    public int getHistorySize() {
        return historySize;
    }

    public void setHistorySize(int historySize) {
        this.historySize = historySize;
    }

    abstract static class WrappedLoggingEvent implements ILoggingEvent {
        protected final ILoggingEvent delegate;

        WrappedLoggingEvent(ILoggingEvent delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getThreadName() {
            return delegate.getThreadName();
        }

        @Override
        public Level getLevel() {
            return delegate.getLevel();
        }

        @Override
        public String getMessage() {
            return delegate.getMessage();
        }

        @Override
        public Object[] getArgumentArray() {
            return delegate.getArgumentArray();
        }

        @Override
        public String getFormattedMessage() {
            return delegate.getFormattedMessage();
        }

        @Override
        public String getLoggerName() {
            return delegate.getLoggerName();
        }

        @Override
        public LoggerContextVO getLoggerContextVO() {
            return delegate.getLoggerContextVO();
        }

        @Override
        public IThrowableProxy getThrowableProxy() {
            return delegate.getThrowableProxy();
        }

        @Override
        public StackTraceElement[] getCallerData() {
            return delegate.getCallerData();
        }

        @Override
        public boolean hasCallerData() {
            return delegate.hasCallerData();
        }

        @Override
        public List<Marker> getMarkerList() {
            return delegate.getMarkerList();
        }

        @Override
        public Map<String, String> getMDCPropertyMap() {
            return delegate.getMDCPropertyMap();
        }

        @Override
        @SuppressWarnings("deprecation")
        public Map<String, String> getMdc() {
            return delegate.getMdc();
        }

        @Override
        public long getTimeStamp() {
            return delegate.getTimeStamp();
        }

        @Override
        public int getNanoseconds() {
            return delegate.getNanoseconds();
        }

        @Override
        public long getSequenceNumber() {
            return delegate.getSequenceNumber();
        }

        @Override
        public List<KeyValuePair> getKeyValuePairs() {
            return delegate.getKeyValuePairs();
        }

        @Override
        public void prepareForDeferredProcessing() {
            delegate.prepareForDeferredProcessing();
        }
    }
}
