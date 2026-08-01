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

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.jumpmind.db.sql.ISqlTemplate;
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
    private final Supplier<ISqlTemplate> sqlTemplateSupplier;
    private final IParameterService parameterService;
    private final LongSupplier clock;
    private final ReentrantLock testLock = new ReentrantLock();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile DbHealthCheckResult lastResult;
    private volatile long unhealthyUntilMs;

    public DatabaseHealthTracker(Supplier<ISqlTemplate> sqlTemplateSupplier, IParameterService parameterService) {
        this(sqlTemplateSupplier, parameterService, System::currentTimeMillis);
    }

    DatabaseHealthTracker(Supplier<ISqlTemplate> sqlTemplateSupplier, IParameterService parameterService, LongSupplier clock) {
        this.sqlTemplateSupplier = sqlTemplateSupplier;
        this.parameterService = parameterService;
        this.clock = clock;
    }

    @Override
    public boolean isRuntimeDbHealthy() {
        if (!parameterService.is(ParameterConstants.DB_HEALTH_CHECK_ENABLED, true)) {
            return true;
        }
        if (isDeclaredUnhealthy() && clock.getAsLong() < unhealthyUntilMs) {
            return false;
        }
        if (!testLock.tryLock()) {
            return !isDeclaredUnhealthy();
        }
        try {
            if (isDeclaredUnhealthy()) {
                if (clock.getAsLong() < unhealthyUntilMs) {
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
        return lastResult;
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
        log.debug("Runtime database connection test failed {} of {} times: {}", failures, failureThreshold, lastResult.result());
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
        unhealthyUntilMs = clock.getAsLong() + waitMs;
        log.info("Runtime database is still unhealthy, next connection test in {} ms: {}", waitMs, lastResult.result());
        return false;
    }

    private void declareUnhealthy(int failures) {
        long waitMs = getUnhealthyWaitMs();
        unhealthyUntilMs = clock.getAsLong() + waitMs;
        updateEngineReadiness(false);
        log.warn("Runtime database is unhealthy after {} consecutive connection failures, pausing jobs and incoming syncs for {} ms: {}",
                failures, waitMs, lastResult.result());
    }

    private boolean testConnection() {
        try {
            sqlTemplateSupplier.get().testConnection();
            lastResult = new DbHealthCheckResult(new Date(), true, HEALTHY_RESULT);
            return true;
        } catch (Exception e) {
            lastResult = new DbHealthCheckResult(new Date(), false, ExceptionUtils.getRootCauseMessage(e));
            return false;
        }
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
        long healthTimeoutSeconds = parameterService.getLong(ParameterConstants.DB_HEALTH_CHECK_TIMEOUT_SECONDS, DEFAULT_DB_HEALTH_TIMEOUT_SECONDS);
        if (healthTimeoutSeconds <= 0) {
            healthTimeoutSeconds = DEFAULT_DB_HEALTH_TIMEOUT_SECONDS;
        }
        return getFailureThreshold() * healthTimeoutSeconds * DateUtils.MILLIS_PER_SECOND;
    }

    private int getFailureThreshold() {
        return Math.max(1, parameterService.getInt(ParameterConstants.DB_HEALTH_CHECK_FAILURE_THRESHOLD, DEFAULT_FAILURE_THRESHOLD));
    }
}
