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
package org.jumpmind.symmetric.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.web.WebConstants;

public class MockTransportManager extends AbstractTransportManager implements ITransportManager {
    protected IIncomingTransport incomingTransport;
    protected IOutgoingWithResponseTransport outgoingTransport;

    public String resolveURL(String url, String registrationUrl) {
        return null;
    }

    public void addExtensionSyncUrlHandler(String name, ISyncUrlExtension handler) {
    }

    public IIncomingTransport getPullTransport(Node remote, Node local,
            String securityToken, Map<String, String> requestProperties, String registrationUrl)
            throws IOException {
        return incomingTransport;
    }

    public IIncomingTransport getPingTransport(Node remote, Node local, String registrationUrl) throws IOException {
        return incomingTransport;
    }

    public IIncomingTransport getFilePullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return incomingTransport;
    }

    public IOutgoingWithResponseTransport getPushTransport(Node remote,
            Node local, String securityToken, String registrationUrl) throws IOException {
        return outgoingTransport;
    }

    public int sendAcknowledgement(Node remote, List<IncomingBatch> list,
            Node local, String securityToken, Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return WebConstants.SC_OK;
    }

    public int sendAcknowledgement(Node remote, List<IncomingBatch> list,
            Node local, String securityToken, String registrationUrl) throws IOException {
        return WebConstants.SC_OK;
    }

    @Override
    public int sendCopyRequest(Node local) throws IOException {
        return -1;
    }

    @Override
    public int sendStatusRequest(Node local, Map<String, String> statuses) throws IOException {
        return -1;
    }

    public void writeAcknowledgement(OutputStream out, Node remote,
            List<IncomingBatch> list, Node local, String securityToken)
            throws IOException {
    }

    public IIncomingTransport getIncomingTransport() {
        return incomingTransport;
    }

    public void setIncomingTransport(IIncomingTransport is) {
        this.incomingTransport = is;
    }

    public IOutgoingWithResponseTransport getOutgoingTransport() {
        return outgoingTransport;
    }

    public void setOutgoingTransport(IOutgoingWithResponseTransport outgoingTransport) {
        this.outgoingTransport = outgoingTransport;
    }

    public IIncomingTransport getRegisterTransport(Node node, String registrationUrl) throws IOException {
        return incomingTransport;
    }

    public IIncomingTransport getRegisterTransport(Node node, String registrationUrl, Map<String, String> requestProperties) throws IOException {
        return incomingTransport;
    }

    public IOutgoingWithResponseTransport getRegisterPushTransport(Node remote, Node local) throws IOException {
        return null;
    }

    public List<BatchAck> readAcknowledgement(String parameterString) throws IOException {
        return null;
    }

    public static List<BatchAck> readAcknowledgement(Map<String, ? extends Object> parameters) {
        return null;
    }

    public List<BatchAck> readAcknowledgement(String parameterString1, String parameterString2) throws IOException {
        return null;
    }

    public IOutgoingWithResponseTransport getFilePushTransport(Node remote, Node local,
            String securityToken, String registrationUrl) throws IOException {
        return outgoingTransport;
    }

    @Override
    public IOutgoingWithResponseTransport getPushTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return outgoingTransport;
    }

    @Override
    public IIncomingTransport getConfigTransport(Node remote, Node local, String securityToken,
            String symmetricVersion, String configVersion, String registrationUrl) throws IOException {
        return null;
    }

    @Override
    public IOutgoingWithResponseTransport getBandwidthPushTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl)
            throws IOException {
        return null;
    }

    @Override
    public void writeRequestProperties(Map<String, String> requestProperties, OutputStream os) throws IOException {
    }

    @Override
    public Map<String, String> readRequestProperties(InputStream is) throws IOException {
        return null;
    }

    @Override
    public IIncomingTransport getComparePullTransport(Node remote, Node local, String securityToken, String registrationUrl,
            Map<String, String> requestParameters) throws IOException {
        return null;
    }

    @Override
    public IOutgoingWithResponseTransport getComparePushTransport(Node remote, Node local, String securityToken, String registrationUrl,
            Map<String, String> requestParameters) throws IOException {
        return null;
    }
}