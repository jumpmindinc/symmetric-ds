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
package org.jumpmind.symmetric.job;

public class JobDefaults {
    public static final String EVERY_5_MINUTES = "0 0/5 * * * *";
    public static final String EVERY_10_SECONDS = "10000";
    public static final String EVERY_30_SECONDS = "30000";
    public static final String EVERY_MINUTE = "60000";
    public static final String EVERY_FIFTEEN_MINUTES = "900000";
    public static final String EVERY_HOUR = "3600000";
    public static final String EVERY_NIGHT_AT_MIDNIGHT = "0 0 0 * * *";
    public static final String EVERY_TEN_MINUTES_AT_THE_ONE_OCLOCK_HOUR = "0 0/10 1 * * *";
    private String schedule;
    private boolean requiresRegisteration = true;
    private String description;
    private boolean enabled = true;

    public JobDefaults() {
    }

    public JobDefaults schedule(String schedule) {
        this.schedule = schedule;
        return this;
    }

    public JobDefaults enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public JobDefaults requiresRegisteration(boolean requiresRegisteration) {
        this.requiresRegisteration = requiresRegisteration;
        return this;
    }

    public JobDefaults description(String description) {
        this.description = description;
        return this;
    }

    public String getSchedule() {
        return schedule;
    }

    public boolean isRequiresRegisteration() {
        return requiresRegisteration;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static String getJobNameParameter(String name) {
        if (name != null) {
            return name.toLowerCase().replace(' ', '.');
        } else {
            return null;
        }
    }

    public static String getStartParameter(String name) {
        return String.format("start.%s.job", getJobNameParameter(name));
    }

    public static String getPeriodicParameter(String name) {
        return String.format("job.%s.period.time.ms", getJobNameParameter(name));
    }

    public static String getCronParameter(String name) {
        return String.format("job.%s.cron", getJobNameParameter(name));
    }
}
