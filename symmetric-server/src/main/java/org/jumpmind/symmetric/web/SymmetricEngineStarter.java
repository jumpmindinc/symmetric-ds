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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SymmetricEngineStarter implements Runnable {
    private SymmetricEngineHolder holder;
    private String propertiesFile;
    private ISymmetricEngine engine;
    private final Logger log = LoggerFactory.getLogger(getClass());

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

    public boolean isRegistrationEngineStarter() {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(getPropertiesFile())) {
            props.load(is);
        } catch (IOException e) {
            log.warn("Unable to read properties file to determine if registration engine starter: {}", getPropertiesFile());
            return false;
        }
        String registrationUrl = props.getProperty(ParameterConstants.REGISTRATION_URL, "");
        String syncUrl = props.getProperty(ParameterConstants.SYNC_URL, "");
        return (StringUtils.isBlank(registrationUrl) || registrationUrl.equals(syncUrl));
    }
}
