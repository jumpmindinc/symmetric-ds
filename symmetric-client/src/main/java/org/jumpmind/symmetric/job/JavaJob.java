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

import static org.jumpmind.symmetric.job.JobDefaults.EVERY_FIFTEEN_MINUTES;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.model.JobDefinition.JobType;
import org.jumpmind.util.SimpleClassCompiler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class JavaJob extends AbstractJob {
    private JavaJob configuredJob;
    protected ISqlTemplate sqlTemplate;

    public JavaJob() {
        super();
    }

    public JavaJob(String jobName, ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        super(jobName, engine, taskScheduler);
    }

    public JobType getJobType() {
        return JobType.JAVA;
    }

    @Override
    public JobDefaults getDefaults() {
        return new JobDefaults();
    }

    @Override
    public void doJob(boolean force) throws Exception {
        if (configuredJob == null) {
            configuredJob = compileJob();
        }
        if (configuredJob != null) {
            configuredJob.doJob(force);
        }
    }

    protected JavaJob compileJob() {
        String jobExression = getJobDefinition().getJobExpression();
        if (StringUtils.isEmpty(jobExression)) {
            return null;
        }
        if (!SimpleClassCompiler.isJdkAvailable()) {
            log.warn("Skipping Java job '{}': Java compilation is not available in restricted security mode (JRE only). Configure a BSH job instead.",
                    getJobDefinition().getJobName());
            return null;
        }
        final String code = CODE_START + jobExression + CODE_END;
        SimpleClassCompiler compiler = new SimpleClassCompiler();
        try {
            JavaJob job = (JavaJob) compiler.getCompiledClass(code);
            job.setEngine(engine);
            job.setSqlTemplate(engine.getSqlTemplate());
            job.setJobName(getJobName());
            job.setJobDefinition(getJobDefinition());
            job.setParameterService(engine.getParameterService());
            job.setTaskScheduler(getTaskScheduler());
            return job;
        } catch (Exception ex) {
            throw new SymmetricException("Failed to compile Java code for job " +
                    getJobDefinition().getJobName() + " code: \n" + code, ex);
        }
    }

    public final static String CODE_START = "import org.jumpmind.symmetric.job.JavaJob;\n" +
            "import org.apache.commons.lang3.StringUtils;\n" +
            "import org.jumpmind.symmetric.ISymmetricEngine;\n" +
            "import org.jumpmind.symmetric.model.JobDefinition.JobType;\n" +
            "import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;\n" +
            "\n" +
            "public class CustomJavaJob extends JavaJob {\n" +
            "\n    " +
            " public void doJob(boolean force) throws Exception {\n";
    public final static String CODE_END = "\n}\n}";

    public ISqlTemplate getSqlTemplate() {
        return sqlTemplate;
    }

    public void setSqlTemplate(ISqlTemplate sqlTemplate) {
        this.sqlTemplate = sqlTemplate;
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
