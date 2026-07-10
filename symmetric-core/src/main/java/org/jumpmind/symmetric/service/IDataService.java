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
package org.jumpmind.symmetric.service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ext.IHeartbeatListener;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.load.IReloadGenerator;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataEvent;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadRequestKey;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;

/**
 * This service provides an API to access and update {@link Data}.
 */
public interface IDataService {
    public void insertTableReloadRequest(ISqlTransaction transaction, TableReloadRequest request);

    public void insertTableReloadRequest(TableReloadRequest request);

    public TableReloadRequest getTableReloadRequest(TableReloadRequestKey key);

    public TableReloadRequest getTableReloadRequest(long loadId);

    public List<TableReloadRequest> getTableReloadRequests(long loadId);

    public TableReloadRequest getTableReloadRequest(long loadId, String triggerId, String routerId);

    public List<TableReloadRequest> getTableReloadRequestToProcess(final String sourceNodeId);

    public List<TableReloadRequest> getTableReloadRequestToProcessByTarget(final String targetNodeId);

    public Map<Long, List<TableReloadRequest>> getTableReloadRequestByLoadIdMap();

    public List<TableReloadStatus> getTableReloadStatus();

    public List<TableReloadStatus> getOutgoingTableReloadStatus();

    public List<TableReloadStatus> getIncomingTableReloadStatus();

    public List<TableReloadStatus> getActiveTableReloadStatus();

    public List<TableReloadStatus> getActiveOutgoingTableReloadStatusByTargetNodeId(String targetNodeId);

    public List<TableReloadStatus> getActiveOutgoingTableReloadStatus();

    public List<TableReloadStatus> getActiveIncomingTableReloadStatus();

    public TableReloadStatus getTableReloadStatusByLoadIdAndSourceNodeId(long loadId, String sourceNodeId);

    public List<TableReloadStatus> getTableReloadStatusByTarget(String targetNodeId);

    public TableReloadStatus updateTableReloadStatusDataLoaded(ISqlTransaction transcation, long loadId,
            String sourceNodeId, long batchId, int batchCount, boolean isBulkLoaded);

    public void updateTableReloadStatusFailed(long loadId, String sourceNodeId, long batchId);

    public void updateTableReloadStatusFailed(ISqlTransaction transaction, long loadId, String sourceNodeId, long batchId);

    public int updateTableReloadRequestsCancelled(long loadId, String sourceNodeId);

    public int cancelTableReloadRequest(TableReloadRequest request);

    public String reloadNode(String nodeId, boolean reverseLoad, String createBy);

    public String reloadTable(String nodeId, String catalogName, String schemaName, String tableName);

    public String reloadTable(String nodeId, String catalogName, String schemaName, String tableName, String overrideInitialLoadSelect);

    public String reloadTableImmediate(String nodeId, String catalogName, String schemaName, String tableName,
            String overrideInitialLoadSelect, String overrideChannelId);

    public boolean reloadMissingForeignKeyRows(long batchId, String nodeId, long dataId, long rowNumber);

    public void reloadMissingForeignKeyRowsForLoad(String sourceNodeId, long batchId, long rowNumber, Table table, CsvData data, String channelId);

    public void reloadMissingForeignKeyRowsReverse(String sourceNodeId, Table table, CsvData data,
            String channelId, boolean sendCorrectionToPeers);

    public void sendNewerDataToNode(ISqlTransaction transaction, String targetNodeId, String tableName, String pkCsvData,
            Date minCreateTime, String winningNodeId);

    /**
     * Sends a SQL command to the remote node for execution by creating a SQL event that is synced like other data
     * 
     * @param nodeId
     *            the remote node where the SQL statement will be executed
     * @param catalogName
     *            used to find the sym_trigger entry for table that will be associated with this event
     * @param schemaName
     *            used to find the sym_trigger entry for table that will be associated with this event
     * @param tableName
     *            used to find the sym_trigger entry for table that will be associated with this event
     * @param sql
     *            the SQL statement to run on the remote node database
     * @return message string indicating success or error
     */
    public String sendSQL(String nodeId, String catalogName, String schemaName, String tableName, String sql);

    public String sendSQL(String nodeId, String sql);

    public Map<Integer, ExtractRequest> insertReloadEvents(
            Node targetNode, boolean reverse, List<TableReloadRequest> reloadRequests,
            ProcessInfo processInfo, List<TriggerRouter> triggerRouters,
            Map<Integer, ExtractRequest> extractRequests,
            IReloadGenerator reloadGenerator);

    public long insertReloadEvent(ISqlTransaction transaction, Node targetNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory, String overrideInitialLoadSelect, boolean isLoad, long loadId, String createBy,
            Status status, long estimatedBatchRowCount);

    public void sendScript(String nodeId, String script, boolean isLoad);

    public boolean sendSchema(String nodeId, String catalogName, String schemaName,
            String tableName, boolean isLoad, boolean excludeIndices, boolean excludeForeignKeys,
            boolean excludeDefaults);

    /**
     * Update {@link Node} information for this node and call {@link IHeartbeatListener}s.
     */
    public void heartbeat(boolean force);

