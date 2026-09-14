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

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.FileSize;

/**
 * Compiles against Logback classes, so only instantiate this class if Logback is present.
 */
public class LogbackHelper {
    private static final Logger log = LoggerFactory.getLogger(LogbackHelper.class);
    private static final String DEFAULT_LOG_PATTERN = "%d %p [%X{engineName}] [%property{HOSTNAME}] [%c{0}] [%t] %m%n";
    private static final String CONSOLE_LOG_PATTERN = "%d %p [%X{engineName}] [%property{HOSTNAME}] [%c{0}] [%t] %m%ex%n";
    private static final String VERBOSE_CONSOLE_LOG_PATTERN = "%d %-5p [%X{engineName}] [%property{HOSTNAME}] [%c{0}] [%t] %m%ex%n";
    private static final String APPENDER_CONSOLE = "CONSOLE";
    private static final String APPENDER_CONSOLE_ERR = "CONSOLE_ERR";
    private static final String APPENDER_ROLLING = "ROLLING";
    private final Map<String, Level> protectedLoggers = new HashMap<>();

    public void initialize(boolean isDebug) {
        File confDir = new File(AppUtils.getSymHome() + "/conf");
        File logbackFile = new File(confDir, isDebug ? "logback-debug.xml" : "logback.xml");
        if (logbackFile.exists()) {
            LoggerContext context = getContext();
            JoranConfigurator joranConfigurator = new JoranConfigurator();
            joranConfigurator.setContext(context);
            context.reset();
            try {
                joranConfigurator.doConfigure(logbackFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to configure Logback from {}: {}", logbackFile.getAbsolutePath(), e.getMessage());
            }
            context.putProperty("HOSTNAME", AppUtils.getHostName());
            enforceProtectedLoggers();
        } else {
            logNonExistentLoggingConfigurations(isDebug);
        }
    }

    public LogSummaryAppender registerLogSummaryAppenderInternal(String name) {
        LogSummaryAppender appender = new LogSummaryAppender(name);
        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(Level.WARN.toString());
        filter.setContext(getContext());
        filter.start();
        appender.addFilter(filter);
        addAppender(appender);
        return appender;
    }

    public void registerConsoleAppender() {
        removeAppender(APPENDER_CONSOLE);
        removeAppender(APPENDER_CONSOLE_ERR);
        addAppender(buildConsoleAppender(APPENDER_CONSOLE, "System.out", CONSOLE_LOG_PATTERN,
                buildLevelFilter(Level.WARN, FilterReply.DENY, FilterReply.NEUTRAL),
                buildLevelFilter(Level.ERROR, FilterReply.DENY, FilterReply.ACCEPT)));
        addAppender(buildConsoleAppender(APPENDER_CONSOLE_ERR, "System.err", CONSOLE_LOG_PATTERN,
                buildThresholdFilter(Level.WARN.toString())));
    }

    public void registerVerboseConsoleAppender() {
        removeAppender(APPENDER_CONSOLE);
        removeAppender(APPENDER_CONSOLE_ERR);
        addAppender(buildConsoleAppender(APPENDER_CONSOLE, "System.out", VERBOSE_CONSOLE_LOG_PATTERN,
                buildLevelFilter(Level.WARN, FilterReply.DENY, FilterReply.NEUTRAL),
                buildLevelFilter(Level.ERROR, FilterReply.DENY, FilterReply.ACCEPT)));
        addAppender(buildConsoleAppender(APPENDER_CONSOLE_ERR, "System.err", VERBOSE_CONSOLE_LOG_PATTERN,
                buildThresholdFilter(Level.WARN.toString())));
    }

    @SafeVarargs
    private ConsoleAppender<ILoggingEvent> buildConsoleAppender(String name, String target, String pattern,
            Filter<ILoggingEvent>... filters) {
        LoggerContext context = getContext();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.start();
        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setName(name);
        appender.setTarget(target);
        appender.setEncoder(encoder);
        for (Filter<ILoggingEvent> filter : filters) {
            appender.addFilter(filter);
        }
        appender.start();
        return appender;
    }

    private LevelFilter buildLevelFilter(Level level, FilterReply onMatch, FilterReply onMismatch) {
        LevelFilter filter = new LevelFilter();
        filter.setLevel(level);
        filter.setOnMatch(onMatch);
        filter.setOnMismatch(onMismatch);
        filter.setContext(getContext());
        filter.start();
        return filter;
    }

    private ThresholdFilter buildThresholdFilter(String level) {
        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(level);
        filter.setContext(getContext());
        filter.start();
        return filter;
    }

    public void registerRollingFileAppender(String overrideLogFileName) {
        Appender<ILoggingEvent> existing = getAppender(APPENDER_ROLLING);
        if (existing instanceof SymRollingFileAppender fileAppender) {
            String fileName = fileAppender.getFile();
            if (overrideLogFileName != null) {
                fileName = fileName.replace("symmetric.log", overrideLogFileName);
                String newFilePattern = ((FixedWindowRollingPolicy) fileAppender.getRollingPolicy())
                        .getFileNamePattern().replace("symmetric.log", overrideLogFileName);
                LoggerContext context = getContext();
                SymRollingFileAppender newAppender = buildRollingFileAppender(context, fileName, newFilePattern);
                removeAppender(APPENDER_ROLLING);
                addAppender(newAppender);
            }
            log.info("Log output will be written to {}", fileName);
        }
    }

    private SymRollingFileAppender buildRollingFileAppender(LoggerContext context, String fileName, String filePattern) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(DEFAULT_LOG_PATTERN);
        encoder.start();
        FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(filePattern);
        rollingPolicy.setMinIndex(1);
        rollingPolicy.setMaxIndex(3);
        SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
        triggeringPolicy.setContext(context);
        triggeringPolicy.setMaxFileSize(FileSize.valueOf("20MB"));
        triggeringPolicy.start();
        SymRollingFileAppender appender = new SymRollingFileAppender();
        appender.setContext(context);
        appender.setName(APPENDER_ROLLING);
        appender.setFile(fileName);
        appender.setRollingPolicy(rollingPolicy);
        appender.setTriggeringPolicy(triggeringPolicy);
        appender.setEncoder(encoder);
        rollingPolicy.setParent(appender);
        rollingPolicy.start();
        appender.start();
        return appender;
    }

