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

import java.util.Date;

import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.model.JobDefinition;

public interface IJob extends IExtensionPoint {
    public String getName();

    public JobDefinition getJobDefinition();

    public boolean isCronSchedule();

    public boolean isPeriodicSchedule();

    public String getSchedule();

    public void start();

    public boolean stop();

    public void pause();

    public void unpause();

    public boolean isPaused();

    public boolean isStarted();

    public long getLastExecutionTimeInMs();

    public Date getLastFinishTime();

    public Date getNextExecutionTime();

    public boolean isRunning();

    public long getNumberOfRuns();

    public long getTotalExecutionTimeInMs();

    public long getAverageExecutionTimeInMs();

    public boolean invoke(boolean force);

    public boolean isRateLimited();

    public String getDeprecatedStartParameter();
}