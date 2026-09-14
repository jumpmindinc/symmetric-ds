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
package org.jumpmind.symmetric.web;

import java.io.IOException;
import java.io.OutputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.TransportUtils;

/**
 * Allow a node to pull the configuration. The symmetricVersion parameter is the version number of the software, which is used to filter configuration that is
 * sent. The configVersion parameter is the current version number of the configuration that will be upgraded.
 */
public class ConfigurationUriHandler extends AbstractUriHandler {
    private IDataExtractorService dataExtractorService;

    public ConfigurationUriHandler(IParameterService parameterService,
            IDataExtractorService dataExtractorService, IInterceptor... interceptors) {
        super("/config/*", parameterService, interceptors);
        this.dataExtractorService = dataExtractorService;
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        Node remoteNode = new Node();
        String symVersion = ServletUtils.getParameter(req, WebConstants.SYMMETRIC_VERSION);
        String configVersion = ServletUtils.getParameter(req, WebConstants.CONFIG_VERSION);
        remoteNode.setNodeId(ServletUtils.getParameter(req, WebConstants.NODE_ID));
        remoteNode.setSymmetricVersion(symVersion);
        remoteNode.setConfigVersion(configVersion);
        log.info("Configuration request from node ID " + remoteNode.getNodeId() + " {symmetricVersion={}, configVersion={}}",
                symVersion, configVersion);
        if (StringUtils.isBlank(configVersion) || Version.isOlderMinorVersion(configVersion, Version.version())) {
            log.info("Sending configuration to node ID " + remoteNode.getNodeId());
            OutputStream outputStream = res.getOutputStream();
            dataExtractorService.extractConfigurationStandalone(remoteNode, TransportUtils.toWriter(outputStream), TableConstants
                    .getConfigTablesExcludedFromExport());
        }
    }
}