    /**
     * Updates the current node's heartbeat. If bypassTrigger is true, the update is applied locally to this node's own database only (via
     * {@link INodeService#updateNodeHostForCurrentNode(boolean)}) without going through the sync pipeline. If false, a full heartbeat runs and is broadcast to
     * other nodes.
     */
    public void updateNodeHostForCurrentNode(boolean bypassTrigger);

    public void insertHeartbeatEvent(Node node, boolean isReload);

    public long insertData(Data data);

    public long insertData(ISqlTransaction transaction, final Data data);

    public void insertData(ISqlTransaction transaction, List<Data> datas);

    public void insertDataEvents(ISqlTransaction transaction, List<DataEvent> events);

    public void insertDataAndDataEventAndOutgoingBatch(Data data, String channelId, List<Node> nodes, boolean isLoad, long loadId, String createBy);

    public long insertDataAndDataEventAndOutgoingBatch(ISqlTransaction transaction, Data data,
            String nodeId, boolean isLoad, long loadId, String createBy, Status status, long estimatedBatchRowCount);

    public long insertDataAndDataEventAndOutgoingBatch(Data data, String nodeId, boolean isLoad, long loadId, String createBy);

    public void insertSqlEvent(ISqlTransaction transaction, Node targetNode, String sql, boolean isLoad, long loadId, String createBy);

    public void insertSqlEvent(ISqlTransaction transaction, TriggerHistory history, String channelId, Node targetNode, String sql, boolean isLoad, long loadId,
            String externalData, String createBy);

    public void insertSqlEvent(Node targetNode, String sql, boolean isLoad, long loadId, String createBy);

    public void insertScriptEvent(String channelId, Node targetNode, String script, boolean isLoad,
            long loadId, String createBy);

    public void insertScriptEvent(ISqlTransaction transaction, String channelId,
            Node targetNode, String script, boolean isLoad, long loadId, String createBy);

    public void insertCreateEvent(Node targetNode, TriggerHistory triggerHistory, String createBy, boolean excludeIndices, boolean excludeForeignKeys,
            boolean excludeDefaults);

    public void insertCreateEvent(Node targetNode, TriggerHistory triggerHistory, boolean isLoad, long loadId, String createBy, boolean excludeIndices,
            boolean excludeForeignKeys, boolean excludeDefaults);

    public void insertCreateEvent(ISqlTransaction transaction, Node targetNode, TriggerHistory triggerHistory, String channelId, boolean isLoad, long loadId,
            String createBy, boolean excludeIndices, boolean excludeForeignKeys, boolean excludeDefaults, String rowData, String externalData);

    public Set<TriggerHistory> insertCreateEventsForOutgoingBatchInError(OutgoingBatch batch, String createBy);

    /**
     * Count the number of data ids in a range
     */
    public int countDataInRange(long firstDataId, long secondDataId);

    public int countData();

    public long countDataGaps();

    public List<DataGap> findDataGapsUnchecked();

    public List<DataGap> findDataGapsExpired();

    public List<DataGap> findDataGaps();

    public Date findCreateTimeOfEvent(long dataId);

    public Date findCreateTimeOfData(long dataId);

    public Date findNextCreateTimeOfDataStartingAt(long dataId);

    public Data createData(String catalogName, String schemaName, String tableName);

    public Data createData(String catalogName, String schemaName, String tableName, String whereClause);

    public Data createData(ISqlTransaction transaction, String catalogName, String schemaName, String tableName, String whereClause);

    public ISqlRowMapper<Data> getDataMapper();

    public List<Number> listDataIds(long batchId, String nodeId);

    public List<Data> listData(long batchId, String nodeId, long startDataId, String channelId, int maxRowsToRetrieve);

    public void insertDataGap(DataGap gap);

    public void insertDataGap(ISqlTransaction transaction, DataGap gap);

    public void insertDataGaps(ISqlTransaction transaction, Collection<DataGap> gaps);

    public void deleteDataGap(ISqlTransaction transaction, DataGap gap);

    public void deleteDataGaps(ISqlTransaction transaction, Collection<DataGap> gaps);

    public void deleteAllDataGaps(ISqlTransaction transaction);

    public void deleteDataGap(DataGap gap);

    public void expireDataGaps(ISqlTransaction transaction, Collection<DataGap> gaps);

    public void deleteCapturedConfigChannelData();

    public boolean fixLastDataGap();

    public long findMaxDataId();

    public Data findData(long dataId);

    public long findMinDataId();

    public ISqlReadCursor<Data> selectDataFor(Batch batch);

    public ISqlReadCursor<Data> selectDataFor(Long batchId, String channelId);

    public ISqlReadCursor<Data> selectDataFor(Long batchId, String targetNodeId, boolean isContainsBigLob);

    public Map<String, Date> getLastDataCaptureByChannel();

    public String findNodeIdsByNodeGroupId();

    public int resendBatchAsReload(long batchId, String nodeId);

    public int resendDataAsReload(long minDataId, long maxDataId);

    public int reCaptureData(long minDataId, long maxDataId);
}