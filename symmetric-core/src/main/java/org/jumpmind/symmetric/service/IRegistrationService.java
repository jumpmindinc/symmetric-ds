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
package org.jumpmind.symmetric.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.RegistrationRequest;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;

/**
 * This service provides an API that deals with {@link Node} registration
 */
public interface IRegistrationService {
    /**
     * Register a "Pull Only" node. This type of node has no Symmetric configuration and can only be used to PULL data from another node. It can never track
     * changes or push data to other nodes. When a node of this type is registered, it must complete all symmetric client functionality by itself including
     * issue the pull, acknowledging batches, etc.
     * 
     * @param externalId
     * @param nodeGroupId
     * @param databaseType
     * @param databaseVersion
     */
    public Node registerPullOnlyNode(String externalId, String nodeGroupId, String databaseType, String databaseVersion, String databseName) throws IOException;

    /**
     * Register a node for the given group name and external id if the registration is open.
     * 
     * @param isRequestedRegistration
     *            An indicator that registration has been requested by the remote client
     */
    public boolean registerNode(Node node, String remoteHost, String remoteAddress, OutputStream out,
            String userId, String password, boolean isRequestedRegistration) throws IOException;

    /**
     * Register a node for the given group name and external id if the registration is open.
     * 
     * @param isRequestedRegistration
     *            An indicator that registration has been requested by the remote client
     */
    public boolean registerNode(Node node, OutputStream out, boolean isRequestedRegistration) throws IOException;

    /**
     * Open registration for a single new node given a node group (f.e., "STORE") and external ID (f.e., "00001"). The unique node ID and password are generated
     * and stored in the node and node_security tables with the registration_enabled flag turned on. The next node to try registering for this node group and
     * external ID will be given this information.
     * 
     * @return the node id
     */
    public String openRegistration(String nodeGroupId, String externalId);

    public String openRegistration(String nodeGroup, String externalId, String syncUrl, Date notBefore, Date notAfter);

    public String openRegistration(String nodeGroup, String externalId, String remoteHost, String remoteAddress);

    public String openRegistration(Node node);

    public boolean isRegistrationOpen(String nodeGroupId, String externalId);

    public boolean isRegistrationOpen();

    /**
     * Re-open registration for a single node that already exists in the database. A new password is generated and the registration_enabled flag is turned on.
     * The next node to try registering for this node group and external ID will be given this information.
     */
    public void reOpenRegistration(String nodeId);

    public void reOpenRegistration(String nodeId, boolean forceNewPassword);

    /**
     * Mark the passed in node as registered in node_security
     * 
     * @param nodeId
     *            is the node that has just finished 'successfully' registering
     */
    public void markNodeAsRegistered(String nodeId);

    public boolean isAutoRegistration();

    /**
     * Client method which attempts to register with the registration.url to pull configuration if the node has not already been registered. If the registration
     * server cannot be reach this method will continue to try with random sleep periods up to one minute up until the registration succeeds or the maximum
     * number of attempts has been reached. Returns true if we had to register with server and was successful. Returns false if we did not have to register.
     */
    public boolean registerWithServer();

    /**
     * Server method which attempts to register using the registration URL of a client node using a push to send configuration. Returns configuration batch sent
     * with its status.
     */
    public List<OutgoingBatch> registerWithClient(Node remote, IOutgoingWithResponseTransport transport);

    /**
     * Client method which attempts to register with the registration.url to pull configuration if the node has not already been registered. Returns true if
     * registered successfully
     */
    public boolean attemptToRegisterWithServer(int maxNumberOfAttempts);

    public List<RegistrationRequest> getRegistrationRequests(boolean includeNodesWithOpenRegistrations, boolean includeRejects);

    public RegistrationRequest getLatestRegistrationRequest(String nodeGroupId, String externalId);

    public boolean deleteRegistrationRequest(RegistrationRequest request);

    public void saveRegistrationRequest(RegistrationRequest request);

    public void updateRegistrationRequest(RegistrationRequest request);

    public boolean isRegisteredWithServer();

    /**
     * Add an entry to the registation_redirect table so that if a node tries to register here. It will be redirected to the correct node.
     */
    public void saveRegistrationRedirect(String externalIdToRedirect, String nodeIdToRedirectTo);

    public void deleteRegistrationRedirectsByNodeId(String nodeId);

    public String getRedirectionUrlFor(String externalId);

    public void requestNodeCopy();

    public void setAllowClientRegistration(boolean enabled);

    /**
     * When server pushes to client asking to register it, the client responds with its registration request properties
     */
    public boolean writeRegistrationProperties(OutputStream os);

    /**
     * When server pushes to client asking to register it, the client loads the configuration batch and returns an acknowledgement
     */
    public boolean loadRegistrationBatch(Node node, InputStream is, OutputStream os);
}