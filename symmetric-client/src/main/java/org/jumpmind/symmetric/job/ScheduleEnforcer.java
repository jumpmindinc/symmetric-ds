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
package org.jumpmind.symmetric.job;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * Handles schedule parsing and minimum period enforcement for job schedules. Supports both periodic (millisecond-based) and cron schedules.
 */
public class ScheduleEnforcer {
    private static final Logger log = LoggerFactory.getLogger(ScheduleEnforcer.class);

    /**
     * Enforces a minimum period on the given schedule. If the schedule would run more frequently than the minimum period, returns the minimum period as a
     * periodic schedule instead.
     *
     * @param schedule
     *            the configured schedule (periodic milliseconds or cron expression)
     * @param minPeriodMs
     *            the minimum allowed period in milliseconds, or 0 for no minimum
     * @return the effective schedule after enforcement
     */
    public String enforceMinimum(String schedule, long minPeriodMs) {
        if (minPeriodMs <= 0 || StringUtils.isEmpty(schedule)) {
            return schedule;
        }
        if (isPeriodicSchedule(schedule)) {
            long period = Long.parseLong(schedule);
            if (period > 0 && period < minPeriodMs) {
                return String.valueOf(minPeriodMs);
            }
        } else {
            long cronInterval = getCronIntervalMs(schedule);
            if (cronInterval > 0 && cronInterval < minPeriodMs) {
                return String.valueOf(minPeriodMs);
            }
        }
        return schedule;
    }

    /**
     * Checks if the given schedule is a periodic schedule (all digits representing milliseconds).
     *
     * @param schedule
     *            the schedule to check
     * @return true if the schedule is periodic, false if it's a cron expression
     */
    public boolean isPeriodicSchedule(String schedule) {
        return NumberUtils.isDigits(schedule);
    }

    /**
     * Checks if the given schedule is a cron schedule.
     *
     * @param schedule
     *            the schedule to check
     * @return true if the schedule is a cron expression, false if it's periodic
     */
    public boolean isCronSchedule(String schedule) {
        return !StringUtils.isEmpty(schedule) && !isPeriodicSchedule(schedule);
    }

    /**
     * Gets the interval in milliseconds for a periodic schedule.
     *
     * @param schedule
     *            the periodic schedule (milliseconds as string)
     * @return the interval in milliseconds, or -1 if invalid
     */
    public long getPeriodicIntervalMs(String schedule) {
        if (isPeriodicSchedule(schedule)) {
            try {
                return Long.parseLong(schedule);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Calculates the interval in milliseconds between consecutive executions of a cron schedule. Note: This calculates the interval between the next two
     * executions from now, which may not represent the typical interval for schedules with variable frequencies.
     *
     * @param cronExpression
     *            the cron expression
     * @return the interval in milliseconds, or -1 if the interval cannot be determined
     */
    public long getCronIntervalMs(String cronExpression) {
        try {
            CronTrigger trigger = new CronTrigger(cronExpression);
            SimpleTriggerContext context = new SimpleTriggerContext();
            Instant first = trigger.nextExecution(context);
            if (first != null) {
                context = new SimpleTriggerContext(first, first, first);
                Instant second = trigger.nextExecution(context);
                if (second != null) {
                    return second.toEpochMilli() - first.toEpochMilli();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse cron expression '{}': {}", cronExpression, e.getMessage());
        }
        return -1;
    }

    /**
     * Gets the interval in milliseconds for any schedule type.
     *
     * @param schedule
     *            the schedule (periodic or cron)
     * @return the interval in milliseconds, or -1 if it cannot be determined
     */
    public long getIntervalMs(String schedule) {
        if (StringUtils.isEmpty(schedule)) {
            return -1;
        }
        if (isPeriodicSchedule(schedule)) {
            return getPeriodicIntervalMs(schedule);
        } else {
            return getCronIntervalMs(schedule);
        }
    }

    /**
     * Checks if the given schedule exceeds the allowed frequency limit (i.e., runs more frequently than the minimum period).
     *
     * @param configuredSchedule
     *            the original configured schedule
     * @param minPeriodMs
     *            the minimum allowed period in milliseconds
     * @return true if the schedule exceeds the limit, false otherwise
     */
    public boolean exceedsScheduleLimit(String configuredSchedule, long minPeriodMs) {
        if (minPeriodMs <= 0 || StringUtils.isEmpty(configuredSchedule)) {
            return false;
        }
        long interval = getIntervalMs(configuredSchedule);
        return interval > 0 && interval < minPeriodMs;
    }
}
