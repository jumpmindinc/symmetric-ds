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
package org.jumpmind.symmetric.route;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.util.DataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A common superclass for data routers
 */
public abstract class AbstractDataRouter implements IDataRouter {
    protected Logger log = LoggerFactory.getLogger(getClass());

    public void contextCommitted(SimpleRouterContext context) {
    }

    protected Map<String, String> getDataMap(DataMetaData dataMetaData, ISymmetricDialect symmetricDialect) {
        return DataUtils.getDataMap(dataMetaData);
    }

    protected Map<String, String> getNewDataAsString(String prefix, DataMetaData dataMetaData, ISymmetricDialect symmetricDialect) {
        return DataUtils.getNewDataAsString(prefix, dataMetaData);
    }

    protected Map<String, String> getOldDataAsString(String prefix, DataMetaData dataMetaData, ISymmetricDialect symmetricDialect) {
        return DataUtils.getOldDataAsString(prefix, dataMetaData);
    }

    protected Map<String, String> getDataAsString(String prefix, DataMetaData dataMetaData, ISymmetricDialect symmetricDialect,
            String[] rowData) {
        return DataUtils.getDataAsString(prefix, dataMetaData, rowData);
    }

    protected Map<String, String> getPkDataAsString(DataMetaData dataMetaData, ISymmetricDialect symmetricDialect) {
        return DataUtils.getPkDataAsString(dataMetaData);
    }

    protected Map<String, Object> getDataObjectMap(DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, boolean upperCase) {
        return DataUtils.getDataObjectMap(dataMetaData, symmetricDialect, upperCase);
    }

    protected Map<String, Object> getNewDataAsObject(String prefix, DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, boolean upperCase) {
        return DataUtils.getNewDataAsObject(prefix, dataMetaData, symmetricDialect, upperCase);
    }

    protected Map<String, Object> getOldDataAsObject(String prefix, DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, boolean upperCase) {
        return DataUtils.getOldDataAsObject(prefix, dataMetaData, symmetricDialect, upperCase);
    }

    protected <T> Map<String, T> getNullData(String prefix, DataMetaData dataMetaData) {
        return DataUtils.getNullData(prefix, dataMetaData);
    }

    protected Map<String, Object> getDataAsObject(String prefix, DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect, String[] rowData, boolean upperCase) {
        return DataUtils.getDataAsObject(prefix, dataMetaData, symmetricDialect, rowData, upperCase);
    }

    protected void testColumnNamesMatchValues(DataMetaData dataMetaData, String[] columnNames, Object[] values) {
        DataUtils.testColumnNamesMatchValues(dataMetaData, columnNames, values);
    }

    protected Map<String, Object> getPkDataAsObject(DataMetaData dataMetaData,
            ISymmetricDialect symmetricDialect) {
        return DataUtils.getPkDataAsObject(dataMetaData, symmetricDialect);
    }

    protected Set<String> addNodeId(String nodeId, Set<String> nodeIds, Set<Node> nodes) {
        nodeIds = nodeIds == null ? new HashSet<String>(1) : nodeIds;
        for (Node node : nodes) {
            if (node.getNodeId().equals(nodeId)) {
                nodeIds.add(nodeId);
                break;
            }
        }
        return nodeIds;
    }

    protected Set<String> toNodeIds(Set<Node> nodes, Set<String> nodeIds) {
        nodeIds = nodeIds == null ? new HashSet<String>(nodes.size()) : nodeIds;
        for (Node node : nodes) {
            nodeIds.add(node.getNodeId());
        }
        return nodeIds;
    }

    protected Set<String> toExternalIds(Set<Node> nodes) {
        Set<String> externalIds = new HashSet<String>();
        for (Node node : nodes) {
            externalIds.add(node.getExternalId());
        }
        return externalIds;
    }

    /**
     * Override if needed.
     */
    public void completeBatch(SimpleRouterContext context, OutgoingBatch batch) {
        log.debug("Completing batch {}", batch.getBatchId());
    }

    /**
     * Override if a router is not configurable.
     */
    public boolean isConfigurable() {
        return true;
    }

    /**
     * Override if a router is able to route non-DML DataEventTypes.
     */
    public boolean isDmlOnly() {
        return true;
    }
}
