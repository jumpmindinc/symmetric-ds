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

import static org.jumpmind.symmetric.job.JobDefaults.EVERY_HOUR;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.JobDefinition.JobType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import bsh.Interpreter;

public class BshJob extends AbstractJob {
    public BshJob(String jobName, ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(jobName, engine, taskScheduler);
    }

    public JobType getJobType() {
        return JobType.BSH;
    }

    @Override
    public void doJob(boolean force) throws Exception {
        try {
            Interpreter interpreter = new Interpreter();
            interpreter.set("engine", engine);
            interpreter.set("sqlTemplate", engine.getDatabasePlatform().getSqlTemplate());
            interpreter.set("log", log);
            if (getJobDefinition().getJobExpression() != null) {
                interpreter.eval(getJobDefinition().getJobExpression());
            }
        } catch (Exception ex) {
            log.error("Exception during bsh job '" + this.getName() + "'\n" + getJobDefinition().getJobExpression(), ex);
        }
    }

    @Override
    public JobDefaults getDefaults() {
        return new JobDefaults();
    }

    @Override
    protected long getMinSchedulePeriodMs() {
        return Long.parseLong(EVERY_HOUR);
    }

    @Override
    public boolean isRateLimited() {
        return true;
    }
}
