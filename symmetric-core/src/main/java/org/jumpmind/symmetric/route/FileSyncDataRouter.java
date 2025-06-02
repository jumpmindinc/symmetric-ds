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
package org.jumpmind.symmetric.route;

import java.sql.Types;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.IRouterService;

public class FileSyncDataRouter extends AbstractDataRouter implements IBuiltInExtensionPoint {
    public static final String ROUTER_TYPE = "filesync";
    private ISymmetricEngine engine;

    public FileSyncDataRouter(ISymmetricEngine engine) {
        this.engine = engine;
    }

    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData,
            Set<Node> nodes, boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        Set<String> nodeIds = new HashSet<String>();
        if (initialLoad && initialLoadSelectUsed) {
            nodeIds = toNodeIds(nodes, null);
        } else {
            IFileSyncService fileSyncService = engine.getFileSyncService();
            IRouterService routerService = engine.getRouterService();
            Map<String, String> newData = getNewDataAsString(null, dataMetaData,
                    engine.getSymmetricDialect());
            String triggerId = newData.get("TRIGGER_ID");
            String routerId = newData.get("ROUTER_ID");
            String sourceNodeId = newData.get("LAST_UPDATE_BY");
            String lastEventType = newData.get("LAST_EVENT_TYPE");
            String relativeDir = newData.get("RELATIVE_DIR");
            // Append calculated top relative dir to old data and new data
            // Append top relative dir column name to list of columns in sym_file_snapshot trigger history
            if (triggerId == null) {
                Map<String, String> oldData = getOldDataAsString(null, dataMetaData,
                        engine.getSymmetricDialect());
                triggerId = oldData.get("TRIGGER_ID");
                routerId = oldData.get("ROUTER_ID");
                sourceNodeId = oldData.get("LAST_UPDATE_BY");
                lastEventType = oldData.get("LAST_EVENT_TYPE");
                relativeDir = oldData.get("RELATIVE_DIR");
            }
            String topRelativeDir = getTopRelativeDir(relativeDir);
            addTopRelativeDirToData(topRelativeDir, dataMetaData);
            LastEventType eventType = LastEventType.fromCode(lastEventType);
            FileTriggerRouter fileTriggerRouter = fileSyncService.getFileTriggerRouter(
                    triggerId, routerId, false);
            if (fileTriggerRouter != null && fileTriggerRouter.isEnabled()) {
                if (fileTriggerRouter.getRouter().getNodeGroupLink()
                        .equals(triggerRouter.getRouter().getNodeGroupLink())) {
                    if (eventType == null || eventType == LastEventType.DELETE
                            && fileTriggerRouter.getFileTrigger().isSyncOnDelete()
                            || eventType == LastEventType.MODIFY
                                    && fileTriggerRouter.getFileTrigger().isSyncOnModified()
                            || eventType == LastEventType.CREATE
                                    && fileTriggerRouter.getFileTrigger().isSyncOnCreate()) {
                        Router router = fileTriggerRouter.getRouter();
                        Map<String, IDataRouter> routers = routerService.getRouters();
                        IDataRouter dataRouter = null;
                        if (StringUtils.isNotBlank(router.getRouterType())) {
                            dataRouter = routers.get(router.getRouterType());
                        }
                        if (dataRouter == null) {
                            dataRouter = routers.get("default");
                        }
                        if (context instanceof ChannelRouterContext) {
                            ((ChannelRouterContext) context).addUsedDataRouter(dataRouter);
                        }
                        dataMetaData.setRouter(router);
                        Set<String> dataRouterNodeIds = dataRouter.routeToNodes(context, dataMetaData, nodes, false,
                                false, triggerRouter);
                        if (dataRouterNodeIds != null) {
                            nodeIds.addAll(dataRouterNodeIds);
                        }
                        nodeIds.remove(sourceNodeId);
                    }
                }
            } else {
                if (context != null && context.getChannel() != null && !context.getChannel().isFileSyncFlag()) {
                    log.error("One or more file triggers use the '{}' channel, which is not configured for file sync.",
                            context.getChannel().getChannelId());
                } else {
                    log.error(
                            "Could not find a trigger router with a trigger_id of {} and a router_id of {}.  The file snapshot will not be routed",
                            triggerId, routerId);
                }
            }
        }
        return nodeIds;
    }

    private String getTopRelativeDir(String relativeDir) {
        String topRelativeDir = null;
        if (relativeDir != null) {
            relativeDir = relativeDir.replace('\\', '/');
            topRelativeDir = (relativeDir.contains("/") ? relativeDir.substring(0, relativeDir.indexOf('/')) : relativeDir);
        }
        return topRelativeDir;
    }

    private void addTopRelativeDirToData(String topRelativeDir, DataMetaData dataMetaData) {
        Table copy;
        try {
            copy = (Table) dataMetaData.getTable().clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        copy.addColumn(new Column("top_relative_dir", false, Types.VARCHAR, topRelativeDir.length(), 0));
        dataMetaData.setTable(copy);
        Data data = dataMetaData.getData();
        String oldData = data.getCsvData(Data.OLD_DATA);
        String newData = data.getCsvData(Data.ROW_DATA);
        if (oldData != null) {
            oldData = oldData.concat(",");
            if (!StringUtils.isBlank(oldData)) {
                oldData = oldData.concat("\"").concat(topRelativeDir).concat("\"");
            }
            data.putCsvData(Data.OLD_DATA, oldData);
        }
        if (newData != null) {
            newData = newData.concat(",");
            if (!StringUtils.isBlank(newData)) {
                newData = newData.concat("\"").concat(topRelativeDir).concat("\"");
            }
            data.putCsvData(Data.ROW_DATA, newData);
        }
        TriggerHistory triggerHistory = data.getTriggerHistory();
        TriggerHistory newTriggerHistory = new TriggerHistory(
                triggerHistory.getSourceTableName(),
                triggerHistory.getPkColumnNames(),
                triggerHistory.getColumnNames().concat(",").concat("TOP_RELATIVE_DIR"));
        data.setTriggerHistory(newTriggerHistory);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }
}
