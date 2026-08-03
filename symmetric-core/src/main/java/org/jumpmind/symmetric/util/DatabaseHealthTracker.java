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

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.symmetric.ApplicationHealthTracker;
import org.jumpmind.symmetric.IApplicationHealthTracker;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.DbHealthCheckResult;
import org.jumpmind.symmetric.service.IParameterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @see IDatabaseHealthTracker
 */
public class DatabaseHealthTracker implements IDatabaseHealthTracker {
    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthTracker.class);
    private static final String HEALTHY_RESULT = "OK";
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_DB_HEALTH_TIMEOUT_SECONDS = 60;
    private static final long DEFAULT_TEST_TIMEOUT_MS = 30000;
    private final AtomicInteger testThreadNumber = new AtomicInteger(1);
    private final ExecutorService testExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "db-health-check-" + testThreadNumber.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });
    private final Supplier<ISqlTemplate> sqlTemplateSupplier;
    private final IParameterService parameterService;
    private final LongSupplier currentSystemTime;
    private final ReentrantLock testLock = new ReentrantLock();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile DbHealthCheckResult lastDbCheckResult;
    private volatile long unhealthyUntilMs;

    public DatabaseHealthTracker(Supplier<ISqlTemplate> sqlTemplateSupplier, IParameterService parameterService) {
        this(sqlTemplateSupplier, parameterService, System::currentTimeMillis);
    }

    DatabaseHealthTracker(Supplier<ISqlTemplate> sqlTemplateSupplier, IParameterService parameterService, LongSupplier currentSystemTime) {
        this.sqlTemplateSupplier = sqlTemplateSupplier;
        this.parameterService = parameterService;
        this.currentSystemTime = currentSystemTime;
    }

    @Override
    public boolean isRuntimeDbHealthy() {
        if (!parameterService.is(ParameterConstants.DB_HEALTH_CHECK_ENABLED, true)) {
            return true;
        }
        if (isDeclaredUnhealthy() && currentSystemTime.getAsLong() < unhealthyUntilMs) {
            return false;
        }
        if (!testLock.tryLock()) {
            return !isDeclaredUnhealthy();
        }
        try {
            if (isDeclaredUnhealthy()) {
                if (currentSystemTime.getAsLong() < unhealthyUntilMs) {
                    return false;
                }
                return attemptRecovery();
            }
            return testWhileHealthy();
        } finally {
            testLock.unlock();
        }
    }

    @Override
    public DbHealthCheckResult getLastResult() {
        return lastDbCheckResult;
    }

    private boolean testWhileHealthy() {
        if (testConnection()) {
            consecutiveFailures.set(0);
            return true;
        }
        int failures = consecutiveFailures.incrementAndGet();
        int failureThreshold = getFailureThreshold();
        if (failures >= failureThreshold) {
            declareUnhealthy(failures);
            return false;
        }
        log.debug("Runtime database connection test failed {} of {} times: {}", failures, failureThreshold, lastDbCheckResult.result());
        return true;
    }

    private boolean attemptRecovery() {
        if (testConnection() && testConnection()) {
            int failures = consecutiveFailures.getAndSet(0);
            updateEngineReadiness(true);
            log.info("Runtime database connection restored after {} consecutive failures", failures);
            return true;
        }
        consecutiveFailures.incrementAndGet();
        long waitMs = getUnhealthyWaitMs();
        unhealthyUntilMs = currentSystemTime.getAsLong() + waitMs;
        log.info("Runtime database is still unhealthy, next connection test in {} ms: {}", waitMs, lastDbCheckResult.result());
        return false;
    }

    private void declareUnhealthy(int failures) {
        long waitMs = getUnhealthyWaitMs();
        unhealthyUntilMs = currentSystemTime.getAsLong() + waitMs;
        updateEngineReadiness(false);
        log.warn("Runtime database is unhealthy after {} consecutive connection failures, pausing jobs and incoming syncs for {} ms: {}",
                failures, waitMs, lastDbCheckResult.result());
    }

    private boolean testConnection() {
        long timeoutMs = getTestTimeoutMs();
        // run on a daemon thread with a hard timeout because a physical connect attempt is not bounded by the pool wait
        Future<?> test = testExecutor.submit(() -> sqlTemplateSupplier.get().testConnection());
        try {
            test.get(timeoutMs, TimeUnit.MILLISECONDS);
            recordResult(true, HEALTHY_RESULT);
            return true;
        } catch (TimeoutException e) {
            test.cancel(true);
            recordResult(false, "Connection test timed out after " + timeoutMs + " ms");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recordResult(false, ExceptionUtils.getRootCauseMessage(e));
            return false;
        } catch (Exception e) {
            recordResult(false, ExceptionUtils.getRootCauseMessage(e));
            return false;
        }
    }

    private void recordResult(boolean isHealthy, String result) {
        lastDbCheckResult = new DbHealthCheckResult(Instant.ofEpochMilli(currentSystemTime.getAsLong()), isHealthy, result);
    }

    private long getTestTimeoutMs() {
        long timeoutMs = parameterService.getLong(DataSourceProperties.DB_POOL_MAX_WAIT, DEFAULT_TEST_TIMEOUT_MS);
        return timeoutMs > 0 ? timeoutMs : DEFAULT_TEST_TIMEOUT_MS;
    }

    private void updateEngineReadiness(boolean isReady) {
        IApplicationHealthTracker appHealthTracker = ApplicationHealthTracker.getTracker();
        if (appHealthTracker != null) {
            String engineName = parameterService.getEngineName();
            if (appHealthTracker.getReadinessMap().get(engineName) != null) {
                appHealthTracker.setEngineReadiness(engineName, isReady);
            }
        }
    }

    private boolean isDeclaredUnhealthy() {
        return consecutiveFailures.get() >= getFailureThreshold();
    }

    private long getUnhealthyWaitMs() {
        long healthTimeoutSeconds = parameterService.getLong(ParameterConstants.DB_HEALTH_CHECK_RETRY_SECONDS, DEFAULT_DB_HEALTH_TIMEOUT_SECONDS);
        if (healthTimeoutSeconds <= 0) {
            healthTimeoutSeconds = DEFAULT_DB_HEALTH_TIMEOUT_SECONDS;
        }
        return healthTimeoutSeconds * DateUtils.MILLIS_PER_SECOND;
    }

    private int getFailureThreshold() {
        return Math.max(1, parameterService.getInt(ParameterConstants.DB_HEALTH_CHECK_FAILURE_THRESHOLD, DEFAULT_FAILURE_THRESHOLD));
    }
}
