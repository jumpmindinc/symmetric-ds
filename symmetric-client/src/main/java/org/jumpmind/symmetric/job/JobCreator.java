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

import java.lang.reflect.Constructor;

import org.apache.commons.lang3.ClassUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.model.JobDefinition;
import org.jumpmind.symmetric.model.JobDefinition.JobType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class JobCreator {
    public IJob createJob(JobDefinition jobDefinition, ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        AbstractJob job = null;
        if (jobDefinition.getJobType() == JobType.BSH) {
            job = new BshJob(jobDefinition.getJobName(), engine, taskScheduler);
        } else if (jobDefinition.getJobType() == JobType.SQL) {
            job = new SqlJob(jobDefinition.getJobName(), engine, taskScheduler);
        } else if (jobDefinition.getJobType() == JobType.BUILT_IN) {
            job = instantiateJavaJob(jobDefinition.getJobExpression(), jobDefinition, engine, taskScheduler);
        } else if (jobDefinition.getJobType() == JobType.JAVA) {
            job = new JavaJob(jobDefinition.getJobName(), engine, taskScheduler);
        } else {
            throw new SymmetricException("Unknown job type " + jobDefinition.getJobType());
        }
        job.setJobDefinition(jobDefinition);
        return job;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected AbstractJob instantiateJavaJob(String className, JobDefinition jobDefinition, ISymmetricEngine engine, ThreadPoolTaskScheduler taskScheduler) {
        try {
            Class jobClass = ClassUtils.getClass(className);
            Constructor[] constructors = jobClass.getConstructors();
            // look for 3 arg constructor of name, engine, taskScheduler.
            for (Constructor constructor : constructors) {
                if (constructor.getParameterTypes().length == 3
                        && constructor.getParameterTypes()[0].isAssignableFrom(String.class)
                        && constructor.getParameterTypes()[1].isAssignableFrom(ISymmetricEngine.class)
                        && constructor.getParameterTypes()[2].isAssignableFrom(ThreadPoolTaskScheduler.class)) {
                    return (AbstractJob) constructor.newInstance(jobDefinition.getJobName(), engine, taskScheduler);
                }
            }
            // look for 2 arg constructor of engine, taskScheduler.
            for (Constructor constructor : constructors) {
                if (constructor.getParameterTypes().length == 2
                        && constructor.getParameterTypes()[0].isAssignableFrom(ISymmetricEngine.class)
                        && constructor.getParameterTypes()[1].isAssignableFrom(ThreadPoolTaskScheduler.class)) {
                    return (AbstractJob) constructor.newInstance(engine, taskScheduler);
                }
            }
            return (AbstractJob) jobClass.getDeclaredConstructor().newInstance(); // try default constructor.
        } catch (Exception ex) {
            throw new SymmetricException("Failed to load and instantiate job class '" + className + "'", ex);
        }
    }
}
