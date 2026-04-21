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
package org.jumpmind.symmetric.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.jumpmind.util.LogSummary;
import org.jumpmind.util.LogSummaryAppender;
import org.jumpmind.util.LogbackHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.event.Level;

/**
 * Compiles against slf4j and only instantiates the helper if Logback is present.
 */
public class LogSummaryAppenderUtils {
    private static final String LOG_SUMMARY_APPENDER_NAME = "SUMMARY";
    private static final Logger log = LoggerFactory.getLogger(LogSummaryAppenderUtils.class);
    private static final List<LogSummary> EMPTY_LIST = new ArrayList<>();
    private static LogbackHelper helper;

    private LogSummaryAppenderUtils() {
    }

    static {
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");
        SLF4JBridgeHandler.install();
        try {
            Class.forName("ch.qos.logback.classic.LoggerContext", false,
                    LogSummaryAppenderUtils.class.getClassLoader());
            helper = new LogbackHelper();
        } catch (ClassNotFoundException e) {
        }
    }

    public static void initialize(boolean isDebug, boolean isVerbose, boolean isNoConsole, boolean isNoLog, String overrideLogFileName) {
        if (helper != null) {
            helper.initialize(isDebug);
            if (isVerbose) {
                helper.registerVerboseConsoleAppender();
            }
            if (isNoConsole) {
                helper.removeAppender("CONSOLE");
                helper.removeAppender("CONSOLE_ERR");
            }
            if (!isVerbose && !isNoConsole) {
                helper.registerConsoleAppender();
            }
            if (isNoLog) {
                helper.removeAppender("ROLLING");
            } else {
                helper.registerRollingFileAppender(overrideLogFileName);
            }
        }
    }

    public static void initialize() {
        if (helper != null) {
            try {
                LogSummaryAppender appender = getLogSummaryAppender();
                if (appender == null) {
                    helper.registerLogSummaryAppenderInternal(LOG_SUMMARY_APPENDER_NAME);
                }
            } catch (NoSuchMethodError | RuntimeException e) {
                helper = null;
                log.debug("Disabling Logback helper", e);
            }
        }
    }

    public static LogSummaryAppender getLogSummaryAppender() {
        LogSummaryAppender appender = null;
        if (helper != null) {
            try {
                appender = (LogSummaryAppender) helper.getAppender(LOG_SUMMARY_APPENDER_NAME);
            } catch (Exception e) {
                // Can get ClassCastException if the app has been recycled in the same container
                log.debug("Failed to load appender " + LOG_SUMMARY_APPENDER_NAME, e);
                try {
                    helper.removeAppender(LOG_SUMMARY_APPENDER_NAME);
                } catch (Exception ex) {
                    log.debug("Failed to remove appender " + LOG_SUMMARY_APPENDER_NAME, ex);
                }
                try {
                    appender = helper.registerLogSummaryAppenderInternal(LOG_SUMMARY_APPENDER_NAME);
                } catch (Exception ex) {
                    helper = null;
                    log.debug("Disabling Logback helper — SLF4J is not bound to Logback in this context", ex);
                }
            }
        }
        return appender;
    }

    public static Appender<ILoggingEvent> getConsoleViewerAppender() {
        if (helper != null) {
            return helper.getAppender("CONSOLE_VIEWER");
        }
        return null;
    }

    public static void addAppender(Appender<ILoggingEvent> appender) {
        if (helper != null) {
            helper.addAppender(appender);
        }
    }

    public static void clearAllLogSummaries(String engineName) {
        LogSummaryAppender appender = getLogSummaryAppender();
        if (appender != null) {
            appender.clearAll(engineName);
        }
    }

    public static List<LogSummary> getLogSummaryWarnings(String engineName) {
        return getLogSummaries(engineName, Level.WARN);
    }

    public static List<LogSummary> getLogSummaryErrors(String engineName) {
        return getLogSummaries(engineName, Level.ERROR);
    }

    public static List<LogSummary> getLogSummaries(String engineName, Level level) {
        LogSummaryAppender appender = getLogSummaryAppender();
        if (appender != null) {
            return appender.getLogSummaries(engineName, helper.convertToLevel(level));
        } else {
            return EMPTY_LIST;
        }
    }

    public static File getLogDir() {
        if (helper != null) {
            return helper.getLogDir();
        }
        return null;
    }

    public static File getLogFile() {
        if (helper != null) {
            return helper.getLogFile();
        }
        return null;
    }

    public static boolean isDefaultLogLayoutPattern() {
        if (helper != null) {
            return helper.isDefaultLogLayoutPattern();
        }
        return false;
    }

    public static void setLevel(String loggerName, Level level) {
        if (helper != null) {
            helper.setLevel(loggerName, level);
        }
    }

    public static Level getLevel(String loggerName) {
        if (helper != null) {
            return helper.getLevel(loggerName);
        }
        return Level.INFO;
    }

    public static Level getRootLevel() {
        if (helper != null) {
            return helper.getRootLevel();
        }
        return Level.INFO;
    }

    public static void addProtectedLogger(String loggerName, Level minimumLevel) {
        if (helper != null) {
            helper.addProtectedLogger(loggerName, helper.convertToLevel(minimumLevel));
        }
    }
}
