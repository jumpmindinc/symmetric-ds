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
import org.jumpmind.symmetric.transport.http.IHttpResumeCache;

public interface ITransportManager {
    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local, String securityToken, String registrationUrl) throws IOException;

    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl) throws IOException;

    public void writeAcknowledgement(OutputStream out, Node remote, List<IncomingBatch> list, Node local, String securityToken) throws IOException;

    public List<BatchAck> readAcknowledgement(String parameterString1, String parameterString2) throws IOException;

    public IIncomingTransport getFilePullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException;

    /**
     * Same as the 5-arg {@code getFilePullTransport}, but additionally requests a resumed pull of one specific previously-interrupted file sync batch when
     * {@code resumeBatchId} is non-null. Implementations that don't support resume may ignore {@code resumeBatchId} and delegate to the 5-arg overload.
     */
    default IIncomingTransport getFilePullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl, Long resumeBatchId) throws IOException {
        return getFilePullTransport(remote, local, securityToken, requestProperties, registrationUrl);
    }

    public IOutgoingWithResponseTransport getFilePushTransport(Node remote, Node local,
            String securityToken, String registrationUrl) throws IOException;

    public IIncomingTransport getPullTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties, String registrationUrl)
            throws IOException;

    /**
     * Same as the 5-arg {@code getPullTransport}, but additionally requests a resumed pull of one specific previously-interrupted batch when
     * {@code resumeBatchId} is non-null. Resume is an HTTP-specific mechanism; implementations that don't support it may ignore {@code resumeBatchId} and
     * delegate to the 5-arg overload.
     */
    default IIncomingTransport getPullTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl, Long resumeBatchId) throws IOException {
        return getPullTransport(remote, local, securityToken, requestProperties, registrationUrl);
    }

    /**
     * @return the resume cache backing this transport manager's pull requests, or {@code null} if this transport doesn't support resume (resume is
     *         HTTP-specific)
     */
    default IHttpResumeCache getResumeCache() {
        return null;
    }

    public IIncomingTransport getPingTransport(Node remote, Node local, String registrationUrl) throws IOException;

    public IOutgoingWithResponseTransport getPushTransport(Node remote, Node local, String securityToken, String registrationUrl) throws IOException;

    public IOutgoingWithResponseTransport getPushTransport(Node remote, Node local, String securityToken, Map<String, String> requestProperties,
            String registrationUrl) throws IOException;

    public IIncomingTransport getRegisterTransport(Node node, String registrationUrl) throws IOException;

    public IIncomingTransport getRegisterTransport(Node node, String registrationUrl, Map<String, String> requestProperties) throws IOException;

    public IOutgoingWithResponseTransport getRegisterPushTransport(Node remote, Node local) throws IOException;

    public IIncomingTransport getConfigTransport(Node remote, Node local, String securityToken,
            String symmetricVersion, String configVersion, String registrationUrl) throws IOException;

    public IIncomingTransport getBandwidthPullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl, long sampleSize) throws IOException;

    public IOutgoingWithResponseTransport getBandwidthPushTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException;

    public IIncomingTransport getComparePullTransport(Node remote, Node local, String securityToken, String registrationUrl,
            Map<String, String> requestParameters) throws IOException;

    public IOutgoingWithResponseTransport getComparePushTransport(Node remote, Node local, String securityToken, String registrationUrl,
            Map<String, String> requestParameters) throws IOException;

    /**
     * This is the proper way to determine the URL for a node. It delegates to configured extension points when necessary to take in to account custom load
     * balancing and url selection schemes.
     * 
     * @param url
     *            This is the url configured in sync_url of the node table
     */
    public String resolveURL(String url, String registrationUrl);

    public int sendCopyRequest(Node local) throws IOException;

    public int sendStatusRequest(Node local, Map<String, String> statuses) throws IOException;

    public void writeRequestProperties(Map<String, String> requestProperties, OutputStream os) throws IOException;

    public Map<String, String> readRequestProperties(InputStream is) throws IOException;
}