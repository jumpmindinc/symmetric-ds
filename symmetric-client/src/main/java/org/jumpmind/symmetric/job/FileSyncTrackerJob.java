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

import static org.jumpmind.symmetric.job.JobDefaults.EVERY_5_MINUTES;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class FileSyncTrackerJob extends AbstractJob {
    public FileSyncTrackerJob(ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(ClusterConstants.FILE_SYNC_TRACKER, engine, taskScheduler);
    }

    @Override
    public JobDefaults getDefaults() {
        boolean fileSyncEnabeld = engine.getParameterService().is(ParameterConstants.FILE_SYNC_ENABLE);
        return new JobDefaults()
                .schedule(EVERY_5_MINUTES)
                .enabled(fileSyncEnabeld)
                .description("Check for changes in sync'd files");
    }

    @Override
    public void doJob(boolean force) throws Exception {
        if (engine != null) {
            engine.getFileSyncService().trackChanges(force);
        }
    }
}
