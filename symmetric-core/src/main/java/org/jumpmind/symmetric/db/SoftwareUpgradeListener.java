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
package org.jumpmind.symmetric.db;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.cache.ClusteredCacheManager;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.ext.ISymmetricEngineAware;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.util.ModuleException;
import org.jumpmind.symmetric.util.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoftwareUpgradeListener implements ISoftwareUpgradeListener, ISymmetricEngineAware, IBuiltInExtensionPoint {
    private static final Logger log = LoggerFactory.getLogger(SoftwareUpgradeListener.class);
    ISymmetricEngine engine;

    @Override
    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
    }

    @Override
    public void upgrade(String databaseVersion, String softwareVersion) {
        IParameterService parameterService = engine.getParameterService();
        if (databaseVersion.equals("3.8.0")) {
            log.info("Detected an original value of 3.8.0 performing necessary upgrades.");
            String sql = "update  " + parameterService.getTablePrefix()
                    + "_" + TableConstants.SYM_CHANNEL +
                    " set max_batch_size = 10000 where reload_flag = 1 and max_batch_size = 1";
            engine.getSqlTemplate().update(sql);
        }
        if (Version.isOlderThanVersion(databaseVersion, "3.13.0") &&
                parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)) {
            engine.getNodeService().deleteNodeHost(engine.getNodeService().findIdentityNodeId());
        }
        if (Version.isOlderThanVersion(databaseVersion, "3.16.8")) {
            boolean oldDbValue = parameterService.is(ParameterConstants.PURGE_STRANDED_DATA_RECAPTURE_ENABLED);
            parameterService.saveParameter(ParameterConstants.PURGE_STRANDED_DATA_RECAPTURE_ENABLED, false, "upgrade");
            if (oldDbValue) {
                log.warn("Upgrading from database version {}: switching parameter {} " +
                        "from {} to {} in order to prevent recapture of old data events during purge.",
                        databaseVersion, ParameterConstants.CLUSTER_LOCKING_ENABLED, oldDbValue, false);
            }
        }
        if (Version.isOlderThanVersion(databaseVersion, "3.18.0")) {
            boolean startupValue = ClusteredCacheManager.getInstance().isClusterLockingEnabled();
            boolean oldDbValue = parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED);
            parameterService.saveParameter(ParameterConstants.CLUSTER_LOCKING_ENABLED, startupValue, "upgrade");
            if (oldDbValue != startupValue) {
                log.warn("Upgrading from database version {}: You must manually populate now-startup parameter {} " +
                        "in either conf/symmetric-server.properties file or environment variable to match " +
                        "pre-upgrade value of {}. It is no longer database-overrideable.",
                        databaseVersion, ParameterConstants.CLUSTER_LOCKING_ENABLED, oldDbValue);
            }
        }
        try {
            ModuleManager.getInstance().upgradeAll();
        } catch (ModuleException e) {
            throw new RuntimeException(e);
        }
    }
}
