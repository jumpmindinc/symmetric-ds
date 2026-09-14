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

import java.util.List;

import org.jumpmind.symmetric.model.JobDefinition;

/*
 * An API that provides access to individual jobs and provides job
 * life cycle control
 */
public interface IJobManager {
    public void init();

    public void startJobs();

    public void stopJobs();

    public void destroy();

    public List<JobDefinition> getCustomJobDefinitions();

    public List<IJob> getJobs();

    public IJob getJob(String name);

    public void saveJob(JobDefinition jobDefinition);

    public void saveJobAsCopy(JobDefinition jobDefinition);

    public void renameJob(String oldName, JobDefinition jobDefinition);

    public void removeJob(String name);

    public void removeAllJobs();

    public boolean isStarted();

    public boolean isJobApplicableToNodeGroup(IJob job);

    public void restartJobs();

    public void restartJob(String name);
}