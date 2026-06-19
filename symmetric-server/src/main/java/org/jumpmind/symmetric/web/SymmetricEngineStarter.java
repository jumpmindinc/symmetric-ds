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
package org.jumpmind.symmetric.web;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.common.ParameterConstants;

public class SymmetricEngineStarter implements Runnable {
    private SymmetricEngineHolder holder;
    private String propertiesFile;
    private ISymmetricEngine engine;

    public SymmetricEngineStarter(String propertiesFile, SymmetricEngineHolder holder) {
        this.propertiesFile = propertiesFile;
        this.holder = holder;
    }

    @Override
    public void run() {
        engine = holder.create(propertiesFile);
        if (engine != null) {
            String name = engine.getEngineName();
            if (holder.isAutoStart() && engine.getParameterService().is(ParameterConstants.AUTO_START_ENGINE)) {
                if (!engine.start()) {
                    holder.getEnginesFailed().put(name, new FailedEngineInfo(name, propertiesFile, engine.getLastException()));
                }
            }
            holder.getEnginesStartingNames().remove(name);
        }
        holder.getEnginesStarting().remove(this);
    }

    public SymmetricEngineHolder getHolder() {
        return holder;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    public ISymmetricEngine getEngine() {
        return engine;
    }

    public boolean isRegistrationNode() {
        if (engine == null) {
            return false;
        }
        IParameterService parameterService = engine.getParameterService();
        String registrationUrl = parameterService.getString(ParameterConstants.REGISTRATION_URL, "");
        String syncUrl = parameterService.getString(ParameterConstants.SYNC_URL, "");
        return (StringUtils.isBlank(registrationUrl) || registrationUrl.equals(syncUrl));
    }
}
