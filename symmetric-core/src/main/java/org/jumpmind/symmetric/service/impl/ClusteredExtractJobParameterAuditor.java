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
package org.jumpmind.symmetric.service.impl;

import java.util.Optional;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;

public class ClusteredExtractJobParameterAuditor implements IParameterAuditor {
    private boolean parametersOverriden = false;

    @Override
    public void audit(TypedProperties parameters, ParameterService parameterService) {
        if (parameters.is(ParameterConstants.CLUSTER_LOCKING_ENABLED, false)
                && parameters.is(ParameterConstants.CLUSTER_STAGING_ENABLED, true)
                && parameters.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB, true)) {
            parameters.setProperty(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB, "false");
            this.parametersOverriden = true;
        }
    }

    public Optional<String> validate(TypedProperties parameters, ParameterService parameterService) {
        if (this.parametersOverriden) {
            return Optional.of(String.format("Engine %s is configured with conflicting parameters. The initial load extract job "
                    + "cannot be used when cluster locking is enabled but staging is not clustered. "
                    + "One of these parameters needs to be changed: %s=true, %s=true, %s=false",
                    parameterService.getEngineName(), ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB,
                    ParameterConstants.CLUSTER_LOCKING_ENABLED, ParameterConstants.CLUSTER_STAGING_ENABLED));
        }
        return Optional.empty();
    }
}
