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
package org.jumpmind.symmetric.common;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Strings;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.model.Node;

public class ConfigurationVersionHelper {
    protected String tablePrefix;
    protected Set<String> proTables;
    protected Map<String, String> tablesByVersion;
    protected boolean isTargetNodePro;
    protected String targetNodeVersion;
    protected Map<String, String> monitorTypesByVersion;

    public ConfigurationVersionHelper(String tablePrefix) {
        this.tablePrefix = tablePrefix;
        proTables = TableConstants.getTablesForConsole(tablePrefix);
        tablesByVersion = TableConstants.getConfigTablesByVersion(tablePrefix);
        monitorTypesByVersion = MonitorConstants.getMonitorTypesByVersion();
    }

    public ConfigurationVersionHelper(String tablePrefix, Node targetNode) {
        this(tablePrefix);
        setTargetNode(targetNode);
    }

    public boolean shouldSendTable(String tableName) {
        if (!isTargetNodePro && proTables.contains(tableName)) {
            return false;
        }
        String tableVersion = tablesByVersion.get(tableName);
        if (tableVersion != null && Version.isOlderThanVersion(targetNodeVersion, tableVersion)) {
            return false;
        }
        return true;
    }

    public Set<Node> filterNodes(Set<Node> nodes, String tableName, Map<String, String> columnValues) {
        boolean isProTable = proTables.contains(tableName);
        String tableVersion = tablesByVersion.get(tableName);
        boolean isMonitor = tableName.equalsIgnoreCase(TableConstants.getTableName(tablePrefix, TableConstants.SYM_MONITOR));
        if (isProTable || tableVersion != null || isMonitor) {
            Set<Node> targetNodes = new HashSet<Node>(nodes.size());
            for (Node node : nodes) {
                setTargetNode(node);
                if ((!isProTable || isTargetNodePro) && (tableVersion == null || !Version.isOlderThanVersion(targetNodeVersion, tableVersion)) &&
                        (!isMonitor || !isNodeOlderThanMonitor(columnValues))) {
                    targetNodes.add(node);
                }
            }
            return targetNodes;
        } else {
            return nodes;
        }
    }

    protected boolean isNodeOlderThanMonitor(Map<String, String> columnValues) {
        String monitorType = columnValues == null ? null : columnValues.get("TYPE");
        String monitorVersion = monitorTypesByVersion.get(monitorType);
        if (monitorVersion == null) {
            return false;
        }
        return Version.isOlderThanVersion(targetNodeVersion, monitorVersion);
    }

    public void setTargetNode(Node targetNode) {
        targetNodeVersion = targetNode.getSymmetricVersion();
        isTargetNodePro = Strings.CS.equals(targetNode.getDeploymentType(), Constants.DEPLOYMENT_TYPE_PROFESSIONAL);
    }
}
