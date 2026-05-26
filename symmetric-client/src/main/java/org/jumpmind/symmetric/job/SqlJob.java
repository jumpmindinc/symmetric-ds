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

import static org.jumpmind.symmetric.job.JobDefaults.EVERY_FIFTEEN_MINUTES;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlScript;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.TokenConstants;
import org.jumpmind.symmetric.model.JobDefinition.JobType;
import org.jumpmind.symmetric.model.Node;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SqlJob extends AbstractJob {
    static final boolean AUTO_COMMIT = true;

    public SqlJob(String jobName, ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(jobName, engine, taskScheduler);
    }

    public JobType getJobType() {
        return JobType.SQL;
    }

    @Override
    protected void doJob(boolean force) throws Exception {
        try {
            if (getJobDefinition().getJobExpression() != null) {
                ISqlTemplate sqlTemplate = engine.getDatabasePlatform().getSqlTemplate();
                Map<String, String> replacementTokens = getReplacementTokens(engine, engine.getSymmetricDialect().getPlatform()
                        .getSqlScriptReplacementTokens());
                SqlScript script = new SqlScript(getJobDefinition().getJobExpression(), sqlTemplate, true, replacementTokens);
                script.execute(AUTO_COMMIT);
            }
        } catch (Exception ex) {
            log.error("Exception during sql job '" + this.getName() + "'\n" + getJobDefinition().getJobExpression(), ex);
        }
    }

    @Override
    public JobDefaults getDefaults() {
        return new JobDefaults();
    }

    protected Map<String, String> getReplacementTokens(ISymmetricEngine engine, Map<String, String> startingReplacementTokens) {
        Map<String, String> replacementTokens = new HashMap<String, String>();
        if (startingReplacementTokens != null) {
            replacementTokens.putAll(startingReplacementTokens);
        }
        replacementTokens.put(TokenConstants.NODE_ID, engine.getNodeId());
        Node identity = engine.getNodeService().findIdentity();
        if (identity != null) {
            replacementTokens.put(TokenConstants.NODE_GROUP_ID, identity.getNodeGroupId());
        }
        return replacementTokens;
    }

    @Override
    protected long getMinSchedulePeriodMs() {
        return Long.parseLong(EVERY_FIFTEEN_MINUTES);
    }

    @Override
    public boolean isRateLimited() {
        return true;
    }
}
