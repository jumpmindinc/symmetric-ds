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
import java.util.List;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.extension.IProcessInfoListener;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.IncomingError;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.RemoteNodeStatus;
import org.jumpmind.symmetric.service.impl.DataLoaderService.ConflictNodeGroupLink;
import org.jumpmind.symmetric.transport.IIncomingTransport;

/**
 * This service provides an API to load data into a SymmetricDS node's database from a transport
 */
public interface IDataLoaderService {
    public boolean refreshFromDatabase();

    public RemoteNodeStatus loadDataFromPull(Node remote, String channelId) throws IOException;

    public void loadDataFromPull(Node sourceNode, RemoteNodeStatus status) throws IOException;

    public void loadDataFromPush(Node sourceNode, InputStream in, OutputStream out) throws IOException;

    public void loadDataFromPush(Node sourceNode, String channelId, InputStream in, OutputStream out) throws IOException;

    public List<IncomingBatch> loadDataFromTransport(ProcessInfo processInfo, Node sourceNode, IIncomingTransport transport) throws IOException;

    public List<IncomingBatch> loadDataFromOfflineTransport(Node remote, RemoteNodeStatus status, IIncomingTransport transport) throws IOException;

    public void loadDataFromConfig(Node remote, RemoteNodeStatus status, boolean force) throws IOException;

    public List<String> getAvailableDataLoaderFactories();

    public List<IncomingBatch> loadDataBatch(String batchData);

    public List<IncomingBatch> loadDataBatch(String batchData, IProcessInfoListener listener);

    public List<ConflictNodeGroupLink> getConflictSettingsNodeGroupLinks(NodeGroupLink link, boolean refreshCache);

    public List<ConflictNodeGroupLink> getConflictSettinsNodeGroupLinksFromDb(NodeGroupLink link);

    public List<ConflictNodeGroupLink> getConflictSettingsNodeGroupLinks();

    public void delete(ConflictNodeGroupLink settings);

    public void delete(ISqlTransaction transaction, ConflictNodeGroupLink settings);

    public void deleteAllConflicts();

    public void save(ConflictNodeGroupLink settings);

    public void saveAsCopy(ConflictNodeGroupLink settings);

    public void rename(String oldId, ConflictNodeGroupLink settings);

    public void clearCache();

    public List<IncomingError> getIncomingErrors(long batchId, String nodeId);

    public IncomingError getIncomingError(long batchId, String nodeId, long rowNumber);

    public IncomingError getCurrentIncomingError(long batchId, String nodeId);

    public void insertIncomingError(ISqlTransaction transaction, IncomingError incomingError);

    public void insertIncomingError(IncomingError incomingError);

    public void updateIncomingError(IncomingError incomingError);
}