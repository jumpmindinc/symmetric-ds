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
package org.jumpmind.symmetric.transport.file;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.IIncomingTransport;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.transport.http.HttpTransportManager;
import org.jumpmind.symmetric.web.WebConstants;
import org.jumpmind.util.FormatUtils;

public class FileTransportManager extends HttpTransportManager implements ITransportManager {
    protected IParameterService parameterService;

    public FileTransportManager(ISymmetricEngine engine) {
        super(engine);
        this.parameterService = engine.getParameterService();
    }

    @Override
    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local, String securityToken, String registrationUrl)
            throws IOException {
        return WebConstants.SC_OK;
    }

    @Override
    public void writeAcknowledgement(OutputStream out, Node remote, List<IncomingBatch> list, Node local, String securityToken)
            throws IOException {
    }

    @Override
    public IIncomingTransport getFilePullTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl) throws IOException {
        return getPullTransport(remote, local, securityToken, requestProperties, registrationUrl);
    }

    /**
     * File-based transport has no concept of a resumed pull; ignore {@code resumeBatchId} rather than inheriting {@link HttpTransportManager}'s HTTP-specific
     * 6-arg override.
     */
    @Override
    public IIncomingTransport getFilePullTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl, Long resumeBatchId) throws IOException {
        return getPullTransport(remote, local, securityToken, requestProperties, registrationUrl);
    }

    @Override
    public IOutgoingWithResponseTransport getFilePushTransport(Node remote, Node local, String securityToken, String registrationUrl)
            throws IOException {
        return getPushTransport(remote, local, securityToken, registrationUrl);
    }

    @Override
    public IIncomingTransport getPullTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl) throws IOException {
        return new FileIncomingTransport(remote, local,
                getDirName(ParameterConstants.NODE_OFFLINE_INCOMING_DIR, local),
                getDirName(ParameterConstants.NODE_OFFLINE_ARCHIVE_DIR, local),
                getDirName(ParameterConstants.NODE_OFFLINE_ERROR_DIR, local));
    }

    /**
     * File-based transport has no concept of a resumed pull; ignore {@code resumeBatchId} rather than inheriting {@link HttpTransportManager}'s HTTP-specific
     * 6-arg override.
     */
    @Override
    public IIncomingTransport getPullTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl, Long resumeBatchId) throws IOException {
        return getPullTransport(remote, local, securityToken, requestProperties, registrationUrl);
    }

    @Override
    public IOutgoingWithResponseTransport getPushTransport(Node remote, Node local, String securityToken, String registrationUrl)
            throws IOException {
        return new FileOutgoingTransport(remote, local, getDirName(ParameterConstants.NODE_OFFLINE_OUTGOING_DIR, local));
    }

    protected String getDirName(String paramName, Node localNode) {
        String dirName = parameterService.getString(paramName);
        dirName = FormatUtils.replace("nodeGroupId", localNode.getNodeGroupId(), dirName);
        dirName = FormatUtils.replace("nodeId", localNode.getNodeId(), dirName);
        if (StringUtils.isNotBlank(dirName)) {
            File dir = new File(dirName);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
        return dirName;
    }
}