    public Appender<ILoggingEvent> getAppender(String name) {
        ch.qos.logback.classic.Logger rootLogger = getRootLogger();
        Iterator<Appender<ILoggingEvent>> appenderIterator = rootLogger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            if (name.equals(appender.getName())) {
                return appender;
            }
        }
        return null;
    }

    public void addAppender(Appender<ILoggingEvent> appender) {
        try {
            LoggerContext context = getContext();
            appender.setContext(context);
            if (!appender.isStarted()) {
                appender.start();
            }
            getRootLogger().addAppender(appender);
        } catch (Exception ex) {
            log.debug("Failed to add appender " + appender.getName(), ex);
        }
    }

    public void removeAppender(String name) {
        getRootLogger().detachAppender(name);
    }

    public File getLogDir() {
        File logFile = getLogFile();
        return logFile != null ? logFile.getParentFile() : null;
    }

    public File getLogFile() {
        ch.qos.logback.classic.Logger rootLogger = getRootLogger();
        Iterator<Appender<ILoggingEvent>> appenderIterator = rootLogger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            if (appender instanceof FileAppender<?> fileAppender) {
                String fileName = fileAppender.getFile();
                if (fileName != null) {
                    return new File(fileName);
                }
            }
        }
        return null;
    }

    public boolean isDefaultLogLayoutPattern() {
        ch.qos.logback.classic.Logger rootLogger = getRootLogger();
        Iterator<Appender<ILoggingEvent>> appenderIterator = rootLogger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            if (appender instanceof FileAppender<?> fileAppender) {
                Encoder<?> encoder = fileAppender.getEncoder();
                if (encoder instanceof PatternLayoutEncoder patternLayoutEncoder) {
                    return DEFAULT_LOG_PATTERN.equals(patternLayoutEncoder.getPattern());
                }
            }
        }
        return false;
    }

    public void setLevel(String loggerName, org.slf4j.event.Level level) {
        Level requestedLevel = convertToLevel(level);
        Level minimumLevel = protectedLoggers.get(loggerName);
        if (minimumLevel != null && !minimumLevel.isGreaterOrEqual(requestedLevel)) {
            return;
        }
        getContext().getLogger(loggerName).setLevel(requestedLevel);
    }

    public org.slf4j.event.Level getLevel(String loggerName) {
        return convertFromLevel(getContext().getLogger(loggerName).getEffectiveLevel());
    }

    public org.slf4j.event.Level getRootLevel() {
        return convertFromLevel(getRootLogger().getEffectiveLevel());
    }

    public void addProtectedLogger(String loggerName, Level minimumLevel) {
        protectedLoggers.put(loggerName, minimumLevel);
        enforceProtectedLoggers();
    }

    private void enforceProtectedLoggers() {
        for (Map.Entry<String, Level> entry : protectedLoggers.entrySet()) {
            ch.qos.logback.classic.Logger logger = getContext().getLogger(entry.getKey());
            if (!entry.getValue().isGreaterOrEqual(logger.getEffectiveLevel())) {
                logger.setLevel(entry.getValue());
            }
        }
    }

    public org.slf4j.event.Level convertFromLevel(Level level) {
        if (level == Level.TRACE) {
            return org.slf4j.event.Level.TRACE;
        }
        if (level == Level.DEBUG) {
            return org.slf4j.event.Level.DEBUG;
        }
        if (level == null || level == Level.INFO) {
            return org.slf4j.event.Level.INFO;
        }
        if (level == Level.WARN) {
            return org.slf4j.event.Level.WARN;
        }
        return org.slf4j.event.Level.ERROR;
    }

    public Level convertToLevel(org.slf4j.event.Level level) {
        if (level == org.slf4j.event.Level.TRACE) {
            return Level.TRACE;
        }
        if (level == org.slf4j.event.Level.DEBUG) {
            return Level.DEBUG;
        }
        if (level == org.slf4j.event.Level.INFO) {
            return Level.INFO;
        }
        if (level == org.slf4j.event.Level.WARN) {
            return Level.WARN;
        }
        return Level.ERROR;
    }

    private LoggerContext getContext() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext)) {
            throw new IllegalStateException("SLF4J is not bound to Logback in this context: " + factory.getClass().getName());
        }
        return (LoggerContext) factory;
    }

    private void logNonExistentLoggingConfigurations(boolean isDebug) {
        String logFile = isDebug ? "logback-debug.xml" : "logback.xml";
        log.warn("No conf/{} file exists! Possible failures: Symmetric Installation OR Log4j2_Logback migration.", logFile);
    }

    private ch.qos.logback.classic.Logger getRootLogger() {
        return getContext().getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
