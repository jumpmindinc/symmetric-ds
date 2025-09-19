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
package org.jumpmind.symmetric.service.impl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.nio.charset.Charset;
import java.sql.DataTruncation;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.InvalidSqlException;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.sql.SqlException;
import org.jumpmind.db.sql.UniqueKeyException;
import org.jumpmind.db.sql.mapper.NumberMapper;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.db.util.TableRow;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.db.SequenceIdentifier;
import org.jumpmind.symmetric.ext.IHeartbeatListener;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.CsvUtils;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.job.PushHeartbeatListener;
import org.jumpmind.symmetric.load.IReloadGenerator;
import org.jumpmind.symmetric.load.IReloadListener;
import org.jumpmind.symmetric.load.IReloadVariableFilter;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataEvent;
import org.jumpmind.symmetric.model.DataGap;
import org.jumpmind.symmetric.model.ExtractRequest;
import org.jumpmind.symmetric.model.ExtractRequest.ExtractStatus;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadRequestKey;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerReBuildReason;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.ClusterConstants;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IFileSyncService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.jumpmind.util.ExceptionUtils;
import org.jumpmind.util.FormatUtils;

/**
 * @see IDataService
 */
public class DataService extends AbstractService implements IDataService {
    private ISymmetricEngine engine;
    private IExtensionService extensionService;
    public static final int RECAPTURE_DATA_COMMIT_LIMIT = 1000;
    public static final int PROGRESS_LOG_UPDATE_DELAY_MS = 30000;
    public static final transient String TIMESTAMP_ISO_JSON_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"; // Zulu == UTC is used in transaction ID for stale data
    public static final transient DateTimeFormatter isoJsonDateTimeFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_ISO_JSON_FORMAT).withZone(ZoneId.from(
            ZoneOffset.UTC));

    public DataService(ISymmetricEngine engine, IExtensionService extensionService) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engine = engine;
        this.extensionService = extensionService;
        extensionService.addExtensionPoint(new PushHeartbeatListener(engine));
        setSqlMap(new DataServiceSqlMap(symmetricDialect.getPlatform(),
                createSqlReplacementTokens()));
    }

    protected Map<IHeartbeatListener, Long> lastHeartbeatTimestamps = new HashMap<IHeartbeatListener, Long>();

    public boolean insertReloadEvent(TableReloadRequest request, boolean deleteAtClient) {
        boolean successful = false;
        if (request != null) {
            ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
            INodeService nodeService = engine.getNodeService();
            Node targetNode = nodeService.findNode(request.getTargetNodeId());
            if (targetNode != null) {
                TriggerRouter triggerRouter = triggerRouterService.getTriggerRouterForCurrentNode(
                        request.getTriggerId(), request.getRouterId(), false);
                if (triggerRouter != null) {
                    Trigger trigger = triggerRouter.getTrigger();
                    Router router = triggerRouter.getRouter();
                    NodeGroupLink link = router.getNodeGroupLink();
                    Node me = nodeService.findIdentity();
                    if (link.getSourceNodeGroupId().equals(me.getNodeGroupId())) {
                        if (link.getTargetNodeGroupId().equals(targetNode.getNodeGroupId())) {
                            TriggerHistory triggerHistory = lookupTriggerHistory(trigger);
                            ISqlTransaction transaction = null;
                            try {
                                transaction = sqlTemplate.startSqlTransaction();
                                if (parameterService
                                        .is(ParameterConstants.INITIAL_LOAD_DELETE_BEFORE_RELOAD)) {
                                    String overrideDeleteStatement = StringUtils.isNotBlank(request
                                            .getBeforeCustomSql()) ? request.getBeforeCustomSql()
                                                    : null;
                                    insertPurgeEvent(transaction, targetNode, triggerRouter,
                                            triggerHistory, false, overrideDeleteStatement, -1,
                                            null);
                                }
                                insertReloadEvent(transaction, targetNode, triggerRouter,
                                        triggerHistory, request.getReloadSelect(), false, -1, null,
                                        Status.NE, -1);
                                if (!targetNode.requires13Compatiblity() && deleteAtClient) {
                                    insertSqlEvent(
                                            transaction,
                                            triggerHistory,
                                            trigger.getChannelId(),
                                            targetNode,
                                            String.format(
                                                    "delete from %s where target_node_id='%s' and source_node_id='%s' and trigger_id='%s' and router_id='%s'",
                                                    TableConstants
                                                            .getTableName(
                                                                    tablePrefix,
                                                                    TableConstants.SYM_TABLE_RELOAD_REQUEST),
                                                    request.getTargetNodeId(), request
                                                            .getSourceNodeId(), request
                                                                    .getTriggerId(), request.getRouterId()),
                                            false, -1, null);
                                }
                                deleteTableReloadRequest(transaction, request);
                                transaction.commit();
                            } catch (Error ex) {
                                if (transaction != null) {
                                    transaction.rollback();
                                }
                                throw ex;
                            } catch (RuntimeException ex) {
                                if (transaction != null) {
                                    transaction.rollback();
                                }
                                throw ex;
                            } finally {
                                close(transaction);
                            }
                        } else {
                            log.error(
                                    "Could not reload table {} for node {} because the router {} target node group id {} did not match",
                                    new Object[] { trigger.getSourceTableName(), request.getTargetNodeId(),
                                            request.getRouterId(), link.getTargetNodeGroupId() });
                        }
                    } else {
                        log.error(
                                "Could not reload table {}  for node {} because the router {} source node group id {} did not match",
                                new Object[] { trigger.getSourceTableName(), request.getTargetNodeId(), request.getRouterId(),
                                        link.getSourceNodeGroupId() });
                    }
                } else {
                    log.error(
                            "Could not reload table for node {} because the trigger router ({}, {}) could not be found",
                            new Object[] { request.getTargetNodeId(), request.getTriggerId(),
                                    request.getRouterId() });
                }
            } else {
                log.error("Could not reload table for node {} because the target node could not be found",
                        request.getTargetNodeId());
            }
        }
        return successful;
    }

    /**
     * Helper. Creates an ISO-compliant transaction Id string for specified instant and prefix.
     */
    public String generateTransactionIdFromTimestamp(String prefix, Instant instant) {
        return prefix + isoJsonDateTimeFormatter.format(instant);
    }

    @Override
    public int cancelTableReloadRequest(TableReloadRequest request) {
        return sqlTemplate.update(
                getSql("cancelTableReloadRequest"),
                new Object[] { new Date(), request.getSourceNodeId(), request.getTargetNodeId(),
                        request.getTriggerId(), request.getRouterId(), request.getCreateTime() },
                new int[] { Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP });
    }

    protected void deleteTableReloadRequest(ISqlTransaction sqlTransaction,
            TableReloadRequest request) {
        sqlTransaction.prepareAndExecute(
                getSql("deleteTableReloadRequest"),
                new Object[] { request.getSourceNodeId(), request.getTargetNodeId(),
                        request.getTriggerId(), request.getRouterId() }, new int[] { Types.VARCHAR,
                                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR });
    }

    @Override
    public void insertTableReloadRequest(TableReloadRequest request) {
        ISqlTransaction transaction = null;
        try {
            transaction = engine.getDatabasePlatform().getSqlTemplate().startSqlTransaction();
            insertTableReloadRequest(transaction, request);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
    }

    @Override
    public void insertTableReloadRequest(ISqlTransaction transaction, TableReloadRequest request) {
        Date time = new Date();
        request.setLastUpdateTime(time);
        if (request.getCreateTime() == null) {
            request.setCreateTime(time);
        }
        request.setCreateTime(new Date((request.getCreateTime().getTime() / 10) * 10));
        transaction.prepareAndExecute(
                getSql("insertTableReloadRequest"),
                new Object[] { request.getReloadSelect(), request.getBeforeCustomSql(),
                        request.getCreateTime(), request.getLastUpdateBy(),
                        request.getLastUpdateTime(), request.getSourceNodeId(),
                        request.getTargetNodeId(), request.getTriggerId(),
                        request.getRouterId(), request.isCreateTable() ? 1 : 0,
                        request.isDeleteFirst() ? 1 : 0, request.getChannelId() });
    }

    @Override
    public TableReloadRequest getTableReloadRequest(final TableReloadRequestKey key) {
        return sqlTemplate.queryForObject(getSql("selectTableReloadRequest"),
                new ISqlRowMapper<TableReloadRequest>() {
                    @Override
                    public TableReloadRequest mapRow(Row rs) {
                        TableReloadRequest request = new TableReloadRequest(key);
                        request.setReloadSelect(rs.getString("reload_select"));
                        request.setReloadTime(rs.getDateTime("reload_time"));
                        request.setBeforeCustomSql(rs.getString("before_custom_sql"));
                        request.setCreateTime(rs.getDateTime("create_time"));
                        request.setLastUpdateBy(rs.getString("last_update_by"));
                        request.setLastUpdateTime(rs.getDateTime("last_update_time"));
                        request.setLoadId(rs.getInt("load_id"));
                        return request;
                    }
                }, key.getSourceNodeId(), key.getTargetNodeId(), key.getTriggerId(),
                key.getRouterId(), key.getCreateTime());
    }

    @Override
    public TableReloadRequest getTableReloadRequest(long loadId) {
        List<TableReloadRequest> collapsedRequests = getTableReloadRequests(loadId);
        return collapsedRequests == null || collapsedRequests.size() == 0 ? null : collapsedRequests.get(0);
    }

    @Override
    public List<TableReloadRequest> getTableReloadRequests(long loadId) {
        List<TableReloadRequest> requests = sqlTemplate.query(getSql("selectTableReloadRequestsByLoadId"),
                new TableReloadRequestMapper(), loadId);
        List<TableReloadRequest> collapsedRequests = collapseTableReloadRequestsByLoadId(requests);
        return collapsedRequests;
    }

    @Override
    public TableReloadRequest getTableReloadRequest(long loadId, String triggerId, String routerId) {
        List<TableReloadRequest> requests = sqlTemplate.query(getSql("selectTableReloadRequestsByLoadIdTriggerRouter"),
                new TableReloadRequestMapper(), loadId, triggerId, routerId);
        if (requests == null || requests.size() == 0) {
            requests = sqlTemplate.query(getSql("selectTableReloadRequestsByLoadIdTriggerRouter"),
                    new TableReloadRequestMapper(), loadId, ParameterConstants.ALL, ParameterConstants.ALL);
        }
        return requests == null || requests.size() == 0 ? null : requests.get(0);
    }

    @Override
    public List<TableReloadRequest> getTableReloadRequestToProcess(final String sourceNodeId) {
        return sqlTemplate.query(getSql("selectTableReloadRequestToProcess"),
                new ISqlRowMapper<TableReloadRequest>() {
                    @Override
                    public TableReloadRequest mapRow(Row rs) {
                        TableReloadRequest request = new TableReloadRequest();
                        request.setSourceNodeId(sourceNodeId);
                        request.setTargetNodeId(rs.getString("target_node_id"));
                        request.setCreateTable(rs.getBoolean("create_table"));
                        request.setDeleteFirst(rs.getBoolean("delete_first"));
                        request.setReloadSelect(rs.getString("reload_select"));
                        request.setReloadTime(rs.getDateTime("reload_time"));
                        request.setBeforeCustomSql(rs.getString("before_custom_sql"));
                        request.setChannelId(rs.getString("channel_id"));
                        request.setTriggerId(rs.getString("trigger_id"));
                        request.setRouterId(rs.getString("router_id"));
                        request.setLoadId(rs.getLong("load_id"));
                        request.setCreateTime(rs.getDateTime("create_time"));
                        request.setLastUpdateBy(rs.getString("last_update_by"));
                        request.setLastUpdateTime(rs.getDateTime("last_update_time"));
                        return request;
                    }
                }, sourceNodeId);
    }

    @Override
    public List<TableReloadRequest> getTableReloadRequestToProcessByTarget(final String targetNodeId) {
        return sqlTemplate.query(getSql("selectTableReloadRequestToProcessByTarget"),
                new ISqlRowMapper<TableReloadRequest>() {
                    @Override
                    public TableReloadRequest mapRow(Row rs) {
                        TableReloadRequest request = new TableReloadRequest();
                        request.setSourceNodeId(rs.getString("source_node_id"));
                        request.setTargetNodeId(targetNodeId);
                        request.setCreateTable(rs.getBoolean("create_table"));
                        request.setDeleteFirst(rs.getBoolean("delete_first"));
                        request.setReloadSelect(rs.getString("reload_select"));
                        request.setReloadTime(rs.getDateTime("reload_time"));
                        request.setBeforeCustomSql(rs.getString("before_custom_sql"));
                        request.setChannelId(rs.getString("channel_id"));
                        request.setTriggerId(rs.getString("trigger_id"));
                        request.setRouterId(rs.getString("router_id"));
                        request.setLoadId(rs.getLong("load_id"));
                        request.setCreateTime(rs.getDateTime("create_time"));
                        request.setLastUpdateBy(rs.getString("last_update_by"));
                        request.setLastUpdateTime(rs.getDateTime("last_update_time"));
                        return request;
                    }
                }, targetNodeId);
    }

    public List<TableReloadRequest> getTableReloadRequests() {
        return sqlTemplateDirty.query(getSql("selectTableReloadRequests"),
                new TableReloadRequestMapper());
    }

    @Override
    public List<TableReloadStatus> getTableReloadStatus() {
        return sqlTemplateDirty.query(getSql("selectTableReloadStatus", "orderTableReloadStatus"),
                new TableReloadStatusMapper());
    }

    @Override
    public List<TableReloadStatus> getOutgoingTableReloadStatus() {
        return sqlTemplateDirty.query(getSql("selectTableReloadStatus", "whereSourceNodeId", "orderTableReloadStatus"),
                new TableReloadStatusMapper(), engine.getNodeId());
    }

    @Override
    public List<TableReloadStatus> getIncomingTableReloadStatus() {
        return sqlTemplateDirty.query(getSql("selectTableReloadStatus", "whereTargetNodeId", "orderTableReloadStatus"),
                new TableReloadStatusMapper(), engine.getNodeId());
    }

    @Override
    public List<TableReloadStatus> getActiveTableReloadStatus() {
        return sqlTemplateDirty.query(getSql("selectActiveTableReloadStatus", "orderTableReloadStatus"),
                new TableReloadStatusMapper());
    }

    @Override
    public List<TableReloadStatus> getActiveOutgoingTableReloadStatus() {
        return sqlTemplateDirty.query(getSql("selectActiveTableReloadStatus", "andSourceNodeId", "orderTableReloadStatus"),
                new TableReloadStatusMapper(), engine.getNodeId());
    }

    @Override
    public List<TableReloadStatus> getActiveIncomingTableReloadStatus() {
        return sqlTemplateDirty.query(getSql("selectActiveTableReloadStatus", "andTargetNodeId", "orderTableReloadStatus"),
                new TableReloadStatusMapper(), engine.getNodeId());
    }

    @Override
    public TableReloadStatus getTableReloadStatusByLoadIdAndSourceNodeId(long loadId, String sourceNodeId) {
        return sqlTemplateDirty.queryForObject(getSql("selectTableReloadStatusByLoadIdSourceNodeId"),
                new TableReloadStatusMapper(), loadId, sourceNodeId);
    }

    @Override
    public List<TableReloadStatus> getTableReloadStatusByTarget(String targetNodeId) {
        return sqlTemplateDirty.query(getSql("selectTableReloadStatusByTargetNodeId"),
                new TableReloadStatusMapper(), targetNodeId);
    }

    public List<TableReloadRequest> getTableReloadRequestByLoadId() {
        return collapseTableReloadRequestsByLoadId(getTableReloadRequests());
    }

    public List<TableReloadRequest> collapseTableReloadRequestsByLoadId(List<TableReloadRequest> requests) {
        List<TableReloadRequest> collapsedRequests = new ArrayList<TableReloadRequest>();
        long previousLoadId = -1;
        TableReloadRequest summary = null;
        for (TableReloadRequest request : requests) {
            if (request.getLoadId() != previousLoadId) {
                if (summary != null) {
                    collapsedRequests.add(summary);
                }
                summary = new TableReloadRequest();
                summary.setTargetNodeId(request.getTargetNodeId());
                summary.setSourceNodeId(request.getSourceNodeId());
                summary.setCreateTable(request.isCreateTable());
                summary.setDeleteFirst(request.isDeleteFirst());
                summary.setBeforeCustomSql(request.getBeforeCustomSql());
                summary.setCreateTime(request.getCreateTime());
                summary.setLastUpdateTime(request.getLastUpdateTime());
                summary.setLastUpdateBy(request.getLastUpdateBy());
                summary.setProcessed(request.isProcessed());
                summary.setLoadId(request.getLoadId());
                summary.setProcessed(request.isProcessed());
                summary.setChannelId(request.getChannelId());
                summary.setTriggerId(request.getTriggerId());
                summary.setRouterId(request.getRouterId());
                summary.setReloadSelect(request.getReloadSelect());
            }
            previousLoadId = request.getLoadId();
        }
        if (summary != null) {
            collapsedRequests.add(summary);
        }
        return collapsedRequests;
    }

    @Override
    public Map<Long, List<TableReloadRequest>> getTableReloadRequestByLoadIdMap() {
        Map<Long, List<TableReloadRequest>> requestMap = new HashMap<Long, List<TableReloadRequest>>();
        for (TableReloadRequest request : getTableReloadRequests()) {
            List<TableReloadRequest> requestList = requestMap.get(request.getLoadId());
            if (requestList == null) {
                requestList = new ArrayList<TableReloadRequest>();
                requestMap.put(request.getLoadId(), requestList);
            }
            requestList.add(request);
        }
        return requestMap;
    }

    @Override
    public TableReloadStatus updateTableReloadStatusDataLoaded(ISqlTransaction transaction, long loadId,
            String sourceNodeId, long batchId, int batchCount, boolean isBulkLoaded) {
        int idType = symmetricDialect.getSqlTypeForIds();
        int count;
        if (platform.supportsParametersInSelect()) {
            count = transaction.prepareAndExecute(getSql("updateTableReloadStatusDataLoaded"),
                    new Object[] { batchId, batchCount, batchId, batchCount, batchId, batchCount,
                            batchId, batchCount, batchId, batchCount, batchId, batchCount, new Date(),
                            batchId, batchCount, batchId, batchCount, batchId, batchCount, loadId, sourceNodeId, new Date(), batchId,
                            isBulkLoaded, batchId, batchId, loadId, sourceNodeId, loadId, sourceNodeId },
                    new int[] { idType, Types.NUMERIC, idType, Types.NUMERIC, idType, Types.NUMERIC,
                            idType, Types.NUMERIC, idType, Types.NUMERIC, idType, Types.NUMERIC, Types.TIMESTAMP,
                            idType, Types.NUMERIC, idType, Types.NUMERIC, idType, Types.NUMERIC, idType,
                            Types.VARCHAR, Types.TIMESTAMP, idType, Types.NUMERIC, idType, idType, idType, Types.VARCHAR, idType,
                            Types.VARCHAR });
        } else {
            String sql = getSql("updateTableReloadStatusDataLoadedNoParams");
            sql = FormatUtils.replace("batchId", String.valueOf(batchId), sql);
            sql = FormatUtils.replace("batchCount", String.valueOf(batchCount), sql);
            sql = FormatUtils.replace("loadId", String.valueOf(loadId), sql);
            sql = FormatUtils.replace("nodeId", sourceNodeId, sql);
            sql = FormatUtils.replace("isBulkLoaded", isBulkLoaded ? "1" : "0", sql);
            count = transaction.prepareAndExecute(sql);
        }
        TableReloadStatus status = null;
        List<TableReloadStatus> statuses = transaction.query(getSql("selectTableReloadStatusByLoadIdSourceNodeId"),
                new TableReloadStatusMapper(), new Object[] { loadId, sourceNodeId }, new int[] { idType, Types.VARCHAR });
        if (statuses != null && statuses.size() > 0) {
            status = statuses.get(0);
        }
        if (count == 0) {
            if (status != null && !status.isCompleted()) {
                log.warn("No load status updated for source node {} load ID {} batch ID {}", sourceNodeId, loadId, batchId);
            } else if (status == null) {
                log.warn("No load status found for source node {} load ID {} batch ID {}", sourceNodeId, loadId, batchId);
            }
            status = null;
        }
        return status;
    }

    @Override
    public void updateTableReloadStatusFailed(long loadId, String sourceNodeId, long batchId) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            updateTableReloadStatusFailed(transaction, loadId, sourceNodeId, batchId);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public void updateTableReloadStatusFailed(ISqlTransaction transaction, long loadId, String sourceNodeId, long batchId) {
        int idType = symmetricDialect.getSqlTypeForIds();
        if (platform.supportsParametersInSelect()) {
            transaction.prepareAndExecute(getSql("updateTableReloadStatusFailed"),
                    new Object[] { batchId, batchId, batchId, loadId, sourceNodeId },
                    new int[] { idType, idType, idType, idType, Types.VARCHAR });
        } else {
            String sql = getSql("updateTableReloadStatusFailedNoParams");
            sql = FormatUtils.replace("batchId", String.valueOf(batchId), sql);
            sql = FormatUtils.replace("loadId", String.valueOf(loadId), sql);
            sql = FormatUtils.replace("nodeId", sourceNodeId, sql);
            transaction.prepareAndExecute(sql);
        }
    }

    public void updateTableReloadStatusDataCounts(ISqlTransaction transaction, long loadId, String sourceNodeId,
            long startBatchId, long endBatchId, long dataBatchCount, long rowsCount) {
        int idType = symmetricDialect.getSqlTypeForIds();
        String sql;
        Object[] args;
        int[] types;
        if (platform.supportsParametersInSelect()) {
            sql = getSql("updateTableReloadStatusDataCounts");
            args = new Object[] { startBatchId, endBatchId, dataBatchCount, rowsCount, new Date(), loadId, sourceNodeId };
            types = new int[] { idType, idType, Types.NUMERIC, Types.NUMERIC, Types.TIMESTAMP, idType, Types.VARCHAR };
        } else {
            sql = getSql("updateTableReloadStatusDataCountsNoParamsInSelect");
            sql = FormatUtils.replace("batchCount", String.valueOf(dataBatchCount), sql);
            sql = FormatUtils.replace("rowCount", String.valueOf(rowsCount), sql);
            args = new Object[] { startBatchId, endBatchId, new Date(), loadId, sourceNodeId };
            types = new int[] { idType, idType, Types.TIMESTAMP, idType, Types.VARCHAR };
        }
        if (transaction == null) {
            try {
                transaction = sqlTemplate.startSqlTransaction();
                transaction.prepareAndExecute(sql, args, types);
                transaction.commit();
            } catch (Error ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } finally {
                close(transaction);
            }
        } else {
            transaction.prepareAndExecute(sql, args, types);
        }
    }

    public void updateTableReloadRequestsLoadId(ISqlTransaction transaction, long loadId, TableReloadRequest request) {
        Object[] args = new Object[] { loadId, new Date(), request.getTargetNodeId(), request.getSourceNodeId(),
                request.getTriggerId(), request.getRouterId(), request.getCreateTime() };
        String sql = getSql("updateTableReloadRequestLoadId");
        int[] types = new int[] { symmetricDialect.getSqlTypeForIds(), Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP };
        if (transaction == null) {
            try {
                transaction = sqlTemplate.startSqlTransaction();
                transaction.prepareAndExecute(sql, args, types);
                transaction.commit();
            } catch (Error ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } finally {
                close(transaction);
            }
        } else {
            transaction.prepareAndExecute(sql, args, types);
        }
    }

    public void updateTableReloadStatusTableCount(ISqlTransaction transaction, long loadId, String sourceNodeId, int tableCount) {
        Object[] args = new Object[] { tableCount, new Date(), loadId, sourceNodeId };
        String sql = getSql("updateTableReloadStatusTableCount");
        int[] types = new int[] { Types.NUMERIC, Types.TIMESTAMP, symmetricDialect.getSqlTypeForIds(), Types.VARCHAR };
        if (transaction == null) {
            try {
                transaction = sqlTemplate.startSqlTransaction();
                transaction.prepareAndExecute(sql, args, types);
                transaction.commit();
            } catch (Error ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } finally {
                close(transaction);
            }
        } else {
            transaction.prepareAndExecute(sql, args, types);
        }
    }

    private void updateTableReloadStatusFinalizeCount(ISqlTransaction transaction, long loadId, String sourceNodeId, int finalizeBatchCount) {
        Object[] args = new Object[] { finalizeBatchCount, new Date(), loadId, sourceNodeId };
        String sql = getSql("updateTableReloadStatusFinalizeCount");
        int[] types = new int[] { Types.NUMERIC, Types.TIMESTAMP, symmetricDialect.getSqlTypeForIds(), Types.VARCHAR };
        if (transaction == null) {
            try {
                transaction = sqlTemplate.startSqlTransaction();
                transaction.prepareAndExecute(sql, args, types);
                transaction.commit();
            } catch (Error ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } finally {
                close(transaction);
            }
        } else {
            transaction.prepareAndExecute(sql,
                    finalizeBatchCount, new Date(), loadId, sourceNodeId);
        }
    }

    public void createTableReloadStatus(ISqlTransaction transaction, long loadId, boolean isFullLoad, String sourceNodeId, String targetNodeId) {
        Date now = new Date();
        Object[] argsDelete = new Object[] { loadId, sourceNodeId };
        String sqlDelete = getSql("deleteTableReloadStatus");
        int[] typesDelete = new int[] { Types.NUMERIC, Types.VARCHAR };
        Object[] args = new Object[] { loadId, targetNodeId, sourceNodeId, isFullLoad ? 1 : 0, now, now, -1, -1, -1 };
        String sql = getSql("insertTableReloadStatus");
        int[] types = new int[] { symmetricDialect.getSqlTypeForIds(), Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.TIMESTAMP, Types.TIMESTAMP,
                Types.NUMERIC, Types.NUMERIC, Types.NUMERIC };
        if (transaction == null) {
            try {
                transaction = sqlTemplate.startSqlTransaction();
                transaction.prepareAndExecute(sqlDelete, argsDelete, typesDelete);
                transaction.prepareAndExecute(sql, args, types);
                transaction.commit();
            } catch (Error ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } finally {
                close(transaction);
            }
        } else {
            transaction.prepareAndExecute(sqlDelete, argsDelete, typesDelete);
            transaction.prepareAndExecute(sql, args, types);
        }
    }

    public void updateTableReloadStatusSetupCount(ISqlTransaction transaction, long loadId, String sourceNodeId,
            int setupBatchCount) {
        Object[] args = new Object[] { setupBatchCount, new Date(), loadId, sourceNodeId };
        String sql = getSql("updateTableReloadStatusSetupCount");
        int[] types = new int[] { Types.NUMERIC, Types.TIMESTAMP, symmetricDialect.getSqlTypeForIds(), Types.VARCHAR };
        if (transaction == null) {
            try {
                transaction = sqlTemplate.startSqlTransaction();
                transaction.prepareAndExecute(sql, args, types);
                transaction.commit();
            } catch (Error ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } catch (RuntimeException ex) {
                if (transaction != null) {
                    transaction.rollback();
                }
                throw ex;
            } finally {
                close(transaction);
            }
        } else {
            transaction.prepareAndExecute(sql, args, types);
        }
    }

    @Override
    public int updateTableReloadRequestsCancelled(long loadId, String sourceNodeId) {
        ISqlTransaction transaction = null;
        int count = 0;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            Date now = new Date();
            count = transaction.prepareAndExecute(getSql("updateTableReloadStatusCancelled"),
                    new Object[] { now, now, loadId, sourceNodeId },
                    new int[] { Types.TIMESTAMP, Types.TIMESTAMP, symmetricDialect.getSqlTypeForIds(), Types.VARCHAR });
            transaction.prepareAndExecute(getSql("updateProcessedTableReloadRequest"), now, loadId);
            transaction.commit();
            return count;
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    protected int updateTableReloadRequestsError(long loadId, String sourceNodeId, int errorCode, String sqlState, String message) {
        return sqlTemplate.update(getSql("updateTableReloadStatusError"), errorCode, StringUtils.left(sqlState, 10), message,
                loadId, sourceNodeId);
    }

    protected class TableReloadRequestMapper implements ISqlRowMapper<TableReloadRequest> {
        @Override
        public TableReloadRequest mapRow(Row rs) {
            TableReloadRequest request = new TableReloadRequest();
            request.setSourceNodeId(rs.getString("source_node_id"));
            request.setTargetNodeId(rs.getString("target_node_id"));
            request.setCreateTable(rs.getBoolean("create_table"));
            request.setDeleteFirst(rs.getBoolean("delete_first"));
            request.setReloadSelect(rs.getString("reload_select"));
            request.setReloadTime(rs.getDateTime("reload_time"));
            request.setBeforeCustomSql(rs.getString("before_custom_sql"));
            request.setChannelId(rs.getString("channel_id"));
            request.setTriggerId(rs.getString("trigger_id"));
            request.setRouterId(rs.getString("router_id"));
            request.setCreateTime(rs.getDateTime("create_time"));
            request.setLastUpdateBy(rs.getString("last_update_by"));
            request.setLastUpdateTime(rs.getDateTime("last_update_time"));
            request.setLoadId(rs.getInt("load_id"));
            request.setProcessed(rs.getBoolean("processed"));
            return request;
        }
    }

    protected class TableReloadStatusMapper implements ISqlRowMapper<TableReloadStatus> {
        @Override
        public TableReloadStatus mapRow(Row rs) {
            TableReloadStatus request = new TableReloadStatus();
            request.setLoadId(rs.getInt("load_id"));
            request.setSourceNodeId(rs.getString("source_node_id"));
            request.setTargetNodeId(rs.getString("target_node_id"));
            request.setCompleted(rs.getBoolean("completed"));
            request.setCancelled(rs.getBoolean("cancelled"));
            request.setFullLoad(rs.getBoolean("full_load"));
            request.setStartDataBatchId(rs.getLong("start_data_batch_id"));
            request.setEndDataBatchId(rs.getLong("end_data_batch_id"));
            request.setSetupBatchCount(rs.getInt("setup_batch_count"));
            request.setDataBatchCount(rs.getInt("data_batch_count"));
            request.setFinalizeBatchCount(rs.getInt("finalize_batch_count"));
            request.setSetupBatchLoaded(rs.getInt("setup_batch_loaded"));
            request.setDataBatchLoaded(rs.getInt("data_batch_loaded"));
            request.setFinalizeBatchLoaded(rs.getInt("finalize_batch_loaded"));
            request.setTableCount(rs.getInt("table_count"));
            request.setRowsLoaded(rs.getLong("rows_loaded"));
            request.setRowsCount(rs.getLong("rows_count"));
            request.setErrorFlag(rs.getBoolean("error_flag"));
            request.setSqlState(rs.getString("sql_state"));
            request.setSqlCode(rs.getInt("sql_code"));
            request.setSqlMessage(rs.getString("sql_message"));
            request.setStartTime(rs.getDateTime("start_time"));
            request.setEndTime(rs.getDateTime("end_time"));
            request.setLastUpdateTime(rs.getDateTime("last_update_time"));
            request.setLastUpdatedBy(rs.getString("last_update_by"));
            request.setNumBatchesBulkLoaded(rs.getInt("batch_bulk_load_count"));
            request.setNumRowsBulkLoaded(rs.getLong("row_bulk_load_count"));
            return request;
        }
    }

    protected long insertRequestedOutgoingBatches(ISqlTransaction transaction, Node targetNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory,
            String overrideInitialLoadSelect, long loadId, String createBy,
            String channelId, long totalRows, long maxRowsPerBatch, long batchCount) {
        long startBatchId = 0;
        if (platform.supportsMultiThreadedTransactions()) {
            startBatchId = engine.getSequenceService().nextRange(Constants.SEQUENCE_OUTGOING_BATCH, batchCount);
        } else {
            startBatchId = engine.getSequenceService().nextRange(transaction, Constants.SEQUENCE_OUTGOING_BATCH, batchCount);
        }
        String tableName = triggerHistory.getSourceTableName().toLowerCase();
        for (int i = 0; i < batchCount; i++) {
            long batchId = startBatchId + i;
            long rowCount = totalRows;
            if (rowCount > maxRowsPerBatch) {
                rowCount = maxRowsPerBatch;
            }
            OutgoingBatch batch = new OutgoingBatch(targetNode.getNodeId(), channelId, Status.RQ);
            batch.setBatchId(batchId);
            batch.setLoadId(loadId);
            batch.setCreateBy(createBy);
            batch.setLoadFlag(true);
            batch.incrementRowCount(DataEventType.RELOAD);
            batch.setDataRowCount(rowCount);
            batch.incrementTableCount(tableName);
            batch.setExtractJobFlag(true);
            engine.getOutgoingBatchService().insertOutgoingBatch(transaction, batch);
            if (i == 0) {
                Data data = new Data(triggerHistory.getSourceTableName(), DataEventType.RELOAD,
                        overrideInitialLoadSelect != null ? overrideInitialLoadSelect : triggerRouter.getInitialLoadSelect(), null,
                        triggerHistory, channelId, null, null);
                data.setNodeList(targetNode.getNodeId());
                data.setPreRouted(true);
                long dataId = insertData(transaction, data);
                insertDataEvent(transaction, new DataEvent(dataId, batchId));
            }
            totalRows -= rowCount;
        }
        return startBatchId;
    }

    /**
     * @return If isLoad then return the inserted batch id otherwise return the data id
     */
    @Override
    public long insertReloadEvent(ISqlTransaction transaction, Node targetNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory,
            String overrideInitialLoadSelect, boolean isLoad, long loadId, String createBy,
            Status status, long estimatedBatchRowCount) {
        String channelId = getReloadChannelIdForTrigger(triggerRouter.getTrigger(), engine
                .getConfigurationService().getChannels(false));
        return insertReloadEvent(transaction, targetNode, triggerRouter, triggerHistory,
                overrideInitialLoadSelect, isLoad, loadId, createBy, status, channelId, estimatedBatchRowCount);
    }

    /**
     * @param estimatedBatchRowCount
     *            TODO
     * @return If isLoad then return the inserted batch id otherwise return the data id
     */
    public long insertReloadEvent(ISqlTransaction transaction, Node targetNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory,
            String overrideInitialLoadSelect, boolean isLoad, long loadId, String createBy,
            Status status, String channelId, long estimatedBatchRowCount) {
        return insertReloadEvent(transaction, targetNode, triggerRouter, triggerHistory,
                overrideInitialLoadSelect, isLoad, loadId, createBy,
                status, channelId, estimatedBatchRowCount, isLoad);
    }

    public long insertReloadEventImmediate(ISqlTransaction transaction, Node targetNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory,
            String overrideInitialLoadSelect, boolean isLoad, long loadId, String createBy,
            Status status, String channelId, long estimatedBatchRowCount) {
        if (triggerHistory == null) {
            triggerHistory = lookupTriggerHistory(triggerRouter.getTrigger());
        }
        Data data = new Data(triggerHistory.getSourceTableName(), DataEventType.RELOAD,
                overrideInitialLoadSelect != null ? overrideInitialLoadSelect
                        : triggerRouter
                                .getInitialLoadSelect(), null, triggerHistory, channelId,
                null, null);
        data.setNodeList(targetNode.getNodeId());
        return insertDataAndDataEventAndOutgoingBatch(transaction, data,
                targetNode.getNodeId(), isLoad,
                loadId, createBy, status, channelId, estimatedBatchRowCount);
    }

    protected long insertReloadEvent(ISqlTransaction transaction, Node targetNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory,
            String overrideInitialLoadSelect, boolean isLoad, long loadId, String createBy,
            Status status, String channelId, long estimatedBatchRowCount, boolean isImmediate) {
        if (triggerHistory == null) {
            triggerHistory = lookupTriggerHistory(triggerRouter.getTrigger());
        }
        Data data = new Data(triggerHistory.getSourceTableName(), DataEventType.RELOAD,
                overrideInitialLoadSelect != null ? overrideInitialLoadSelect
                        : triggerRouter
                                .getInitialLoadSelect(), null, triggerHistory, channelId,
                null, null);
        data.setNodeList(targetNode.getNodeId());
        if (isImmediate) {
            return insertDataAndDataEventAndOutgoingBatch(transaction, data,
                    targetNode.getNodeId(), isLoad,
                    loadId, createBy, status, null, estimatedBatchRowCount);
        } else {
            return insertData(transaction, data);
        }
    }

    protected String getReloadChannelIdForTrigger(Trigger trigger, Map<String, Channel> channels) {
        String channelId = trigger != null ? trigger.getChannelId() : Constants.CHANNEL_DEFAULT;
        if (parameterService.is(ParameterConstants.INITIAL_LOAD_USE_RELOAD_CHANNEL)) {
            Channel normalChannel = channels.get(channelId);
            Channel reloadChannel = channels.get(trigger != null ? trigger.getReloadChannelId()
                    : Constants.CHANNEL_RELOAD);
            if (normalChannel != null && normalChannel.isFileSyncFlag()) {
                if (reloadChannel != null && reloadChannel.isFileSyncFlag()) {
                    channelId = reloadChannel.getChannelId();
                }
            } else {
                if (reloadChannel != null && reloadChannel.isReloadFlag()) {
                    channelId = reloadChannel.getChannelId();
                } else {
                    channelId = Constants.CHANNEL_RELOAD;
                }
            }
        }
        return channelId;
    }

    @Override
    public Map<Integer, ExtractRequest> insertReloadEvents(Node targetNode, boolean reverse, List<TableReloadRequest> reloadRequests, ProcessInfo processInfo,
            List<TriggerRouter> triggerRouters, Map<Integer, ExtractRequest> extractRequests,
            IReloadGenerator reloadGenerator) {
        if (engine.getClusterService().lock(ClusterConstants.SYNC_TRIGGERS)) {
            try {
                INodeService nodeService = engine.getNodeService();
                ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
                synchronized (triggerRouterService) {
                    boolean transactional = parameterService
                            .is(ParameterConstants.DATA_RELOAD_IS_BATCH_INSERT_TRANSACTIONAL);
                    long loadId = 0;
                    if (reloadRequests != null && reloadRequests.size() > 0) {
                        loadId = reloadRequests.get(0).getLoadId();
                    }
                    if (loadId != 0) {
                        // Cancel the load
                        TableReloadStatus status = new TableReloadStatus();
                        status.setSourceNodeId(engine.getNodeId());
                        status.setTargetNodeId(targetNode.getNodeId());
                        status.setLoadId((int) loadId);
                        status.setFullLoad(reloadRequests.get(0).isFullLoadRequest());
                        engine.getInitialLoadService().cancelLoad(status);
                        // Get original table reload request
                        TableReloadRequest tableReloadRequest = reloadRequests.get(0);
                        // Insert new table reload request
                        tableReloadRequest.setLoadId(0l);
                        tableReloadRequest.setProcessed(false);
                        tableReloadRequest.setCreateTime(null);
                        insertTableReloadRequest(tableReloadRequest);
                        // Start a new load
                        loadId = 0l;
                    }
                    List<TriggerHistory> activeHistories = null;
                    if (reloadGenerator == null) {
                        activeHistories = triggerRouterService.getActiveTriggerHistories();
                    } else {
                        activeHistories = reloadGenerator.getActiveTriggerHistories(targetNode);
                    }
                    boolean isFullLoad = reloadRequests == null
                            || (reloadRequests.size() == 1 && reloadRequests.get(0).isFullLoadRequest());
                    boolean isChannelLoad = false;
                    String channelId = null;
                    if (reloadRequests != null
                            && (reloadRequests.size() == 1 && reloadRequests.get(0).isChannelRequest())) {
                        isChannelLoad = true;
                        channelId = reloadRequests.get(0).getChannelId();
                    }
                    if (!reverse) {
                        log.info("Queueing up " + (isFullLoad ? "an initial" : "a") + " load to node " + targetNode.getNodeId()
                                + (isChannelLoad ? " for channel " + channelId : ""));
                    } else {
                        log.info("Queueing up a reverse " + (isFullLoad ? "initial " : "partial ") + "load to node " + targetNode.getNodeId());
                    }
                    /*
                     * Outgoing data events are pointless because we are reloading all data
                     */
                    if (isFullLoad && StringUtils.isBlank(reloadRequests.get(0).getReloadSelect())) {
                        engine.getOutgoingBatchService().markAllAsSentForNode(targetNode.getNodeId(),
                                false);
                    }
                    Node sourceNode = nodeService.findIdentity();
                    String nodeIdRecord = reverse ? nodeService.findIdentityNodeId()
                            : targetNode
                                    .getNodeId();
                    NodeSecurity nodeSecurity = nodeService.findNodeSecurity(nodeIdRecord);
                    ISqlTransaction transaction = null;
                    try {
                        transaction = platform.getSqlTemplate().startSqlTransaction();
                        if (loadId == 0) {
                            if (platform.supportsMultiThreadedTransactions()) {
                                loadId = engine.getSequenceService().nextVal(Constants.SEQUENCE_OUTGOING_BATCH_LOAD_ID);
                            } else {
                                loadId = engine.getSequenceService().nextVal(transaction, Constants.SEQUENCE_OUTGOING_BATCH_LOAD_ID);
                            }
                        }
                        processInfo.setCurrentLoadId(loadId);
                        String createBy = reverse ? nodeSecurity.getRevInitialLoadCreateBy() : nodeSecurity.getInitialLoadCreateBy();
                        if (reloadRequests != null && reloadRequests.size() > 0) {
                            createBy = reloadRequests.get(0).getLastUpdateBy();
                            createTableReloadStatus(platform.supportsMultiThreadedTransactions() ? null : transaction,
                                    loadId, isFullLoad, reloadRequests.get(0).getSourceNodeId(), reloadRequests.get(0).getTargetNodeId());
                            for (TableReloadRequest request : reloadRequests) {
                                updateTableReloadRequestsLoadId(
                                        platform.supportsMultiThreadedTransactions() ? null : transaction,
                                        loadId, request);
                            }
                            if (!isFullLoad && !reverse) {
                                nodeService.setPartialLoadStarted(transaction, nodeIdRecord, loadId, createBy);
                            }
                            // force early commit to get load ID on the reload requests and reload status
                            close(transaction);
                            transaction = platform.getSqlTemplate().startSqlTransaction();
                        }
                        List<TriggerHistory> triggerHistories = new ArrayList<TriggerHistory>();
                        if (isFullLoad || isChannelLoad) {
                            triggerHistories.addAll(activeHistories);
                            if (reloadRequests != null && reloadRequests.size() == 1) {
                                if (channelId != null) {
                                    List<TriggerHistory> channelTriggerHistories = new ArrayList<TriggerHistory>();
                                    for (TriggerHistory history : triggerHistories) {
                                        Trigger trigger = findTriggerFor(history, triggerRouters);
                                        if (trigger != null && (channelId.equals(trigger.getChannelId())
                                                || channelId.equals(trigger.getReloadChannelId()))) {
                                            channelTriggerHistories.add(history);
                                        }
                                    }
                                    triggerHistories = channelTriggerHistories;
                                }
                            }
                        } else {
                            for (TableReloadRequest reloadRequest : reloadRequests) {
                                triggerHistories.addAll(engine.getTriggerRouterService()
                                        .getActiveTriggerHistories(new Trigger(reloadRequest.getTriggerId(), null)));
                            }
                        }
                        boolean sortByFk = !(isFullLoad && parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false) &&
                                reloadRequests != null && reloadRequests.size() > 0 && reloadRequests.get(0).isCreateTable());
                        Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId = triggerRouterService
                                .fillTriggerRoutersByHistIdAndSortHist(sourceNode.getNodeGroupId(),
                                        targetNode.getNodeGroupId(), targetNode.getExternalId(), triggerHistories, triggerRouters, sortByFk);
                        if (isFullLoad) {
                            if (!reverse) {
                                nodeService.setInitialLoadEnabled(transaction, nodeIdRecord, false, true, loadId, createBy);
                            } else {
                                nodeService.setReverseInitialLoadEnabled(transaction, nodeIdRecord, false, true, loadId, createBy);
                            }
                            callReloadListeners(true, targetNode, transactional, transaction, loadId);
                            if (reloadRequests == null || reloadRequests.size() == 0) {
                                insertCreateSchemaScriptPriorToReload(targetNode, nodeIdRecord, loadId,
                                        createBy, transactional, transaction);
                            }
                        }
                        Map<String, TableReloadRequest> mapReloadRequests = convertReloadListToMap(reloadRequests, triggerRouters, isFullLoad, isChannelLoad);
                        String symNodeSecurityReloadChannel = null;
                        int totalTableCount = 0;
                        try {
                            for (List<TriggerRouter> triggerRouterList : triggerRoutersByHistoryId.values()) {
                                if (triggerRouterList.size() > 0) {
                                    TriggerRouter tr = triggerRouterList.get(0);
                                    symNodeSecurityReloadChannel = tr.getTrigger().getReloadChannelId();
                                }
                                totalTableCount += triggerRouterList.size();
                            }
                        } catch (Exception e) {
                        }
                        processInfo.setTotalDataCount(totalTableCount);
                        if (reloadRequests != null && reloadRequests.size() > 0) {
                            updateTableReloadStatusTableCount(
                                    platform.supportsMultiThreadedTransactions() ? null : transaction,
                                    loadId, sourceNode.getNodeId(), totalTableCount);
                        }
                        int setupBatchCount = 0;
                        int finalizeBatchCount = 0;
                        setupBatchCount += insertSqlEventsPriorToReload(targetNode, nodeIdRecord, loadId, createBy,
                                transactional, transaction, reverse,
                                triggerHistories, triggerRoutersByHistoryId,
                                mapReloadRequests, isFullLoad, symNodeSecurityReloadChannel);
                        int createTableBatchCount = insertCreateBatchesForReload(targetNode, loadId, createBy,
                                triggerHistories, triggerRoutersByHistoryId, transactional,
                                transaction, mapReloadRequests);
                        setupBatchCount += createTableBatchCount;
                        if (parameterService.is(ParameterConstants.INITIAL_LOAD_DEFER_CREATE_CONSTRAINTS, false)) {
                            finalizeBatchCount += createTableBatchCount;
                        }
                        setupBatchCount += insertDeleteBatchesForReload(targetNode, loadId, createBy,
                                triggerHistories, triggerRoutersByHistoryId, transactional,
                                transaction, mapReloadRequests);
                        setupBatchCount += insertSQLBatchesForReload(targetNode, loadId, createBy,
                                triggerHistories, triggerRoutersByHistoryId, transactional,
                                transaction, mapReloadRequests);
                        if (reloadRequests != null && reloadRequests.size() > 0) {
                            updateTableReloadStatusSetupCount(
                                    platform.supportsMultiThreadedTransactions() ? null : transaction, loadId,
                                    sourceNode.getNodeId(), setupBatchCount);
                        }
                        extractRequests = insertLoadBatchesForReload(targetNode, loadId, createBy, triggerHistories,
                                triggerRoutersByHistoryId, transactional, transaction, mapReloadRequests, processInfo, null, extractRequests, isFullLoad);
                        finalizeBatchCount += insertSqlEventsAfterReload(targetNode, nodeIdRecord, loadId, createBy,
                                transactional, transaction, reverse,
                                triggerHistories, triggerRoutersByHistoryId,
                                mapReloadRequests, isFullLoad, symNodeSecurityReloadChannel);
                        int fileSyncBatches = insertFileSyncBatchForReload(targetNode, loadId, createBy, transactional,
                                transaction, mapReloadRequests, isFullLoad, processInfo);
                        if (reloadRequests != null && reloadRequests.size() > 0) {
                            updateTableReloadStatusTableCount(platform.supportsMultiThreadedTransactions() ? null : transaction, loadId,
                                    sourceNode.getNodeId(), totalTableCount + fileSyncBatches);
                        }
                        if (isFullLoad) {
                            callReloadListeners(false, targetNode, transactional, transaction, loadId);
                        }
                        engine.getStatisticManager().incrementNodesLoaded(1);
                        if (reloadRequests != null && reloadRequests.size() > 0) {
                            updateTableReloadStatusFinalizeCount(platform.supportsMultiThreadedTransactions() ? null : transaction, loadId, sourceNode
                                    .getNodeId(), finalizeBatchCount);
                            int rowsAffected = transaction.prepareAndExecute(getSql("updateProcessedTableReloadRequest"), new Date(), loadId);
                            if (rowsAffected == 0) {
                                List<TableReloadRequest> requests = transaction.query(getSql("selectTableReloadRequestsByLoadId"),
                                        new TableReloadRequestMapper(), new Object[] { loadId }, new int[] { symmetricDialect.getSqlTypeForIds() });
                                if (requests != null && requests.size() > 0 && requests.get(0).isProcessed()) {
                                    throw new InterruptedException("Table reload request appears to be cancelled");
                                }
                                throw new SymmetricException(String.format("Failed to update a table_reload_request as processed for loadId '%s' ",
                                        loadId));
                            }
                            log.info("Table reload request(s) for load id " + loadId + " have been processed.");
                        }
                        update_outgoing_batch_and_extract_request_for_processing(transaction, sourceNode.getNodeId(), targetNode.getNodeId(), loadId);
                        checkInterrupted();
                        transaction.commit();
                    } catch (Error ex) {
                        if (transaction != null) {
                            transaction.rollback();
                        }
                        throw ex;
                    } catch (Exception ex) {
                        if (transaction != null) {
                            transaction.rollback();
                        }
                        if (ex instanceof InvalidSqlException) {
                            InvalidSqlException e = (InvalidSqlException) ex;
                            updateTableReloadRequestsError(loadId, sourceNode.getNodeId(), e.getErrorCode(), e.getSQLState(),
                                    ExceptionUtils.unwrapMessages(ex));
                            log.warn("Cancelling load " + loadId, e);
                            updateTableReloadRequestsCancelled(loadId, sourceNode.getNodeId());
                        } else if (ex instanceof RuntimeException) {
                            throw (RuntimeException) ex;
                        } else if (ex instanceof InterruptedException) {
                            log.info("Insert reload events was interrupted: {}", ex.getMessage() == null ? "" : ex.getMessage());
                        }
                    } finally {
                        close(transaction);
                    }
                    if (!reverse && isFullLoad) {
                        /*
                         * Remove all incoming events for the node that we are starting a reload for
                         */
                        engine.getPurgeService().purgeAllIncomingEventsForNode(
                                targetNode.getNodeId());
                    }
                    engine.getDataExtractorService().releaseMissedExtractRequests();
                }
            } finally {
                engine.getClusterService().unlock(ClusterConstants.SYNC_TRIGGERS);
            }
        } else {
            log.info("Not attempting to insert reload events because sync trigger is currently running");
        }
        return extractRequests;
    }

    private void update_outgoing_batch_and_extract_request_for_processing(
            ISqlTransaction transaction, String sourceNodeId, String targetNodeId, long loadId) {
        TableReloadStatus status = getTableReloadStatusByLoadIdAndSourceNodeId(loadId, sourceNodeId);
        if (status != null) {
            long startDataBatchId = status.getStartDataBatchId();
            long endDataBatchId = status.getEndDataBatchId();
            String reloadUpdateStatus = Status.NE.name();
            if (parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB)) {
                reloadUpdateStatus = Status.RQ.name();
            }
            update_setup_batches(transaction, targetNodeId, loadId, Status.LS.name(), Status.NE.name(), startDataBatchId);
            update_load_batches(transaction, targetNodeId, loadId, Status.LS.name(), reloadUpdateStatus,
                    startDataBatchId, endDataBatchId);
            update_finalize_batches(transaction, targetNodeId, loadId, Status.LS.name(), Status.NE.name(),
                    endDataBatchId);
            update_extract_requests(transaction, loadId, engine.getNodeId(), ExtractStatus.LS.name(),
                    ExtractStatus.NE.name());
        }
    }

    private void update_setup_batches(ISqlTransaction transaction, String targetNodeId, long loadId,
            String fromStatus, String toStatus, long maxBatchId) {
        engine.getOutgoingBatchService().updateOutgoingSetupBatchStatusByStatus(transaction, targetNodeId, loadId,
                maxBatchId, fromStatus, toStatus);
    }

    private void update_load_batches(ISqlTransaction transaction, String targetNodeId, long loadId,
            String fromStatus, String toStatus, long startDataBatchId, long endDataBatchId) {
        engine.getOutgoingBatchService().updateOutgoingLoadBatchStatusByStatus(transaction, targetNodeId, loadId,
                startDataBatchId, endDataBatchId, fromStatus, toStatus);
    }

    private void update_finalize_batches(ISqlTransaction transaction, String targetNodeId, long loadId,
            String fromStatus, String toStatus, long minBatchId) {
        engine.getOutgoingBatchService().updateOutgoingFinalizeBatchStatusByStatus(transaction, targetNodeId,
                loadId, minBatchId, fromStatus, toStatus);
    }

    private void update_extract_requests(ISqlTransaction transaction, long loadId, String sourceNodeId,
            String fromStatus, String toStatus) {
        engine.getDataExtractorService().updateExtractRequestStatuses(transaction, loadId, sourceNodeId,
                fromStatus, toStatus);
    }

    private long getBatchCountFor(Map<Integer, ExtractRequest> extractRequests) {
        long batchCount = 0;
        for (ExtractRequest request : extractRequests.values()) {
            batchCount += ((request.getEndBatchId() - request.getStartBatchId()) + 1);
        }
        return batchCount;
    }

    private Trigger findTriggerFor(TriggerHistory history, List<TriggerRouter> triggerRouters) {
        for (TriggerRouter triggerRouter : triggerRouters) {
            if (triggerRouter.getTrigger().getTriggerId().equals(history.getTriggerId())) {
                return triggerRouter.getTrigger();
            }
        }
        return null;
    }

    protected Map<String, TableReloadRequest> convertReloadListToMap(List<TableReloadRequest> reloadRequests, List<TriggerRouter> triggerRouters,
            boolean isFullLoad, boolean isChannelLoad) {
        if (reloadRequests == null) {
            return null;
        }
        Map<String, TableReloadRequest> reloadMap = new CaseInsensitiveMap<String, TableReloadRequest>();
        for (TableReloadRequest reloadRequest : reloadRequests) {
            if (!isFullLoad && !isChannelLoad) {
                validate(reloadRequest, triggerRouters);
            }
            reloadMap.put(reloadRequest.getIdentifier(), reloadRequest);
        }
        return reloadMap;
    }

    protected void validate(TableReloadRequest reloadRequest, List<TriggerRouter> triggerRouters) {
        boolean validMatch = false;
        for (TriggerRouter triggerRouter : triggerRouters) {
            if (Objects.equals(triggerRouter.getTriggerId(), reloadRequest.getTriggerId())
                    && Objects.equals(triggerRouter.getRouterId(), reloadRequest.getRouterId())) {
                validMatch = true;
                break;
            }
        }
        if (!validMatch) {
            throw new InvalidSqlException("Invalid SQL", new SqlException("Table reload request submitted which does not have a valid trigger/router "
                    + "combination in sym_trigger_router. Request trigger id: '" + reloadRequest.getTriggerId() + "' router id: '"
                    + reloadRequest.getRouterId() + "' create time: " + reloadRequest.getCreateTime()));
        }
    }

    private void callReloadListeners(boolean before, Node targetNode, boolean transactional,
            ISqlTransaction transaction, long loadId) {
        for (IReloadListener listener : extensionService.getExtensionPointList(IReloadListener.class)) {
            if (before) {
                listener.beforeReload(transaction, targetNode, loadId);
            } else {
                listener.afterReload(transaction, targetNode, loadId);
            }
            if (!transactional) {
                transaction.commit();
            }
        }
    }

    private void insertCreateSchemaScriptPriorToReload(Node targetNode, String nodeIdRecord, long loadId,
            String createBy, boolean transactional, ISqlTransaction transaction) {
        String dumpCommand = parameterService.getString(ParameterConstants.INITIAL_LOAD_SCHEMA_DUMP_COMMAND);
        String loadCommand = parameterService.getString(ParameterConstants.INITIAL_LOAD_SCHEMA_LOAD_COMMAND);
        if (isNotBlank(dumpCommand) && isNotBlank(loadCommand)) {
            try {
                log.info("Dumping schema using the following dump command: " + dumpCommand);
                ProcessBuilder pb = new ProcessBuilder(FormatUtils.splitOnSpacePreserveQuotedStrings(dumpCommand));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                java.io.InputStream is = process.getInputStream();
                java.io.StringWriter ow = new java.io.StringWriter();
                IOUtils.copy(is, ow, Charset.defaultCharset());
                String output = ow.toString();
                output = StringEscapeUtils.escapeEcmaScript(output);
                String script = IOUtils.toString(getClass().getResourceAsStream("/load-schema-at-target.bsh"), Charset.defaultCharset());
                script = script.replace("${data}", output);
                script = script.replace("${commands}", formatCommandForScript(loadCommand));
                if (process.waitFor() != 0) {
                    throw new IoException(output.toString());
                }
                log.info("Inserting script to load dump at client");
                engine.getDataService().insertScriptEvent(transaction, Constants.CHANNEL_RELOAD, targetNode,
                        script, true, loadId, "reload listener");
            } catch (Exception e) {
                throw new IoException(e);
            }
        }
    }

    private String formatCommandForScript(String command) {
        String[] tokens = FormatUtils.splitOnSpacePreserveQuotedStrings(command);
        StringBuilder builder = new StringBuilder();
        for (String string : tokens) {
            builder.append("\"" + StringEscapeUtils.escapeJava(string) + "\",");
        }
        return builder.substring(0, builder.length() - 1);
    }

    private int insertSqlEventsPriorToReload(Node targetNode, String nodeIdRecord, long loadId,
            String createBy, boolean transactional, ISqlTransaction transaction, boolean reverse,
            List<TriggerHistory> triggerHistories,
            Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId,
            Map<String, TableReloadRequest> reloadRequests, boolean isFullLoad, String channelId) {
        int batchCount = 0;
        if (isFullLoad && !Constants.DEPLOYMENT_TYPE_REST.equals(targetNode.getDeploymentType())) {
            /*
             * Insert node security so the client doing the initial load knows that an initial load is currently happening
             */
            insertNodeSecurityUpdate(transaction, nodeIdRecord, targetNode.getNodeId(), true,
                    loadId, createBy, channelId);
            batchCount++;
            TableReloadRequest t = reloadRequests.get(ParameterConstants.ALL + ParameterConstants.ALL);
            if (t != null && StringUtils.isBlank(t.getReloadSelect())) {
                long curBatchId = engine.getSequenceService().currVal(transaction, Constants.SEQUENCE_OUTGOING_BATCH);
                /*
                 * Mark incoming batches as OK at the target node because we marked outgoing batches as OK at the source
                 */
                insertSqlEvent(
                        transaction,
                        targetNode,
                        String.format(
                                "update %s_incoming_batch set status='OK', error_flag=0 where node_id='%s' and status != 'OK' "
                                        + "and batch_id < " + curBatchId,
                                tablePrefix, engine.getNodeService().findIdentityNodeId()), true,
                        loadId, createBy, Status.LS);
                batchCount++;
            }
        }
        if (isFullLoad) {
            String beforeSql = parameterService.getString(reverse ? ParameterConstants.INITIAL_LOAD_REVERSE_BEFORE_SQL
                    : ParameterConstants.INITIAL_LOAD_BEFORE_SQL);
            if (isNotBlank(beforeSql)) {
                insertSqlEvent(
                        transaction,
                        targetNode,
                        beforeSql, true,
                        loadId, createBy, Status.LS);
                batchCount++;
            }
        }
        return batchCount;
    }

    private int insertSqlEventsAfterReload(Node targetNode, String nodeIdRecord, long loadId,
            String createBy, boolean transactional, ISqlTransaction transaction, boolean reverse,
            List<TriggerHistory> triggerHistories,
            Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId,
            Map<String, TableReloadRequest> reloadRequests, boolean isFullLoad, String channelId) {
        int totalBatchCount = 0;
        if (isFullLoad) {
            String afterSql = parameterService.getString(reverse ? ParameterConstants.INITIAL_LOAD_REVERSE_AFTER_SQL
                    : ParameterConstants.INITIAL_LOAD_AFTER_SQL);
            if (isNotBlank(afterSql)) {
                insertSqlEvent(transaction, targetNode, afterSql, true, loadId, createBy);
                totalBatchCount++;
            }
        }
        return totalBatchCount;
    }

    private int insertCreateBatchesForReload(Node targetNode, long loadId, String createBy,
            List<TriggerHistory> triggerHistories,
            Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId, boolean transactional,
            ISqlTransaction transaction, Map<String, TableReloadRequest> reloadRequests) throws InterruptedException {
        int createEventsSent = 0;
        if (reloadRequests != null && reloadRequests.size() > 0) {
            for (TriggerHistory triggerHistory : triggerHistories) {
                List<TriggerRouter> triggerRouters = triggerRoutersByHistoryId.get(triggerHistory
                        .getTriggerHistoryId());
                TableReloadRequest currentRequest = reloadRequests.get(ParameterConstants.ALL + ParameterConstants.ALL);
                boolean fullLoad = currentRequest == null ? false : true;
                for (TriggerRouter triggerRouter : triggerRouters) {
                    if (!fullLoad) {
                        currentRequest = reloadRequests.get(triggerRouter.getTriggerId() + triggerRouter.getRouterId());
                    }
                    // Check the create flag on the specific table reload request
                    if (triggerRouter.getInitialLoadOrder() > -1
                            && currentRequest != null
                            && currentRequest.isCreateTable()
                            && engine.getGroupletService().isTargetEnabled(triggerRouter, targetNode)
                            && !triggerHistory.getSourceTableNameLowerCase().startsWith(symmetricDialect.getTablePrefix().toLowerCase() + "_")) {
                        insertCreateEvent(transaction, targetNode, triggerHistory, triggerRouter.getTrigger().getReloadChannelId(), true,
                                loadId, createBy, false, false, false, Status.LS);
                        createEventsSent++;
                        if (!transactional) {
                            transaction.commit();
                        }
                    }
                    checkInterrupted();
                }
            }
            if (createEventsSent > 0) {
                log.info("Before sending load {} to target node {} create table events were sent for {} tables", new Object[] {
                        loadId, targetNode, createEventsSent });
            }
        } else {
            if (parameterService.is(ParameterConstants.INITIAL_LOAD_CREATE_SCHEMA_BEFORE_RELOAD)) {
                for (TriggerHistory triggerHistory : triggerHistories) {
                    List<TriggerRouter> triggerRouters = triggerRoutersByHistoryId.get(triggerHistory
                            .getTriggerHistoryId());
                    for (TriggerRouter triggerRouter : triggerRouters) {
                        if (triggerRouter.getInitialLoadOrder() >= 0
                                && engine.getGroupletService().isTargetEnabled(triggerRouter,
                                        targetNode)) {
                            insertCreateEvent(transaction, targetNode, triggerHistory, triggerRouter.getRouter().getRouterId(), true,
                                    loadId, createBy, false, false, false, Status.LS);
                            createEventsSent++;
                            if (!transactional) {
                                transaction.commit();
                            }
                            checkInterrupted();
                        }
                    }
                }
            }
        }
        return createEventsSent;
    }

    private int insertDeleteBatchesForReload(Node targetNode, long loadId, String createBy,
            List<TriggerHistory> triggerHistories,
            Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId, boolean transactional,
            ISqlTransaction transaction, Map<String, TableReloadRequest> reloadRequests) throws InterruptedException {
        int deleteEventsSent = 0;
        if (reloadRequests != null && reloadRequests.size() > 0) {
            for (ListIterator<TriggerHistory> triggerHistoryIterator = triggerHistories
                    .listIterator(triggerHistories.size()); triggerHistoryIterator.hasPrevious();) {
                TriggerHistory triggerHistory = triggerHistoryIterator.previous();
                List<TriggerRouter> triggerRouters = triggerRoutersByHistoryId.get(triggerHistory
                        .getTriggerHistoryId());
                TableReloadRequest currentRequest = reloadRequests.get(ParameterConstants.ALL + ParameterConstants.ALL);
                boolean fullLoad = currentRequest == null ? false : true;
                for (ListIterator<TriggerRouter> iterator = triggerRouters
                        .listIterator(triggerRouters.size()); iterator.hasPrevious();) {
                    TriggerRouter triggerRouter = iterator.previous();
                    if (!fullLoad) {
                        currentRequest = reloadRequests.get(triggerRouter.getTriggerId() + triggerRouter.getRouterId());
                    }
                    // Check the delete flag on the specific table reload request
                    if (triggerRouter.getInitialLoadOrder() > -1
                            && (currentRequest == null || currentRequest.isDeleteFirst())
                            && engine.getGroupletService().isTargetEnabled(triggerRouter,
                                    targetNode)) {
                        insertPurgeEvent(transaction, targetNode, triggerRouter, triggerHistory,
                                true, currentRequest.getBeforeCustomSql(), loadId, createBy,
                                Status.LS);
                        deleteEventsSent++;
                        if (!transactional) {
                            transaction.commit();
                        }
                    }
                    checkInterrupted();
                }
            }
            if (deleteEventsSent > 0) {
                log.info("Before sending load {} to target node {} delete data events were sent for {} tables", new Object[] {
                        loadId, targetNode, deleteEventsSent });
            }
        } else {
            if (parameterService.is(ParameterConstants.INITIAL_LOAD_DELETE_BEFORE_RELOAD)) {
                for (ListIterator<TriggerHistory> triggerHistoryIterator = triggerHistories
                        .listIterator(triggerHistories.size()); triggerHistoryIterator.hasPrevious();) {
                    TriggerHistory triggerHistory = triggerHistoryIterator.previous();
                    List<TriggerRouter> triggerRouters = triggerRoutersByHistoryId.get(triggerHistory
                            .getTriggerHistoryId());
                    for (ListIterator<TriggerRouter> iterator = triggerRouters
                            .listIterator(triggerRouters.size()); iterator.hasPrevious();) {
                        TriggerRouter triggerRouter = iterator.previous();
                        if (triggerRouter.getInitialLoadOrder() >= 0
                                && engine.getGroupletService().isTargetEnabled(triggerRouter,
                                        targetNode)
                                && (!StringUtils.isBlank(parameterService
                                        .getString(ParameterConstants.INITIAL_LOAD_DELETE_FIRST_SQL)) || !StringUtils
                                                .isEmpty(triggerRouter.getInitialLoadDeleteStmt()))) {
                            insertPurgeEvent(transaction, targetNode, triggerRouter, triggerHistory,
                                    true, null, loadId, createBy, Status.LS);
                            deleteEventsSent++;
                            if (!transactional) {
                                transaction.commit();
                            }
                        }
                        checkInterrupted();
                    }
                }
            }
        }
        return deleteEventsSent;
    }

    private int insertSQLBatchesForReload(Node targetNode, long loadId, String createBy,
            List<TriggerHistory> triggerHistories,
            Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId, boolean transactional,
            ISqlTransaction transaction, Map<String, TableReloadRequest> reloadRequests) throws InterruptedException {
        int sqlEventsSent = 0;
        if (reloadRequests != null && reloadRequests.size() > 0) {
            List<TriggerHistory> copyTriggerHistories = new ArrayList<TriggerHistory>(triggerHistories);
            Collections.reverse(copyTriggerHistories);
            for (TriggerHistory triggerHistory : copyTriggerHistories) {
                List<TriggerRouter> triggerRouters = triggerRoutersByHistoryId.get(triggerHistory
                        .getTriggerHistoryId());
                TableReloadRequest currentRequest = reloadRequests.get(ParameterConstants.ALL + ParameterConstants.ALL);
                boolean fullLoad = currentRequest == null ? false : true;
                for (TriggerRouter triggerRouter : triggerRouters) {
                    if (!fullLoad) {
                        currentRequest = reloadRequests.get(triggerRouter.getTriggerId() + triggerRouter.getRouterId());
                    }
                    // Check the before custom sql is present on the specific table reload request
                    if (triggerRouter.getInitialLoadOrder() > -1
                            && currentRequest != null
                            && currentRequest.getBeforeCustomSql() != null
                            && currentRequest.getBeforeCustomSql().length() > 0
                            && engine.getGroupletService().isTargetEnabled(triggerRouter,
                                    targetNode)) {
                        List<String> sqlStatements = resolveTargetTables(currentRequest.getBeforeCustomSql(),
                                triggerRouter, triggerHistory, targetNode);
                        for (String sql : sqlStatements) {
                            insertSqlEvent(transaction, triggerHistory, triggerRouter.getTrigger().getChannelId(),
                                    targetNode, sql,
                                    true, loadId, createBy, Status.LS);
                            sqlEventsSent++;
                        }
                        if (!transactional) {
                            transaction.commit();
                        }
                    }
                    checkInterrupted();
                }
            }
            if (sqlEventsSent > 0) {
                log.info("Before sending load {} to target node {} SQL data events were sent for {} tables", new Object[] {
                        loadId, targetNode, sqlEventsSent });
            }
        }
        return sqlEventsSent;
    }

    private Map<Integer, ExtractRequest> insertLoadBatchesForReload(Node targetNode, long loadId, String createBy,
            List<TriggerHistory> triggerHistories,
            Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId, boolean transactional,
            ISqlTransaction transaction, Map<String, TableReloadRequest> reloadRequests, ProcessInfo processInfo,
            String selectSqlOverride, Map<Integer, ExtractRequest> extractRequests, boolean isFullLoad) throws InterruptedException {
        Map<String, Channel> channels = engine.getConfigurationService().getChannels(false);
        Map<Integer, ExtractRequest> requests = new HashMap<Integer, ExtractRequest>();
        if (extractRequests != null) {
            requests.putAll(extractRequests);
        }
        boolean canEngineBulkOperation = (engine.getParameterService().is(ParameterConstants.MSSQL_BULK_EXTRACT_USE_BCP) &&
                engine.getSymmetricDialect().getTargetPlatform().getName().startsWith(DatabaseNamesConstants.MSSQL)) ||
                (engine.getParameterService().is(ParameterConstants.ASE_BULK_EXTRACT_USE_BCP) &&
                        engine.getSymmetricDialect().getTargetPlatform().getName().startsWith(DatabaseNamesConstants.ASE));
        boolean canBulkOperation = canEngineBulkOperation && isLocalNodeUsingBulkLoad(targetNode.getNodeId());
        boolean canParentBulkOperation = true;
        if (extractRequests != null && extractRequests.size() > 0) {
            canParentBulkOperation = canEngineBulkOperation && isLocalNodeUsingBulkLoad(extractRequests.values().iterator().next().getNodeId());
        }
        boolean shouldAbandonParent = canParentBulkOperation && !canBulkOperation;
        String sourceNodeId = engine.getNodeService().findIdentity().getNodeId();
        String tablePrefix = engine.getParameterService().getTablePrefix().toLowerCase();
        long firstBatchId = 0;
        long totalDataCount = 0;
        for (TriggerHistory triggerHist : triggerHistories) {
            if (!triggerHist.getTriggerId().startsWith(tablePrefix)) {
                totalDataCount = totalDataCount + 1;
            }
        }
        processInfo.setTotalDataCount(totalDataCount);
        for (TriggerHistory triggerHistory : triggerHistories) {
            if (!triggerHistory.getSourceTableNameLowerCase().startsWith(tablePrefix)) {
                processInfo.incrementCurrentDataCount();
            }
            List<TriggerRouter> triggerRouters = triggerRoutersByHistoryId.get(triggerHistory
                    .getTriggerHistoryId());
            checkInterrupted();
            for (TriggerRouter triggerRouter : triggerRouters) {
                if (triggerRouter.getInitialLoadOrder() >= 0
                        && engine.getGroupletService().isTargetEnabled(triggerRouter, targetNode)) {
                    String selectSql = selectSqlOverride;
                    if (StringUtils.isEmpty(selectSql)) {
                        if (reloadRequests != null) {
                            if (isFullLoad && reloadRequests.size() == 1) {
                                TableReloadRequest reloadRequest = reloadRequests.values().stream().findFirst().get();
                                selectSql = reloadRequest != null ? reloadRequest.getReloadSelect() : null;
                            } else {
                                TableReloadRequest reloadRequest = reloadRequests.get(triggerRouter.getTriggerId() + triggerRouter.getRouterId());
                                selectSql = reloadRequest != null ? reloadRequest.getReloadSelect() : null;
                            }
                        }
                        if (StringUtils.isBlank(selectSql)) {
                            selectSql = StringUtils.isBlank(triggerRouter.getInitialLoadSelect())
                                    ? Constants.ALWAYS_TRUE_CONDITION
                                    : triggerRouter.getInitialLoadSelect();
                        }
                    }
                    Table table = getTargetPlatform(triggerHistory.getSourceTableName()).getTableFromCache(
                            triggerHistory.getSourceCatalogName(), triggerHistory.getSourceSchemaName(),
                            triggerHistory.getSourceTableName(), false);
                    if (table != null) {
                        processInfo.setCurrentTableName(table.getName());
                        Trigger trigger = triggerRouter.getTrigger();
                        String reloadChannel = getReloadChannelIdForTrigger(trigger, channels);
                        Channel channel = channels.get(reloadChannel);
                        long rowCount = -1;
                        long parentRequestId = 0;
                        ExtractRequest parentRequest = requests.get(triggerHistory.getTriggerHistoryId());
                        if (parentRequest != null && !shouldAbandonParent) {
                            Router router = engine.getTriggerRouterService().getRouterById(triggerRouter.getRouterId(), false);
                            if (router != null && router.getRouterType().equals("default")) {
                                parentRequestId = parentRequest.getRequestId();
                                rowCount = parentRequest.getRows();
                            }
                        }
                        if (rowCount == -1) {
                            rowCount = getDataCountForReload(table, targetNode, selectSql, loadId);
                        }
                        long transformMultiplier = getTransformMultiplier(table, triggerRouter);
                        long startBatchId = 0;
                        long numberOfBatches = 1;
                        if (parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB)) {
                            if (rowCount > 0) {
                                processInfo.setCurrentRowCount(processInfo.getCurrentRowCount() + rowCount);
                                numberOfBatches = (long) Math.ceil((rowCount * transformMultiplier) / (channel.getMaxBatchSize() * 1f));
                                processInfo.setCurrentBatchCount(processInfo.getCurrentBatchCount() + numberOfBatches);
                            }
                            long maxBatchSize = channel.getMaxBatchSize();
                            if (canBulkOperation && canParentBulkOperation && channel.getDataLoaderType().equals("bulk")) {
                                numberOfBatches = 1;
                                maxBatchSize = rowCount;
                            }
                            startBatchId = insertRequestedOutgoingBatches(transaction, targetNode, triggerRouter, triggerHistory, selectSql,
                                    loadId, createBy, reloadChannel, rowCount, maxBatchSize, numberOfBatches);
                        } else {
                            startBatchId = insertReloadEvent(transaction, targetNode, triggerRouter, triggerHistory,
                                    selectSql, true, loadId, createBy, Status.LS, null, -1);
                        }
                        long endBatchId = startBatchId + numberOfBatches - 1;
                        firstBatchId = firstBatchId == 0 ? startBatchId : firstBatchId;
                        if (table.getNameLowerCase().startsWith(symmetricDialect.getTablePrefix() + "_" + TableConstants.SYM_FILE_SNAPSHOT)) {
                            TableReloadStatus reloadStatus = getTableReloadStatusByLoadIdAndSourceNodeId(loadId, sourceNodeId);
                            firstBatchId = reloadStatus.getStartDataBatchId() > 0 ? reloadStatus.getStartDataBatchId() : firstBatchId;
                        }
                        updateTableReloadStatusDataCounts(platform.supportsMultiThreadedTransactions() ? null : transaction,
                                loadId, sourceNodeId, firstBatchId, endBatchId, numberOfBatches, rowCount);
                        ExtractRequest request = engine.getDataExtractorService().requestExtractRequest(transaction, targetNode.getNodeId(), channel.getQueue(),
                                triggerRouter, startBatchId, endBatchId, loadId, table.getName(), rowCount, parentRequestId);
                        if (parentRequest == null) {
                            requests.put(triggerHistory.getTriggerHistoryId(), request);
                        }
                    } else {
                        log.warn("The table defined by trigger_hist row {} no longer exists.  A load will not be queue'd up for the table", triggerHistory
                                .getTriggerHistoryId());
                    }
                    if (!transactional) {
                        transaction.commit();
                    }
                }
            }
        }
        // Needs to have a "data batch" to give point of reference for setup/finalize batches if no actual data batches
        if (firstBatchId == 0) {
            long startBatchId = 0;
            if (platform.supportsMultiThreadedTransactions()) {
                startBatchId = engine.getSequenceService().nextRange(Constants.SEQUENCE_OUTGOING_BATCH, 1);
            } else {
                startBatchId = engine.getSequenceService().nextRange(transaction, Constants.SEQUENCE_OUTGOING_BATCH, 1);
            }
            updateTableReloadStatusDataCounts(platform.supportsMultiThreadedTransactions() ? null : transaction,
                    loadId, sourceNodeId, startBatchId, startBatchId, 0, 0);
        }
        return requests;
    }

    protected long getDataCountForReload(Table table, Node targetNode, String selectSql, long loadId) throws SqlException {
        long rowCount = -1;
        if (parameterService.is(ParameterConstants.INITIAL_LOAD_USE_ESTIMATED_COUNTS) &&
                (selectSql == null || StringUtils.isBlank(selectSql) || selectSql.replace(" ", "").equals("1=1"))) {
            rowCount = getTargetPlatform().getEstimatedRowCount(table);
        }
        if (rowCount < 0) {
            DatabaseInfo dbInfo = getTargetPlatform().getDatabaseInfo();
            String quote = dbInfo.getDelimiterToken();
            String catalogSeparator = dbInfo.getCatalogSeparator();
            String schemaSeparator = dbInfo.getSchemaSeparator();
            if (selectSql != null && selectSql.trim().toUpperCase().startsWith("WHERE")) {
                selectSql = selectSql.trim().substring(5);
            }
            String sql = String.format("select count(*) from %s t where %s", table
                    .getQualifiedTableName(quote, catalogSeparator, schemaSeparator), selectSql);
            sql = FormatUtils.replace("groupId", targetNode.getNodeGroupId(), sql);
            sql = FormatUtils.replace("externalId", targetNode.getExternalId(), sql);
            sql = FormatUtils.replace("nodeId", targetNode.getNodeId(), sql);
            for (IReloadVariableFilter filter : extensionService.getExtensionPointList(IReloadVariableFilter.class)) {
                sql = filter.filterPurgeSql(sql, targetNode, table);
            }
            try {
                rowCount = getTargetPlatform().getSqlTemplateDirty().queryForLong(sql);
            } catch (SqlException ex) {
                throw new InvalidSqlException("Failed to execute row count SQL for load ID " + loadId + " with: " + sql, ex);
            }
        }
        return rowCount;
    }

    protected int getTransformMultiplier(Table table, TriggerRouter triggerRouter) {
        int transformMultiplier = 0;
        List<TransformTableNodeGroupLink> transforms = engine.getTransformService()
                .findTransformsFor(triggerRouter.getRouter().getNodeGroupLink(), TransformPoint.EXTRACT);
        if (transforms != null) {
            for (TransformService.TransformTableNodeGroupLink transform : transforms) {
                if (transform.getSourceTableName().equals(table.getName())) {
                    transformMultiplier++;
                }
            }
        }
        transformMultiplier = Math.max(1, transformMultiplier);
        return transformMultiplier;
    }

    protected boolean isLocalNodeUsingBulkLoad(String nodeId) {
        ISymmetricEngine targetEngine = AbstractSymmetricEngine.findEngineByNodeId(nodeId);
        boolean isTargetUsingBulkLoad = targetEngine != null &&
                ((targetEngine.getParameterService().is("mssql.bulk.load.use.bcp") &&
                        targetEngine.getSymmetricDialect().getTargetPlatform().getName().startsWith(DatabaseNamesConstants.MSSQL)) ||
                        (targetEngine.getSymmetricDialect().getTargetPlatform().getName().startsWith(DatabaseNamesConstants.ASE)));
        return isTargetUsingBulkLoad;
    }

    private int insertFileSyncBatchForReload(Node targetNode, long loadId, String createBy,
            boolean transactional, ISqlTransaction transaction, Map<String, TableReloadRequest> reloadRequests, boolean isFullLoad, ProcessInfo processInfo)
            throws InterruptedException {
        int totalBatchCount = 0;
        if (parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)
                && !Constants.DEPLOYMENT_TYPE_REST.equals(targetNode.getDeploymentType())) {
            ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
            IFileSyncService fileSyncService = engine.getFileSyncService();
            if (fileSyncService.getFileTriggerRoutersForCurrentNode(false).size() > 0) {
                String fileSnapshotTableName = TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_SNAPSHOT);
                TriggerHistory fileSyncSnapshotHistory = triggerRouterService.findTriggerHistory(null, null, fileSnapshotTableName);
                if (fileSyncSnapshotHistory == null) {
                    if (isFullLoad || reloadRequests == null || isReloadRequestForFileChannel(reloadRequests)) {
                        log.warn("Did not include file sync batches in load {} for target node {} because there is no valid trigger history for {}",
                                loadId, targetNode, fileSnapshotTableName);
                    } else {
                        log.warn(
                                "Could not determine whether to include file sync batches in load {} for target node {} because there is no valid trigger history for {}",
                                loadId, targetNode, fileSnapshotTableName);
                    }
                    return totalBatchCount;
                }
                String routerid = triggerRouterService.buildSymmetricTableRouterId(
                        fileSyncSnapshotHistory.getTriggerId(), parameterService.getNodeGroupId(),
                        targetNode.getNodeGroupId());
                TriggerRouter fileSyncSnapshotTriggerRouter = triggerRouterService
                        .getTriggerRouterForCurrentNode(fileSyncSnapshotHistory.getTriggerId(),
                                routerid, true);
                if (!isFullLoad && reloadRequests != null
                        && reloadRequests.get(fileSyncSnapshotTriggerRouter.getTriggerId() + fileSyncSnapshotTriggerRouter.getRouterId()) == null
                        && !isReloadRequestForFileChannel(reloadRequests)) {
                    return totalBatchCount;
                }
                List<TriggerHistory> triggerHistories = Collections.singletonList(fileSyncSnapshotHistory);
                List<TriggerRouter> triggerRouters = Collections.singletonList(fileSyncSnapshotTriggerRouter);
                Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId = new HashMap<Integer, List<TriggerRouter>>();
                triggerRoutersByHistoryId.put(fileSyncSnapshotHistory.getTriggerHistoryId(), triggerRouters);
                String overrideSql = fileSyncSnapshotTriggerRouter.getInitialLoadSelect();
                if (parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB)) {
                    final String FILTER_ENABLED_FILE_SYNC_TRIGGER_ROUTERS = String.format(
                            "1=(select initial_load_enabled from %s tr where t.trigger_id = tr.trigger_id AND t.router_id = tr.router_id)",
                            TableConstants.getTableName(tablePrefix, TableConstants.SYM_FILE_TRIGGER_ROUTER));
                    if (StringUtils.isNotBlank(overrideSql)) {
                        overrideSql = FILTER_ENABLED_FILE_SYNC_TRIGGER_ROUTERS + " AND " + overrideSql;
                    } else {
                        overrideSql = FILTER_ENABLED_FILE_SYNC_TRIGGER_ROUTERS;
                    }
                    totalBatchCount += getBatchCountFor(insertLoadBatchesForReload(targetNode, loadId, createBy, triggerHistories,
                            triggerRoutersByHistoryId, transactional, transaction, null, processInfo, overrideSql, null,
                            isFullLoad));
                } else {
                    List<Channel> channels = engine.getConfigurationService().getFileSyncChannels();
                    for (Channel channel : channels) {
                        if (channel.isReloadFlag()) {
                            String initialSql = "reload_channel_id='" + channel.getChannelId() + "'";
                            String sql = initialSql;
                            if (isNotBlank(overrideSql)) {
                                sql = sql + " AND " + overrideSql;
                            }
                            insertReloadEvent(transaction, targetNode, fileSyncSnapshotTriggerRouter,
                                    fileSyncSnapshotHistory,
                                    sql, true, loadId,
                                    createBy, Status.NE, channel.getChannelId(), -1);
                            totalBatchCount++;
                            if (!transactional) {
                                transaction.commit();
                            }
                        }
                    }
                }
            }
        }
        return totalBatchCount;
    }

    private boolean isReloadRequestForFileChannel(Map<String, TableReloadRequest> reloadRequests) {
        for (TableReloadRequest reloadRequest : reloadRequests.values()) {
            if (reloadRequest.getChannelId() != null && engine.getConfigurationService().getChannel(reloadRequest.getChannelId()).isFileSyncFlag()) {
                return true;
            }
        }
        return false;
    }

    private TriggerHistory lookupTriggerHistory(Trigger trigger) {
        TriggerHistory history = engine.getTriggerRouterService()
                .getNewestTriggerHistoryForTrigger(trigger.getTriggerId(),
                        trigger.getSourceCatalogName(), trigger.getSourceSchemaName(),
                        trigger.getSourceTableName());
        if (history == null) {
            engine.getTriggerRouterService().syncTriggers();
            history = engine.getTriggerRouterService().getNewestTriggerHistoryForTrigger(
                    trigger.getTriggerId(), null, null, null);
        }
        if (history == null) {
            throw new RuntimeException("Cannot find history for trigger " + trigger.getTriggerId()
                    + ", " + trigger.getSourceTableName());
        }
        return history;
    }

    protected void insertPurgeEvent(ISqlTransaction transaction, Node targetNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory, boolean isLoad,
            String overrideDeleteStatement, long loadId, String createBy) {
        insertPurgeEvent(transaction, targetNode, triggerRouter, triggerHistory, isLoad,
                overrideDeleteStatement, loadId, createBy, Status.NE);
    }

    protected void insertPurgeEvent(ISqlTransaction transaction, Node targetNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory, boolean isLoad,
            String overrideDeleteStatement, long loadId, String createBy, Status outgoingBatchStatus) {
        Node sourceNode = engine.getNodeService().findIdentity();
        List<TransformTableNodeGroupLink> transforms = this.engine.getTransformService().findTransformsFor(
                sourceNode.getNodeGroupId(), targetNode.getNodeGroupId(), triggerRouter.getTargetTable(triggerHistory));
        if (StringUtils.isNotBlank(overrideDeleteStatement)) {
            List<String> sqlStatements = resolveTargetTables(overrideDeleteStatement, triggerRouter, triggerHistory, targetNode);
            for (String sql : sqlStatements) {
                createPurgeEvent(transaction, sql, targetNode, sourceNode,
                        triggerRouter, triggerHistory, isLoad, loadId, createBy, outgoingBatchStatus);
            }
        } else if (transforms != null && transforms.size() > 0) {
            List<String> sqlStatements = symmetricDialect.createPurgeSqlForMultipleTables(targetNode, triggerRouter,
                    triggerHistory, transforms, null);
            for (String sql : sqlStatements) {
                createPurgeEvent(transaction,
                        sql,
                        targetNode, sourceNode,
                        triggerRouter, triggerHistory, isLoad, loadId, createBy, outgoingBatchStatus);
            }
        } else {
            createPurgeEvent(transaction,
                    symmetricDialect.createPurgeSqlFor(targetNode, triggerRouter, triggerHistory, transforms),
                    targetNode, sourceNode,
                    triggerRouter, triggerHistory, isLoad, loadId, createBy, outgoingBatchStatus);
        }
    }

    public List<String> resolveTargetTables(String sql, TriggerRouter triggerRouter, TriggerHistory triggerHistory, Node targetNode) {
        if (sql == null) {
            return null;
        }
        List<String> sqlStatements = new ArrayList<String>();
        if (sql != null && sql.contains("%s")) {
            Set<String> tableNames = new HashSet<String>();
            Node sourceNode = engine.getNodeService().findIdentity();
            String sourceTableName = triggerRouter.qualifiedTargetTableName(triggerHistory);
            List<TransformTableNodeGroupLink> transforms = this.engine.getTransformService().findTransformsFor(
                    sourceNode.getNodeGroupId(), targetNode.getNodeGroupId(), triggerRouter.getTargetTable(triggerHistory));
            if (transforms != null) {
                for (TransformTableNodeGroupLink transform : transforms) {
                    tableNames.add(transform.getFullyQualifiedTargetTableName());
                }
            } else {
                tableNames.add(sourceTableName);
            }
            for (String tableName : tableNames) {
                if (parameterService.is(ParameterConstants.DB_DELIMITED_IDENTIFIER_MODE)) {
                    String delimiter = engine.getTargetDialect().getTargetPlatform().getDatabaseInfo().getDelimiterToken();
                    tableName = tableName.replaceAll("\\.", delimiter + "." + delimiter);
                    tableName = delimiter + tableName + delimiter;
                }
                sqlStatements.add(String.format(sql, tableName));
            }
        } else {
            sqlStatements.add(sql);
        }
        return sqlStatements;
    }

    protected void createPurgeEvent(ISqlTransaction transaction, String sql, Node targetNode, Node sourceNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory, boolean isLoad,
            long loadId, String createBy) {
        createPurgeEvent(transaction, sql, targetNode, sourceNode, triggerRouter, triggerHistory,
                isLoad, loadId, createBy, Status.NE);
    }

    protected void createPurgeEvent(ISqlTransaction transaction, String sql, Node targetNode, Node sourceNode,
            TriggerRouter triggerRouter, TriggerHistory triggerHistory, boolean isLoad,
            long loadId, String createBy, Status outgoingBatchStatus) {
        sql = FormatUtils.replace("groupId", targetNode.getNodeGroupId(), sql);
        sql = FormatUtils.replace("externalId", targetNode.getExternalId(), sql);
        sql = FormatUtils.replace("nodeId", targetNode.getNodeId(), sql);
        sql = FormatUtils.replace("targetGroupId", targetNode.getNodeGroupId(), sql);
        sql = FormatUtils.replace("targetExternalId", targetNode.getExternalId(), sql);
        sql = FormatUtils.replace("targetNodeId", targetNode.getNodeId(), sql);
        sql = FormatUtils.replace("sourceGroupId", sourceNode.getNodeGroupId(), sql);
        sql = FormatUtils.replace("sourceExternalId", sourceNode.getExternalId(), sql);
        sql = FormatUtils.replace("sourceNodeId", sourceNode.getNodeId(), sql);
        Table table = new Table(triggerHistory.getSourceCatalogName(), triggerHistory.getSourceSchemaName(),
                triggerHistory.getSourceTableName(), triggerHistory.getParsedColumnNames(), triggerHistory.getParsedPkColumnNames());
        for (IReloadVariableFilter filter : extensionService.getExtensionPointList(IReloadVariableFilter.class)) {
            sql = filter.filterPurgeSql(sql, targetNode, table);
        }
        String channelId = getReloadChannelIdForTrigger(triggerRouter.getTrigger(), engine
                .getConfigurationService().getChannels(false));
        Data data = new Data(triggerHistory.getSourceTableName(), DataEventType.SQL,
                CsvUtils.escapeCsvData(sql), null, triggerHistory, channelId, null, null);
        data.setNodeList(targetNode.getNodeId());
        if (isLoad) {
            insertDataAndDataEventAndOutgoingBatch(transaction, data, targetNode.getNodeId(),
                    isLoad, loadId, createBy, outgoingBatchStatus, null, -1);
        } else {
            insertData(transaction, data);
        }
    }

    @Override
    public void insertSqlEvent(Node targetNode, String sql, boolean isLoad, long loadId,
            String createBy) {
        TriggerHistory history = engine.getTriggerRouterService()
                .findTriggerHistoryForGenericSync();
        Trigger trigger = engine.getTriggerRouterService().getTriggerById(history.getTriggerId(),
                false);
        String reloadChannelId = getReloadChannelIdForTrigger(trigger, engine
                .getConfigurationService().getChannels(false));
        Data data = new Data(history.getSourceTableName(), DataEventType.SQL,
                CsvUtils.escapeCsvData(sql), null, history, isLoad ? reloadChannelId
                        : Constants.CHANNEL_CONFIG, null, null);
        data.setNodeList(targetNode.getNodeId());
        if (isLoad) {
            insertDataAndDataEventAndOutgoingBatch(data, targetNode.getNodeId(),
                    isLoad, loadId, createBy);
        } else {
            insertData(data);
        }
    }

    @Override
    public void insertSqlEvent(ISqlTransaction transaction, Node targetNode, String sql,
            boolean isLoad, long loadId, String createBy) {
        insertSqlEvent(transaction, targetNode, sql, isLoad, loadId, createBy, Status.NE);
    }

    public void insertSqlEvent(ISqlTransaction transaction, Node targetNode, String sql,
            boolean isLoad, long loadId, String createBy, Status outgoingBatchStatus) {
        TriggerHistory history = engine.getTriggerRouterService()
                .findTriggerHistoryForGenericSync();
        insertSqlEvent(transaction, history, Constants.CHANNEL_CONFIG, targetNode, sql, isLoad,
                loadId, createBy, outgoingBatchStatus);
    }

    @Override
    public void insertSqlEvent(ISqlTransaction transaction, TriggerHistory history,
            String channelId, Node targetNode, String sql, boolean isLoad, long loadId,
            String createBy) {
        insertSqlEvent(transaction, history, channelId, targetNode, sql, isLoad, loadId,
                createBy, Status.NE);
    }

    public void insertSqlEvent(ISqlTransaction transaction, TriggerHistory history,
            String channelId, Node targetNode, String sql, boolean isLoad, long loadId,
            String createBy, Status outgoingBatchStatus) {
        Trigger trigger = engine.getTriggerRouterService().getTriggerById(history.getTriggerId(),
                false);
        String reloadChannelId = getReloadChannelIdForTrigger(trigger, engine
                .getConfigurationService().getChannels(false));
        Data data = new Data(history.getSourceTableName(), DataEventType.SQL,
                CsvUtils.escapeCsvData(sql), null, history, isLoad ? reloadChannelId : channelId,
                null, null);
        data.setNodeList(targetNode.getNodeId());
        if (isLoad) {
            insertDataAndDataEventAndOutgoingBatch(transaction, data, targetNode.getNodeId(),
                    isLoad, loadId, createBy, outgoingBatchStatus, null, -1);
        } else {
            insertData(transaction, data);
        }
    }

    @Override
    public void insertScriptEvent(String channelId, Node targetNode, String script, boolean isLoad,
            long loadId, String createBy) {
        ISqlTransaction transaction = null;
        try {
            transaction = platform.getSqlTemplate().startSqlTransaction();
            insertScriptEvent(transaction, channelId, targetNode, script, isLoad, loadId, createBy);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
    }

    @Override
    public void insertScriptEvent(ISqlTransaction transaction, String channelId,
            Node targetNode, String script, boolean isLoad, long loadId, String createBy) {
        TriggerHistory history = engine.getTriggerRouterService()
                .findTriggerHistoryForGenericSync();
        Trigger trigger = engine.getTriggerRouterService().getTriggerById(history.getTriggerId(),
                false);
        String reloadChannelId = getReloadChannelIdForTrigger(trigger, engine
                .getConfigurationService().getChannels(false));
        Data data = new Data(history.getSourceTableName(), DataEventType.BSH,
                CsvUtils.escapeCsvData(script), null, history,
                isLoad ? reloadChannelId : channelId, null, null);
        data.setNodeList(targetNode.getNodeId());
        if (isLoad) {
            insertDataAndDataEventAndOutgoingBatch(transaction, data, targetNode.getNodeId(),
                    isLoad, loadId, createBy, Status.NE, null, -1);
        } else {
            insertData(transaction, data);
        }
    }

    @Override
    public int countDataInRange(long firstDataId, long secondDataId) {
        return sqlTemplate.queryForInt(getSql("countDataInRangeSql"), firstDataId, secondDataId);
    }

    @Override
    public int countData() {
        return sqlTemplate.queryForInt(getSql("countDataSql"));
    }

    @Override
    public void insertCreateEvent(final Node targetNode, TriggerHistory triggerHistory,
            boolean isLoad, long loadId, String createBy,
            boolean excludeIndices, boolean excludeForeignKeys, boolean excludeDefaults) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            insertCreateEvent(transaction, targetNode, triggerHistory, isLoad, loadId,
                    createBy, excludeIndices, excludeForeignKeys, excludeDefaults);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public void insertCreateEvent(Node targetNode, TriggerHistory triggerHistory, String createBy,
            boolean excludeIndices, boolean excludeForeignKeys, boolean excludeDefaults) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            Trigger trigger = engine.getTriggerRouterService().getTriggerById(triggerHistory.getTriggerId(), false);
            insertCreateEvent(transaction, targetNode, triggerHistory, trigger.getChannelId(), false, -1, createBy,
                    excludeIndices, excludeForeignKeys, excludeDefaults);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    protected void insertCreateEvent(ISqlTransaction transaction, Node targetNode,
            TriggerHistory triggerHistory, boolean isLoad, long loadId, String createBy,
            boolean excludeIndices, boolean excludeForeignKeys, boolean excludeDefaults) {
        Trigger trigger = engine.getTriggerRouterService().getTriggerById(
                triggerHistory.getTriggerId(), false);
        String reloadChannelId = getReloadChannelIdForTrigger(trigger, engine
                .getConfigurationService().getChannels(false));
        insertCreateEvent(transaction, targetNode, triggerHistory, isLoad ? reloadChannelId
                : Constants.CHANNEL_CONFIG, isLoad, loadId, createBy,
                excludeIndices, excludeForeignKeys, excludeDefaults);
    }

    @Override
    public void insertCreateEvent(ISqlTransaction transaction, Node targetNode,
            TriggerHistory triggerHistory, String channelId, boolean isLoad, long loadId, String createBy,
            boolean excludeIndices, boolean excludeForeignKeys, boolean excludeDefaults) {
        insertCreateEvent(transaction, targetNode, triggerHistory, channelId, isLoad, loadId,
                createBy, excludeIndices, excludeForeignKeys, excludeDefaults, Status.NE);
    }

    public void insertCreateEvent(ISqlTransaction transaction, Node targetNode,
            TriggerHistory triggerHistory, String channelId, boolean isLoad, long loadId, String createBy,
            boolean excludeIndices, boolean excludeForeignKeys, boolean excludeDefaults, Status outgoingBatchStatus) {
        Data data = new Data(triggerHistory.getSourceTableName(), DataEventType.CREATE,
                null, null, triggerHistory, channelId, null, null);
        data.setNodeList(targetNode.getNodeId());
        data.setOldData(getOptionsForExclusions(excludeIndices, excludeForeignKeys, excludeDefaults));
        try {
            if (isLoad) {
                insertDataAndDataEventAndOutgoingBatch(transaction, data, targetNode.getNodeId(),
                        isLoad, loadId, createBy, outgoingBatchStatus, null, -1);
            } else {
                insertData(transaction, data);
            }
        } catch (UniqueKeyException e) {
            if (e.getRootCause() != null && e.getRootCause() instanceof DataTruncation) {
                log.error(
                        "Table data definition XML was too large and failed.  The feature to send table creates during the initial load may be limited on your platform.  You may need to set the initial.load.create.first parameter to false.");
            }
            throw e;
        }
    }

    /**
     * @return The trigger histories that were used to insert the create event(s)
     */
    @Override
    public Set<TriggerHistory> insertCreateEventsForOutgoingBatchInError(OutgoingBatch batch, String createBy) {
        Set<TriggerHistory> triggerHistorySet = new HashSet<TriggerHistory>();
        if (batch.isLoadFlag()) {
            triggerHistorySet.addAll(getTriggerHistoriesForOutgoingLoadBatchInError(batch));
        } else if (batch.getFailedDataId() > 0) {
            CollectionUtils.addIgnoreNull(triggerHistorySet, getTriggerHistoryForOutgoingCdcBatchInError(batch));
        }
        String targetNodeId = batch.getNodeId();
        Node targetNode = new Node(targetNodeId, null);
        for (TriggerHistory triggerHistory : triggerHistorySet) {
            insertCreateEvent(targetNode, triggerHistory, false, -1, createBy, false, false, false);
        }
        return triggerHistorySet;
    }

    protected Set<TriggerHistory> getTriggerHistoriesForOutgoingLoadBatchInError(OutgoingBatch batch) {
        Set<TriggerHistory> triggerHistorySet = new HashSet<TriggerHistory>();
        ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
        long batchId = batch.getBatchId();
        for (ExtractRequest extractRequest : engine.getDataExtractorService()
                .getTablesForExtractByLoadIdAndNodeId(batch.getLoadId(), batch.getNodeId())) {
            if (batchId >= extractRequest.getStartBatchId() && batchId <= extractRequest.getEndBatchId()) {
                Trigger trigger = new Trigger(extractRequest.getTriggerId(), null);
                String tableName = extractRequest.getTableName();
                for (TriggerHistory triggerHistory : triggerRouterService.getActiveTriggerHistories(trigger)) {
                    if (triggerHistory.getSourceTableName().equalsIgnoreCase(tableName)) {
                        triggerHistorySet.add(triggerHistory);
                    }
                }
                break;
            }
        }
        return triggerHistorySet;
    }

    protected TriggerHistory getTriggerHistoryForOutgoingCdcBatchInError(OutgoingBatch batch) {
        Data data = findData(batch.getFailedDataId());
        if (data != null) {
            return data.getTriggerHistory();
        }
        return null;
    }

    private String getOptionsForExclusions(boolean excludeIndices, boolean excludeForeignKeys, boolean excludeDefaults) {
        StringBuilder sb = new StringBuilder();
        if (excludeIndices) {
            sb.append(Constants.SEND_SCHEMA_EXCLUDE_INDICES);
        }
        if (excludeForeignKeys) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(Constants.SEND_SCHEMA_EXCLUDE_FOREIGN_KEYS);
        }
        if (excludeDefaults) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(Constants.SEND_SCHEMA_EXCLUDE_DEFAULTS);
        }
        return sb.toString();
    }

    @Override
    public long insertData(Data data) {
        ISqlTransaction transaction = null;
        long dataId = -1;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            dataId = insertData(transaction, data);
            transaction.commit();
            return dataId;
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public long insertData(ISqlTransaction transaction, final Data data) {
        String sql = getSql("insertIntoDataSql");
        Object[] args = new Object[] { data.getTableName(), data.getDataEventType().getCode(), data.getRowData(),
                data.getPkData(), data.getOldData(),
                data.getTriggerHistory() != null ? data.getTriggerHistory().getTriggerHistoryId() : -1,
                data.getChannelId(), data.getExternalData(), data.getNodeList(), data.isPreRouted() ? 1 : 0,
                data.getTransactionId(), data.getSourceNodeId() };
        int[] types = new int[] { Types.VARCHAR, Types.CHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP };
        if (data.getCreateTime() != null) {
            args = ArrayUtils.add(args, data.getCreateTime());
        } else {
            args = ArrayUtils.add(args, new Date());
        }
        long id = transaction.insertWithGeneratedKey(sql, symmetricDialect.getSequenceKeyName(SequenceIdentifier.DATA),
                symmetricDialect.getSequenceName(SequenceIdentifier.DATA), args, types);
        data.setDataId(id);
        return id;
    }

    public void insertData(ISqlTransaction transaction, List<Data> datas) {
        transaction.prepare(getSql("insertIntoDataBulkSql"));
        int[] types = new int[] { Types.VARCHAR, Types.CHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.NUMERIC,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP };
        for (Data data : datas) {
            Object[] args = new Object[] { data.getTableName(), data.getDataEventType().getCode(), data.getRowData(),
                    data.getPkData(), data.getOldData(), data.getTriggerHistory() != null ? data.getTriggerHistory().getTriggerHistoryId() : -1,
                    data.getChannelId(), data.getTransactionId(), data.getSourceNodeId(), data.getCreateTime() };
            transaction.addRow(data, args, types);
        }
        transaction.flush();
    }

    protected void insertDataEvent(ISqlTransaction transaction, DataEvent dataEvent) {
        insertDataEvent(transaction, dataEvent.getDataId(), dataEvent.getBatchId());
    }

    protected void insertDataEvent(ISqlTransaction transaction, long dataId, long batchId) {
        try {
            transaction.prepareAndExecute(getSql("insertIntoDataEventSql"),
                    new Object[] { dataId, batchId, new Date() }, new int[] { Types.NUMERIC, Types.NUMERIC, Types.TIMESTAMP });
        } catch (RuntimeException ex) {
            throw new RuntimeException(String.format("Could not insert a data event: data_id=%s batch_id=%s",
                    dataId, batchId), ex);
        }
    }

    @Override
    public void insertDataEvents(ISqlTransaction transaction, final List<DataEvent> events) {
        if (events.size() > 0) {
            transaction.prepare(getSql("insertIntoDataEventSql"));
            for (DataEvent dataEvent : events) {
                transaction.addRow(
                        dataEvent, new Object[] { dataEvent.getDataId(), dataEvent.getBatchId(), new Date() },
                        new int[] { Types.NUMERIC, Types.NUMERIC, Types.TIMESTAMP });
            }
            transaction.flush();
        }
    }

    @Override
    public void insertDataAndDataEventAndOutgoingBatch(Data data, String channelId,
            List<Node> nodes, boolean isLoad, long loadId, String createBy) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            data.setPreRouted(true);
            long dataId = insertData(transaction, data);
            for (Node node : nodes) {
                insertDataEventAndOutgoingBatch(transaction, dataId, channelId, node.getNodeId(),
                        data.getDataEventType(), isLoad, loadId, createBy, Status.NE, data.getTableName(), -1);
            }
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    /**
     * @return The inserted batch id
     */
    @Override
    public long insertDataAndDataEventAndOutgoingBatch(Data data, String nodeId,
            boolean isLoad, long loadId, String createBy) {
        long batchId = 0;
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            batchId = insertDataAndDataEventAndOutgoingBatch(transaction, data, nodeId,
                    isLoad, loadId, createBy, Status.NE, null, -1);
            transaction.commit();
            return batchId;
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    /**
     * @param estimatedBatchRowCount
     *            TODO
     * @return The inserted batch id
     */
    public long insertDataAndDataEventAndOutgoingBatch(ISqlTransaction transaction, Data data, String nodeId, boolean isLoad,
            long loadId, String createBy, Status status, String overrideChannelId, long estimatedBatchRowCount) {
        data.setPreRouted(true);
        long dataId = insertData(transaction, data);
        String channelId = null;
        if (isLoad) {
            if (overrideChannelId != null) {
                channelId = overrideChannelId;
            } else {
                TriggerHistory history = data.getTriggerHistory();
                if (history != null && channelId == null) {
                    Trigger trigger = engine.getTriggerRouterService().getTriggerById(history.getTriggerId(), false);
                    channelId = getReloadChannelIdForTrigger(trigger, engine.getConfigurationService().getChannels(false));
                }
            }
        } else {
            channelId = data.getChannelId();
        }
        return insertDataEventAndOutgoingBatch(transaction, dataId, channelId, nodeId, data.getDataEventType(), isLoad, loadId,
                createBy, status, data.getTableName(), estimatedBatchRowCount);
    }

    @Override
    public long insertDataAndDataEventAndOutgoingBatch(ISqlTransaction transaction, Data data,
            String nodeId, boolean isLoad, long loadId, String createBy,
            Status status, long estimatedBatchRowCount) {
        return insertDataAndDataEventAndOutgoingBatch(transaction, data, nodeId, isLoad, loadId, createBy, status, null, estimatedBatchRowCount);
    }

    protected long insertDataEventAndOutgoingBatch(ISqlTransaction transaction, long dataId,
            String channelId, String nodeId, DataEventType eventType,
            boolean isLoad, long loadId, String createBy, Status status, String tableName, long estimatedBatchRowCount) {
        OutgoingBatch outgoingBatch = new OutgoingBatch(nodeId, channelId, status);
        outgoingBatch.setLoadId(loadId);
        outgoingBatch.setCreateBy(createBy);
        outgoingBatch.setLoadFlag(isLoad);
        outgoingBatch.incrementRowCount(eventType);
        if (estimatedBatchRowCount > 0) {
            outgoingBatch.setDataRowCount(estimatedBatchRowCount);
        } else {
            outgoingBatch.incrementDataRowCount();
        }
        if (tableName != null) {
            outgoingBatch.incrementTableCount(tableName.toLowerCase());
        }
        if (status == Status.RQ) {
            outgoingBatch.setExtractJobFlag(true);
        }
        engine.getOutgoingBatchService().insertOutgoingBatch(transaction, outgoingBatch);
        insertDataEvent(transaction, new DataEvent(dataId, outgoingBatch.getBatchId()));
        return outgoingBatch.getBatchId();
    }

    @Override
    public String reloadNode(String nodeId, boolean reverseLoad, String createBy) {
        INodeService nodeService = engine.getNodeService();
        Node targetNode = engine.getNodeService().findNode(nodeId);
        if (targetNode == null) {
            return String.format("Unknown node %s", nodeId);
        } else if (reverseLoad
                && nodeService.setReverseInitialLoadEnabled(nodeId, true, false, -1, createBy)) {
            return String.format("Successfully enabled reverse initial load for node %s", nodeId);
        } else if (nodeService.setInitialLoadEnabled(nodeId, true, false, -1, createBy)) {
            return String.format("Successfully enabled initial load for node %s", nodeId);
        } else {
            return String.format("Could not enable initial load for %s", nodeId);
        }
    }

    private void insertNodeSecurityUpdate(ISqlTransaction transaction, String nodeIdRecord,
            String targetNodeId, boolean isLoad, long loadId, String createBy, String channelId) {
        Data data = createData(transaction, null, null, tablePrefix + "_node_security",
                " t.node_id = '" + nodeIdRecord + "'");
        if (data != null) {
            insertDataAndDataEventAndOutgoingBatch(transaction, data, targetNodeId,
                    isLoad, loadId, createBy, Status.LS, channelId, -1);
        } else {
            throw new SymmetricException(String.format("Unable to issue an update for %s_node_security. " +
                    " Check the %s_trigger_hist for %s_node_security.", tablePrefix, tablePrefix, tablePrefix));
        }
    }

    @Override
    public void sendScript(String nodeId, String script, boolean isLoad) {
        Node targetNode = engine.getNodeService().findNode(nodeId, true);
        TriggerHistory history = engine.getTriggerRouterService()
                .findTriggerHistoryForGenericSync();
        Data data = new Data(history.getSourceTableName(), DataEventType.BSH,
                CsvUtils.escapeCsvData(script), null, history, Constants.CHANNEL_CONFIG, null, null);
        data.setNodeList(nodeId);
        if (!isLoad) {
            insertData(data);
        } else {
            insertDataAndDataEventAndOutgoingBatch(data, targetNode.getNodeId(),
                    isLoad, -1, null);
        }
    }

    @Override
    public boolean sendSchema(String nodeId, String catalogName, String schemaName,
            String tableName, boolean isLoad, boolean excludeIndices, boolean excludeForeignKeys,
            boolean excludeDefaults) {
        Node targetNode = engine.getNodeService().findNode(nodeId, true);
        if (targetNode == null) {
            log.error("Could not send schema to the node {}.  The target node does not exist", nodeId);
            return false;
        }
        ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
        List<TriggerHistory> triggerHistories = triggerRouterService.findTriggerHistories(
                catalogName, schemaName, tableName);
        int eventCount = 0;
        for (TriggerHistory triggerHistory : triggerHistories) {
            eventCount++;
            insertCreateEvent(targetNode, triggerHistory, false, -1, null,
                    excludeIndices, excludeForeignKeys, excludeDefaults);
        }
        if (eventCount > 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String sendSQL(String nodeId, String catalogName, String schemaName, String tableName,
            String sql) {
        Node sourceNode = engine.getNodeService().findIdentity();
        Node targetNode = engine.getNodeService().findNode(nodeId, true);
        if (targetNode == null) {
            return "Unknown node " + nodeId;
        }
        ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
        TriggerHistory triggerHistory = triggerRouterService.findTriggerHistory(catalogName,
                schemaName, tableName);
        if (triggerHistory == null) {
            return "Trigger for table " + tableName + " does not exist from node "
                    + sourceNode.getNodeGroupId();
        } else {
            Trigger trigger = triggerRouterService.getTriggerById(triggerHistory.getTriggerId());
            if (trigger != null) {
                ISqlTransaction transaction = null;
                try {
                    transaction = sqlTemplate.startSqlTransaction();
                    insertSqlEvent(transaction, triggerHistory, trigger.getChannelId(), targetNode,
                            sql, false, -1, null);
                    transaction.commit();
                    return "Successfully create SQL event for node " + targetNode.getNodeId();
                } catch (Error ex) {
                    if (transaction != null) {
                        transaction.rollback();
                    }
                    throw ex;
                } catch (RuntimeException ex) {
                    if (transaction != null) {
                        transaction.rollback();
                    }
                    throw ex;
                } finally {
                    close(transaction);
                }
            } else {
                return "Trigger for table " + tableName + " does not exist from node "
                        + sourceNode.getNodeGroupId();
            }
        }
    }

    @Override
    public String sendSQL(String nodeId, String sql) {
        String tableName = TableConstants.getTableName(parameterService.getTablePrefix(), TableConstants.SYM_NODE_HOST);
        Node sourceNode = engine.getNodeService().findIdentity();
        Node targetNode = engine.getNodeService().findNode(nodeId, true);
        if (targetNode == null) {
            return "Unknown node " + nodeId;
        }
        ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
        TriggerHistory triggerHistory = triggerRouterService.findTriggerHistory(null, null, tableName);
        if (triggerHistory == null) {
            return "Trigger for table " + tableName + " does not exist from node "
                    + sourceNode.getNodeGroupId();
        } else {
            Trigger trigger = triggerRouterService.getTriggerById(triggerHistory.getTriggerId());
            if (trigger != null) {
                ISqlTransaction transaction = null;
                try {
                    transaction = sqlTemplate.startSqlTransaction();
                    Data data = new Data(triggerHistory.getSourceTableName(), DataEventType.SQL,
                            CsvUtils.escapeCsvData(sql), null, triggerHistory, Constants.CHANNEL_CONFIG,
                            null, null);
                    data.setNodeList(targetNode.getNodeId());
                    insertDataAndDataEventAndOutgoingBatch(transaction, data, targetNode.getNodeId(),
                            false, -1, null, Status.NE, null, -1);
                    transaction.commit();
                    return "Successfully create SQL event for node " + targetNode.getNodeId();
                } catch (Error ex) {
                    if (transaction != null) {
                        transaction.rollback();
                    }
                    throw ex;
                } catch (RuntimeException ex) {
                    if (transaction != null) {
                        transaction.rollback();
                    }
                    throw ex;
                } finally {
                    close(transaction);
                }
            } else {
                return "Trigger for table " + tableName + " does not exist from node "
                        + sourceNode.getNodeGroupId();
            }
        }
    }

    @Override
    public String reloadTable(String nodeId, String catalogName, String schemaName, String tableName) {
        return reloadTable(nodeId, catalogName, schemaName, tableName, null);
    }

    @Override
    public String reloadTable(String nodeId, String catalogName, String schemaName,
            String tableName, String overrideInitialLoadSelect) {
        return reloadTable(nodeId, catalogName, schemaName, tableName, overrideInitialLoadSelect, null, false);
    }

    @Override
    public String reloadTableImmediate(String nodeId, String catalogName, String schemaName, String tableName,
            String overrideInitialLoadSelect, String overrideChannelId) {
        return reloadTable(nodeId, catalogName, schemaName, tableName, overrideInitialLoadSelect, overrideChannelId, true);
    }

    protected String reloadTable(String nodeId, String catalogName, String schemaName,
            String tableName, String overrideInitialLoadSelect, String overrideChannelId, boolean isImmediate) {
        Node sourceNode = engine.getNodeService().findIdentity();
        Node targetNode = engine.getNodeService().findNode(nodeId);
        if (targetNode == null) {
            return "Unknown node " + nodeId;
        }
        ITriggerRouterService triggerRouterService = engine.getTriggerRouterService();
        List<TriggerHistory> triggerHistories = triggerRouterService.findTriggerHistories(
                catalogName, schemaName, tableName);
        Map<Integer, List<TriggerRouter>> triggerRoutersByHistoryId = triggerRouterService
                .fillTriggerRoutersByHistIdAndSortHist(sourceNode.getNodeGroupId(),
                        targetNode.getNodeGroupId(), targetNode.getExternalId(), triggerHistories, false);
        int eventCount = 0;
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            for (TriggerHistory triggerHistory : triggerHistories) {
                List<TriggerRouter> triggerRouters = triggerRoutersByHistoryId.get(triggerHistory
                        .getTriggerHistoryId());
                boolean hasTriggerRouters = triggerRouters != null && triggerRouters.size() > 0;
                if (!hasTriggerRouters && triggerHistory.getSourceTableName().startsWith(parameterService.getTablePrefix())) {
                    if (overrideChannelId == null) {
                        overrideChannelId = Constants.CHANNEL_RELOAD;
                    }
                    hasTriggerRouters = triggerRouters.add(new TriggerRouter());
                }
                if (hasTriggerRouters) {
                    for (TriggerRouter triggerRouter : triggerRouters) {
                        eventCount++;
                        String channelId = overrideChannelId;
                        if (channelId == null) {
                            channelId = getReloadChannelIdForTrigger(triggerRouter.getTrigger(), engine
                                    .getConfigurationService().getChannels(false));
                        }
                        if (isImmediate) {
                            insertReloadEventImmediate(transaction, targetNode, triggerRouter, triggerHistory,
                                    overrideInitialLoadSelect, false, -1, "reloadTable", Status.NE, channelId, -1);
                        } else {
                            insertReloadEvent(transaction, targetNode, triggerRouter, triggerHistory,
                                    overrideInitialLoadSelect, false, -1, "reloadTable", Status.NE, channelId, -1);
                        }
                    }
                }
            }
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
        if (eventCount > 0) {
            return "Successfully created " + (eventCount > 1 ? eventCount + " events" : "event")
                    + " to reload table " + tableName + " for node "
                    + targetNode.getNodeId();
        } else {
            return "Trigger for table " + tableName + " does not exist for source node group of "
                    + sourceNode.getNodeGroupId();
        }
    }

    @Override
    public void reloadMissingForeignKeyRowsReverse(String sourceNodeId, Table table, CsvData data, String channelId, boolean sendCorrectionToPeers) {
        try {
            IDatabasePlatform platform = engine.getTargetDialect().getPlatform();
            Map<String, String> dataMap = data.toColumnNameValuePairs(table.getColumnNames(), CsvData.ROW_DATA);
            List<TableRow> tableRows = new ArrayList<TableRow>();
            Row row = new Row(dataMap.size());
            row.putAll(dataMap);
            Table localTable = platform.getTableFromCache(table.getCatalog(), table.getSchema(), table.getName(), false);
            if (localTable == null) {
                log.info("Could not find table " + table.getFullyQualifiedTableName());
            }
            tableRows.add(new TableRow(localTable, row, null, null, null));
            List<TableRow> foreignTableRows = platform.getDdlReader().getImportedForeignTableRows(tableRows, new HashSet<TableRow>(), symmetricDialect
                    .getBinaryEncoding());
            if (foreignTableRows.isEmpty()) {
                log.info("Could not determine foreign table rows to fix foreign key violation for "
                        + "nodeId '{}' table '{}'", sourceNodeId, localTable.getName());
            }
            Collections.reverse(foreignTableRows);
            Set<TableRow> visited = new HashSet<TableRow>();
            Node sourceNode = engine.getNodeService().findNode(sourceNodeId);
            Node identity = engine.getNodeService().findIdentity();
            StringBuilder script = new StringBuilder();
            List<Node> targetNodes = new ArrayList<Node>();
            targetNodes.add(identity);
            if (sendCorrectionToPeers) {
                targetNodes.addAll(engine.getNodeService().findEnabledNodesFromNodeGroup(sourceNode.getNodeGroupId()));
                targetNodes.remove(sourceNode);
            }
            for (TableRow foreignTableRow : foreignTableRows) {
                if (visited.add(foreignTableRow)) {
                    Table foreignTable = foreignTableRow.getTable();
                    String catalog = foreignTable.getCatalog();
                    String schema = foreignTable.getSchema();
                    if (StringUtils.equals(platform.getDefaultCatalog(), catalog)) {
                        catalog = null;
                        if (StringUtils.equals(platform.getDefaultSchema(), schema)) {
                            schema = null;
                        }
                    }
                    log.info(
                            "Requesting foreign key correction reload "
                                    + "nodeId {} catalog '{}' schema '{}' foreign table name '{}' fk name '{}' where sql '{}' "
                                    + "to correct table '{}' for column '{}'",
                            sourceNodeId, catalog, schema, foreignTable.getName(), foreignTableRow.getFkName(),
                            foreignTableRow.getWhereSql(), localTable.getName(), foreignTableRow.getReferenceColumnName());
                    for (Node targetNode : targetNodes) {
                        script.append("engine.getDataService().reloadTableImmediate(\"" + targetNode.getNodeId() + "\", " +
                                (catalog == null ? catalog : "\"" + catalog + "\"") + ", " +
                                (schema == null ? schema : "\"" + schema + "\"") + ", \"" +
                                foreignTable.getName().replace("\"", "\\\"") + "\", \"" +
                                foreignTableRow.getWhereSql().replace("\"", "\\\"") + "\", " +
                                (channelId == null ? channelId : "\"" + channelId + "\"") + ");\n");
                    }
                }
            }
            if (script.length() > 0) {
                insertScriptEvent("config", sourceNode, script.toString(), false, -1, "fk");
            }
        } catch (Exception e) {
            log.error("Unknown exception while processing foreign key for node id: " + sourceNodeId, e);
        }
    }

    @Override
    public void reloadMissingForeignKeyRowsForLoad(String sourceNodeId, long batchId, long rowNumber, Table table, CsvData data, String channelId) {
        String rowData = data.getCsvData(CsvData.ROW_DATA);
        // Replace all actual newlines and carriage returns with \n and \r strings
        rowData = rowData.replaceAll("\n", "\\n").replaceAll("\r", "\\r");
        rowData = CsvUtils.escapeCsvData(rowData);
        Node sourceNode = engine.getNodeService().findNode(sourceNodeId);
        String script = "try { engine.getDataService().sendMissingForeignKeyRowsForLoad(" + batchId + ", \""
                + engine.getNodeId() + "\", " + rowNumber + ", " + rowData + "); } catch (Exception e) { }";
        if (log.isDebugEnabled()) {
            log.debug("Requesting missing foreign key rows for batch {}-{} row {} for table {} row data {}", sourceNodeId, batchId, rowNumber,
                    table.getFullyQualifiedTableName(), data.getCsvData(CsvData.ROW_DATA));
        } else {
            log.info("Requesting missing foreign key rows for batch {}-{} row {} for table {}", sourceNodeId, batchId, rowNumber,
                    table.getFullyQualifiedTableName());
        }
        insertScriptEvent("config", sourceNode, script, false, -1, "fk");
    }

    public void sendMissingForeignKeyRowsForLoad(long batchId, String nodeId, long rowNumber, String rowData) {
        ISqlReadCursor<Data> cursor = null;
        Data data = null;
        long startBatchId = batchId;
        if (parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB)) {
            startBatchId = sqlTemplateDirty.queryForLong(getSql("selectStartBatchExtractRequest"), batchId, nodeId, engine.getNodeId());
            if (startBatchId == 0) {
                startBatchId = batchId;
            }
        }
        try {
            cursor = selectDataFor(startBatchId, nodeId, false);
            data = cursor.next();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (data != null) {
            data.putCsvData(CsvData.ROW_DATA, rowData);
            data.setDataEventType(DataEventType.INSERT);
            reloadMissingForeignKeyRows(data, batchId, nodeId, -1, rowNumber);
        } else {
            log.warn("Unable to correct missing foreign key error in batch {}-{} row {} because no data found", nodeId, batchId, rowNumber);
        }
    }

    @Override
    public void reloadMissingForeignKeyRows(long batchId, String nodeId, long dataId, long rowNumber) {
        reloadMissingForeignKeyRows(findData(dataId), batchId, nodeId, dataId, rowNumber);
    }

    protected void reloadMissingForeignKeyRows(Data data, long batchId, String nodeId, long dataId, long rowNumber) {
        String batchName = nodeId + "-" + batchId + " " + (dataId == -1 ? "row " + rowNumber : "data " + dataId);
        if (data == null) {
            log.warn("Unable to reload missing foreign data for data ID {} because data is not found", dataId);
            return;
        } else if (data.getDataEventType() != DataEventType.INSERT && data.getDataEventType() != DataEventType.UPDATE &&
                data.getDataEventType() != DataEventType.DELETE) {
            log.warn("Unable to reload missing foreign data for data ID {} because event type {} is not DML", dataId, data.getDataEventType());
            return;
        }
        log.debug("reloadMissingForeignKeyRows for batch {} table {}", batchName, data.getTableName());
        TriggerHistory hist = data.getTriggerHistory();
        IDatabasePlatform targetPlatform = getTargetPlatform(data.getTableName());
        ISymmetricDialect targetDialect = symmetricDialect.getTargetDialect(data.getTableName());
        Table table = targetPlatform.getTableFromCache(hist.getSourceCatalogName(), hist.getSourceSchemaName(), hist.getSourceTableName(), false);
        if (table == null) {
            log.info("Unable to lookup table " + hist.getFullyQualifiedSourceTableName());
            return;
        }
        table = table.copyAndFilterColumns(hist.getParsedColumnNames(), hist.getParsedPkColumnNames(), true, false);
        Object[] values = targetPlatform.getObjectValues(targetDialect.getBinaryEncoding(), data.getParsedData(CsvData.ROW_DATA), table.getColumns());
        List<TableRow> tableRows = new ArrayList<TableRow>();
        Row row = new Row(values.length);
        int i = 0;
        for (String columnName : table.getColumnNames()) {
            row.put(columnName, values.length > i ? values[i++] : null);
        }
        tableRows.add(new TableRow(table, row, null, null, null));
        List<TableRow> foreignTableRows = targetPlatform.getDdlReader().getImportedForeignTableRows(tableRows, new HashSet<TableRow>(), targetDialect
                .getBinaryEncoding());
        if (foreignTableRows.isEmpty()) {
            log.info("Could not determine foreign table rows to fix foreign key violation for "
                    + "batch {} table {}", batchName, data.getTableName());
        }
        sortTableRowsByForeignKeys(foreignTableRows);
        Set<TableRow> visited = new HashSet<TableRow>();
        boolean foundAllRows = true;
        for (TableRow foreignTableRow : foreignTableRows) {
            if (foreignTableRow.getRow().size() == 0) {
                log.info("Resolving batch {} to ignore the row because {} refers to a missing foreign row in {}", batchName,
                        table.getFullyQualifiedTableName(), foreignTableRow.getTable().getFullyQualifiedTableName());
                foundAllRows = false;
                break;
            }
        }
        if (foundAllRows) {
            for (TableRow foreignTableRow : foreignTableRows) {
                if (visited.add(foreignTableRow)) {
                    Table foreignTable = foreignTableRow.getTable();
                    String catalog = foreignTable.getCatalog();
                    String schema = foreignTable.getSchema();
                    if (StringUtils.equals(targetPlatform.getDefaultCatalog(), catalog)) {
                        catalog = null;
                        if (StringUtils.equals(targetPlatform.getDefaultSchema(), schema)) {
                            schema = null;
                        }
                    }
                    log.info("Issuing foreign key correction for batch {} table {}: foreign table '{}' column '{}' fk name '{}' where '{}'",
                            batchName, data.getTableName(), Table.getFullyQualifiedTableName(catalog, schema, foreignTable.getName()),
                            foreignTableRow.getReferenceColumnName(), foreignTableRow.getFkName(), foreignTableRow.getWhereSql());
                    try {
                        reloadTableImmediate(nodeId, catalog, schema, foreignTable.getName(), foreignTableRow.getWhereSql(),
                                dataId == -1 ? Constants.CHANNEL_CONFIG : null);
                    } catch (Exception ex) {
                        log.info("Failed to issue foreign key correction, but will try again,", ex);
                        reloadTableImmediate(nodeId, catalog, schema, foreignTable.getName(), foreignTableRow.getWhereSql(),
                                dataId == -1 ? Constants.CHANNEL_CONFIG : null);
                    }
                }
            }
        } else {
            sendSQL(nodeId, "update " + engine.getParameterService().getTablePrefix() +
                    "_incoming_error set resolve_ignore = 1 where batch_id = " + batchId + " and node_id = '" + engine.getNodeId() +
                    "' and failed_row_number = " + rowNumber);
        }
    }

    protected void sortTableRowsByForeignKeys(List<TableRow> tableRows) {
        List<Table> tables = new ArrayList<Table>();
        for (TableRow tableRow : tableRows) {
            tables.add(tableRow.getTable());
        }
        List<Table> sortedTables = Database.sortByForeignKeys(tables);
        Map<Table, Integer> tableMap = new HashMap<Table, Integer>();
        int index = 0;
        for (Table table : sortedTables) {
            tableMap.put(table, index++);
        }
        Collections.sort(tableRows, new Comparator<TableRow>() {
            @Override
            public int compare(TableRow t1, TableRow t2) {
                Integer i1 = tableMap.get(t1.getTable());
                Integer i2 = tableMap.get(t2.getTable());
                return i1 == null ? -1 : i2 == null ? 1 : i1.compareTo(i2);
            }
        });
    }

    @Override
    public void sendNewerDataToNode(ISqlTransaction transaction, String targetNodeId, String tableName, String pkCsvData,
            Date minCreateTime, String winningNodeId) {
        if (pkCsvData != null) {
            pkCsvData = pkCsvData.replace("\\n", "\n").replace("\\r", "\r");
        }
        List<Data> datas = transaction.query(getSql("selectData", "whereNewerData"), getDataMapper(),
                new Object[] { tableName, pkCsvData + "%", pkCsvData, minCreateTime },
                new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP });
        if (datas != null && datas.size() > 0) {
            Data data = datas.get(0);
            String dataNodeId = data.getSourceNodeId();
            if (dataNodeId == null || dataNodeId.equals("")) {
                dataNodeId = engine.getNodeId();
            }
            if (data.getCreateTime().getTime() > minCreateTime.getTime() || dataNodeId.hashCode() > winningNodeId.hashCode()) {
                data.setNodeList(targetNodeId);
                data.setChannelId(Constants.CHANNEL_RELOAD);
                insertData(transaction, data);
            }
        }
    }

    /**
     * Because we can't add a trigger on the _node table, we are artificially generating heartbeat events.
     * 
     * @param node
     */
    @Override
    public void insertHeartbeatEvent(Node node, boolean isReload) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            String tableName = TableConstants.getTableName(tablePrefix, TableConstants.SYM_NODE_HOST);
            List<NodeGroupLink> links = engine.getConfigurationService().getNodeGroupLinksFor(
                    parameterService.getNodeGroupId(), false);
            for (NodeGroupLink nodeGroupLink : links) {
                if (nodeGroupLink.getDataEventAction() == NodeGroupLinkAction.P) {
                    Set<TriggerRouter> triggerRouters = engine.getTriggerRouterService()
                            .getTriggerRouterForTableForCurrentNode(nodeGroupLink, null, null,
                                    tableName, false);
                    if (triggerRouters != null && triggerRouters.size() > 0) {
                        Data data = createData(transaction, triggerRouters.iterator().next()
                                .getTrigger(), String.format(" t.node_id = '%s'", node.getNodeId()));
                        if (data != null) {
                            insertData(transaction, data);
                        } else {
                            log.warn("Not generating data/data events for table {} "
                                    + "because a trigger or trigger hist is not created yet.",
                                    tableName);
                        }
                    } else {
                        log.warn("Not generating data/data events for table {} "
                                + "because a trigger or trigger hist is not created yet.",
                                tableName);
                    }
                }
            }
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public Data createData(String catalogName, String schemaName, String tableName) {
        return createData(catalogName, schemaName, tableName, null);
    }

    @Override
    public Data createData(String catalogName, String schemaName, String tableName,
            String whereClause) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            Data data = createData(transaction, catalogName, schemaName, tableName, whereClause);
            transaction.commit();
            return data;
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public Data createData(ISqlTransaction transaction, String catalogName, String schemaName,
            String tableName, String whereClause) {
        Data data = null;
        Set<TriggerRouter> triggerRouters = engine.getTriggerRouterService()
                .getTriggerRouterForTableForCurrentNode(catalogName, schemaName, tableName, false);
        if (triggerRouters != null && triggerRouters.size() > 0) {
            data = createData(transaction, triggerRouters.iterator().next().getTrigger(),
                    whereClause);
        }
        return data;
    }

    protected Data createData(ISqlTransaction transaction, Trigger trigger, String whereClause) {
        Data data = null;
        if (trigger != null) {
            TriggerHistory triggerHistory = engine.getTriggerRouterService()
                    .getNewestTriggerHistoryForTrigger(trigger.getTriggerId(),
                            trigger.getSourceCatalogName(), trigger.getSourceSchemaName(),
                            trigger.getSourceTableName());
            if (triggerHistory == null) {
                triggerHistory = engine.getTriggerRouterService().findTriggerHistory(
                        trigger.getSourceCatalogName(), trigger.getSourceSchemaName(),
                        trigger.getSourceTableName());
                if (triggerHistory == null) {
                    triggerHistory = engine.getTriggerRouterService().findTriggerHistory(
                            trigger.getSourceCatalogName(), trigger.getSourceSchemaName(),
                            trigger.getSourceTableName().toUpperCase());
                }
            }
            if (triggerHistory != null) {
                String rowData = null;
                String pkData = null;
                if (whereClause != null) {
                    rowData = getCsvDataFor(transaction, trigger, triggerHistory, whereClause, false);
                    pkData = getCsvDataFor(transaction, trigger, triggerHistory, whereClause, true);
                }
                data = new Data(trigger.getSourceTableName(), DataEventType.UPDATE, rowData,
                        pkData, triggerHistory, trigger.getChannelId(), null, null);
            }
        }
        return data;
    }

    protected String getCsvDataFor(ISqlTransaction transaction, Trigger trigger, TriggerHistory triggerHistory, String whereClause, boolean pkOnly,
            Table table) {
        String data = null;
        String sql = null;
        try {
            if (pkOnly) {
                sql = symmetricDialect.createCsvPrimaryKeySql(trigger, triggerHistory,
                        engine.getConfigurationService().getChannel(trigger.getChannelId()), whereClause, table);
            } else {
                sql = symmetricDialect.createCsvDataSql(trigger, triggerHistory,
                        engine.getConfigurationService().getChannel(trigger.getChannelId()), whereClause, table);
            }
        } catch (NotImplementedException e) {
        }
        if (isNotBlank(sql)) {
            data = transaction.queryForObject(sql, String.class);
        } else {
            DatabaseInfo databaseInfo = platform.getDatabaseInfo();
            String quote = databaseInfo.getDelimiterToken() == null || !parameterService.is(ParameterConstants.DB_DELIMITED_IDENTIFIER_MODE)
                    ? ""
                    : databaseInfo.getDelimiterToken();
            sql = "select " + triggerHistory.getColumnNames() + " from "
                    + Table.getFullyQualifiedTableName(triggerHistory.getSourceCatalogName(), triggerHistory.getSourceSchemaName(),
                            triggerHistory.getSourceTableName(), quote, databaseInfo.getCatalogSeparator(),
                            databaseInfo.getSchemaSeparator()) + " t where " + whereClause;
            Row row = transaction.queryForRow(sql);
            if (row != null) {
                data = row.csvValue();
            }
        }
        if (data != null) {
            data = data.trim();
        }
        return data;
    }

    protected String getCsvDataFor(ISqlTransaction transaction, Trigger trigger, TriggerHistory triggerHistory, String whereClause, boolean pkOnly) {
        Table table = platform.getTableFromCache(trigger.getSourceCatalogName(),
                trigger.getSourceSchemaName(), trigger.getSourceTableName(), false);
        return getCsvDataFor(transaction, trigger, triggerHistory, whereClause, pkOnly, table);
    }

    @Override
    public long countDataGaps() {
        long dataGapCount = sqlTemplate.queryForLong(getSql("countDataGapsSql"));
        engine.getStatisticManager().setDataGapCount(dataGapCount);
        return dataGapCount;
    }

    @Override
    public List<DataGap> findDataGapsUnchecked() {
        return findDataGaps(false);
    }

    @Override
    public List<DataGap> findDataGapsExpired() {
        return findDataGaps(true);
    }

    protected List<DataGap> findDataGaps(boolean isExpired) {
        List<DataGap> dataGapList = sqlTemplate.query(getSql("findDataGapsSql"), new ISqlRowMapper<DataGap>() {
            @Override
            public DataGap mapRow(Row rs) {
                return new DataGap(rs.getLong("start_id"), rs.getLong("end_id"), rs
                        .getDateTime("create_time"));
            }
        }, isExpired ? 1 : 0);
        if (!isExpired) {
            engine.getStatisticManager().setDataGapCount(dataGapList.size());
        }
        return dataGapList;
    }

    @Override
    public List<DataGap> findDataGaps() {
        final long maxDataToSelect = parameterService
                .getLong(ParameterConstants.ROUTING_LARGEST_GAP_SIZE);
        List<DataGap> gaps = findDataGapsUnchecked();
        boolean lastGapExists = false;
        long lastGapStartId = 0;
        for (DataGap dataGap : gaps) {
            lastGapExists |= dataGap.gapSize() >= maxDataToSelect - 1;
            lastGapStartId = Math.max(lastGapStartId, dataGap.getEndId());
        }
        if (!lastGapExists) {
            if (lastGapStartId == 0) {
                long maxRoutedDataId = findMaxDataEventDataId();
                long minDataId = findMinDataId() - 1; // -1 to make sure the ++ operation doesn't move past a piece of unrouted data.
                // At this point, determine the startId as the GREATER of the smallest known data id
                // or the largest known data id that was already routed.
                lastGapStartId = Math.max(minDataId, maxRoutedDataId);
            }
            if (lastGapStartId > -1) {
                lastGapStartId++;
            }
            DataGap gap = new DataGap(lastGapStartId, lastGapStartId + maxDataToSelect);
            log.info("Inserting missing last data gap: {}", gap);
            insertDataGap(gap);
            gaps = findDataGaps();
        }
        return gaps;
    }

    public long findMaxDataEventDataId() {
        return sqlTemplate.queryForLong(getSql("selectMaxDataEventDataIdSql"));
    }

    @Override
    public void insertDataGap(DataGap gap) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            insertDataGap(transaction, gap);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public void insertDataGap(ISqlTransaction transaction, DataGap gap) {
        log.debug("Inserting data gap: {}", gap);
        transaction.prepareAndExecute(getSql("insertDataGapSql"),
                new Object[] { engine.getClusterService().getServerId(), gap.getStartId(), gap.getEndId(), 0,
                        gap.getCreateTime() }, new int[] {
                                Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.TIMESTAMP });
    }

    @Override
    public void insertDataGaps(ISqlTransaction transaction, Collection<DataGap> gaps) {
        if (gaps.size() > 0) {
            int[] types = new int[] { Types.VARCHAR, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC, Types.TIMESTAMP };
            int maxRowsToFlush = engine.getParameterService().getInt(ParameterConstants.ROUTING_FLUSH_JDBC_BATCH_SIZE);
            long lastUpdateTimestamp = System.currentTimeMillis();
            int flushCount = 0, totalCount = 0;
            transaction.setInBatchMode(true);
            transaction.prepare(getSql("insertDataGapSql"));
            for (DataGap gap : gaps) {
                transaction.addRow(gap, new Object[] { engine.getClusterService().getServerId(), gap.getStartId(), gap.getEndId(), 0,
                        gap.getCreateTime() }, types);
                totalCount++;
                if (++flushCount >= maxRowsToFlush) {
                    transaction.flush();
                    flushCount = 0;
                }
                if (System.currentTimeMillis() - lastUpdateTimestamp > PROGRESS_LOG_UPDATE_DELAY_MS) {
                    log.info("Inserted {} of {} new gaps", totalCount, gaps.size());
                    lastUpdateTimestamp = System.currentTimeMillis();
                }
            }
            transaction.flush();
        }
    }

    @Override
    public void deleteDataGap(DataGap gap) {
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            deleteDataGap(transaction, gap);
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
    }

    @Override
    public void deleteDataGap(ISqlTransaction transaction, DataGap gap) {
        log.debug("Deleting data gap: {}", gap);
        int count = transaction.prepareAndExecute(
                getSql("deleteDataGapSql"),
                new Object[] { gap.getStartId(), gap.getEndId() },
                new int[] { symmetricDialect.getSqlTypeForIds(),
                        symmetricDialect.getSqlTypeForIds() });
        if (count == 0) {
            log.error("Failed to delete data gap: {}", gap);
        }
    }

    @Override
    public void deleteDataGaps(ISqlTransaction transaction, Collection<DataGap> gaps) {
        if (gaps.size() > 0) {
            int[] types = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() };
            int maxRowsToFlush = engine.getParameterService().getInt(ParameterConstants.ROUTING_FLUSH_JDBC_BATCH_SIZE);
            long lastUpdateTimestamp = System.currentTimeMillis();
            int flushCount = 0, totalCount = 0;
            transaction.setInBatchMode(true);
            transaction.prepare(getSql("deleteDataGapSql"));
            for (DataGap gap : gaps) {
                transaction.addRow(gap, new Object[] { gap.getStartId(), gap.getEndId() }, types);
                if (++flushCount >= maxRowsToFlush) {
                    transaction.flush();
                    flushCount = 0;
                }
                if (System.currentTimeMillis() - lastUpdateTimestamp > PROGRESS_LOG_UPDATE_DELAY_MS) {
                    log.info("Deleted {} of {} old gaps", totalCount, gaps.size());
                    lastUpdateTimestamp = System.currentTimeMillis();
                }
            }
            transaction.flush();
        }
    }

    @Override
    public void deleteAllDataGaps(ISqlTransaction transaction) {
        transaction.prepareAndExecute(getSql("deleteAllDataGapsSql"));
    }

    @Override
    public void expireDataGaps(ISqlTransaction transaction, Collection<DataGap> gaps) {
        if (gaps.size() > 0) {
            int[] types = new int[] { symmetricDialect.getSqlTypeForIds(), symmetricDialect.getSqlTypeForIds() };
            int maxRowsToFlush = engine.getParameterService().getInt(ParameterConstants.ROUTING_FLUSH_JDBC_BATCH_SIZE);
            long lastUpdateTimestamp = System.currentTimeMillis();
            int flushCount = 0, totalCount = 0;
            transaction.setInBatchMode(true);
            transaction.prepare(getSql("expireDataGapSql"));
            for (DataGap gap : gaps) {
                transaction.addRow(gap, new Object[] { gap.getStartId(), gap.getEndId() }, types);
                if (++flushCount >= maxRowsToFlush) {
                    transaction.flush();
                    flushCount = 0;
                }
                if (System.currentTimeMillis() - lastUpdateTimestamp > PROGRESS_LOG_UPDATE_DELAY_MS) {
                    log.info("Expired {} of {} gaps", totalCount, gaps.size());
                    lastUpdateTimestamp = System.currentTimeMillis();
                }
            }
            transaction.flush();
        }
    }

    @Override
    public Date findCreateTimeOfEvent(long dataId) {
        return sqlTemplate.queryForObject(getSql("findDataEventCreateTimeSql"), Date.class, dataId);
    }

    @Override
    public Date findCreateTimeOfData(long dataId) {
        return sqlTemplate.queryForObject(getSql("findDataCreateTimeSql"), Date.class, dataId);
    }

    @Override
    public Date findNextCreateTimeOfDataStartingAt(long dataId) {
        return findCreateTimeOfData(sqlTemplate.queryForObject(getSql("findMinDataSql"), Long.class, dataId));
    }

    /**
     * Get a list of {@link IHeartbeatListener}s that are ready for a heartbeat according to {@link IHeartbeatListener#getTimeBetweenHeartbeatsInSeconds()}
     * 
     * @param force
     *            if true, then return the entire list of {@link IHeartbeatListener}s
     */
    protected List<IHeartbeatListener> getHeartbeatListeners(boolean force) {
        if (force) {
            return extensionService.getExtensionPointList(IHeartbeatListener.class);
        } else {
            List<IHeartbeatListener> listeners = new ArrayList<IHeartbeatListener>();
            long ts = System.currentTimeMillis();
            for (IHeartbeatListener iHeartbeatListener : extensionService.getExtensionPointList(IHeartbeatListener.class)) {
                Long lastHeartbeatTimestamp = lastHeartbeatTimestamps.get(iHeartbeatListener);
                if (lastHeartbeatTimestamp == null
                        || lastHeartbeatTimestamp <= ts
                                - (iHeartbeatListener.getTimeBetweenHeartbeatsInSeconds() * 1000)) {
                    listeners.add(iHeartbeatListener);
                }
            }
            return listeners;
        }
    }

    protected void updateLastHeartbeatTime(List<IHeartbeatListener> listeners) {
        if (listeners != null) {
            Long ts = System.currentTimeMillis();
            for (IHeartbeatListener iHeartbeatListener : listeners) {
                lastHeartbeatTimestamps.put(iHeartbeatListener, ts);
            }
        }
    }

    /**
     * @see IDataService#heartbeat()
     */
    @Override
    public void heartbeat(boolean force) {
        List<IHeartbeatListener> listeners = getHeartbeatListeners(force);
        if (listeners.size() > 0) {
            Node me = engine.getNodeService().findIdentity();
            if (me != null) {
                List<IHeartbeatListener> successfulListeners = new ArrayList<IHeartbeatListener>();
                for (IHeartbeatListener l : listeners) {
                    try {
                        l.heartbeat(me);
                        successfulListeners.add(l);
                    } catch (Throwable e) {
                        log.error("Failed to execute IHeartbeatListener " + l.getClass().getSimpleName(), e);
                    }
                }
                updateLastHeartbeatTime(successfulListeners);
            } else {
                log.debug("Did not run the heartbeat process because the node has not been configured");
            }
        }
    }

    @Override
    public List<Number> listDataIds(long batchId, String nodeId) {
        return sqlTemplateDirty.query(getSql("selectEventDataIdsSql", getDataOrderBy()),
                new NumberMapper(), batchId, nodeId);
    }

    @Override
    public List<Data> listData(long batchId, String nodeId, long startDataId, String channelId,
            final int maxRowsToRetrieve) {
        return sqlTemplateDirty.query(getDataSelectSql(batchId, startDataId, channelId),
                maxRowsToRetrieve, new DataMapper(), new Object[] { batchId, nodeId, startDataId },
                new int[] { symmetricDialect.getSqlTypeForIds(), Types.VARCHAR, symmetricDialect.getSqlTypeForIds() });
    }

    @Override
    public Data findData(long dataId) {
        return sqlTemplateDirty.queryForObject(getSql("selectData", "whereDataId"), new DataMapper(), dataId);
    }

    public List<Data> findData(long startDataId, long endDataId) {
        return sqlTemplateDirty.query(getSql("selectData", "whereDataIdBetween"), new DataMapper(), startDataId, endDataId);
    }

    @Override
    public ISqlRowMapper<Data> getDataMapper() {
        return new DataMapper();
    }

    @Override
    public ISqlReadCursor<Data> selectDataFor(Batch batch) {
        return selectDataFor(batch.getBatchId(), batch.getTargetNodeId(), engine.getConfigurationService()
                .getNodeChannel(batch.getChannelId(), false).getChannel().isContainsBigLob());
    }

    @Override
    public ISqlReadCursor<Data> selectDataFor(Long batchId, String targetNodeId, boolean isContainsBigLob) {
        return sqlTemplateDirty.queryForCursor(
                getDataSelectSql(batchId, -1l, isContainsBigLob),
                new DataMapper(), new Object[] { batchId, targetNodeId },
                new int[] { symmetricDialect.getSqlTypeForIds(), Types.VARCHAR });
    }

    @Override
    public ISqlReadCursor<Data> selectDataFor(Long batchId, String channelId) {
        return sqlTemplateDirty.queryForCursor(getDataSelectByBatchSql(batchId, -1l, channelId),
                new DataMapper(), new Object[] { batchId }, new int[] { symmetricDialect.getSqlTypeForIds() });
    }

    protected String getDataSelectByBatchSql(long batchId, long startDataId, String channelId) {
        String startAtDataIdSql = startDataId >= 0l ? " and d.data_id >= ? " : "";
        return symmetricDialect.massageDataExtractionSql(
                getSql("selectEventDataByBatchIdSql", startAtDataIdSql, getDataOrderBy()),
                engine.getConfigurationService().getNodeChannel(channelId, false).getChannel().isContainsBigLob());
    }

    protected String getDataSelectSql(long batchId, long startDataId, String channelId) {
        return getDataSelectSql(batchId, startDataId,
                engine.getConfigurationService().getNodeChannel(channelId, false).getChannel().isContainsBigLob());
    }

    protected String getDataSelectSql(long batchId, long startDataId, boolean isContainsBigLob) {
        String startAtDataIdSql = startDataId >= 0l ? " and d.data_id >= ? " : "";
        return symmetricDialect.massageDataExtractionSql(
                getSql("selectEventDataToExtractSql", startAtDataIdSql, getDataOrderBy()), isContainsBigLob);
    }

    protected String getDataOrderBy() {
        String orderBy = "";
        if (parameterService.is(ParameterConstants.DBDIALECT_ORACLE_SEQUENCE_NOORDER, false)) {
            orderBy = " order by d.create_time asc, d.data_id asc";
        } else if (parameterService.is(ParameterConstants.ROUTING_DATA_READER_ORDER_BY_DATA_ID_ENABLED, true)) {
            orderBy = " order by d.data_id asc";
        }
        return orderBy;
    }

    @Override
    public long findMaxDataId() {
        long maxDataId = symmetricDialect.getCurrentSequenceValue(SequenceIdentifier.DATA);
        if (maxDataId == -1) {
            maxDataId = sqlTemplateDirty.queryForLong(getSql("selectMaxDataIdSql"));
        }
        return maxDataId;
    }

    @Override
    public long findMinDataId() {
        return sqlTemplateDirty.queryForLong(getSql("selectMinDataIdSql"));
    }

    @Override
    public void deleteCapturedConfigChannelData() {
        int count = sqlTemplate.update(getSql("deleteCapturedConfigChannelDataSql"));
        if (count > 0) {
            log.info("Deleted {} data rows that were on the config channel", count);
        }
    }

    @Override
    public Map<String, Date> getLastDataCaptureByChannel() {
        Map<String, Date> captureMap = new HashMap<String, Date>();
        LastCaptureByChannelMapper mapper = new LastCaptureByChannelMapper(captureMap);
        sqlTemplate.query(getSql("findLastCaptureTimeByChannelSql"), mapper);
        return mapper.getCaptureMap();
    }

    @Override
    public boolean fixLastDataGap() {
        boolean fixed = false;
        long maxDataId = findMaxDataId();
        List<DataGap> gaps = findDataGaps();
        if (gaps.size() > 0) {
            DataGap lastGap = gaps.get(gaps.size() - 1);
            if (lastGap.getEndId() < maxDataId) {
                fixed = true;
                log.warn("The last data id of {} was bigger than the last gap's end_id of {}.  Increasing the gap size", maxDataId, lastGap.getEndId());
                final long maxDataToSelect = parameterService
                        .getLong(ParameterConstants.ROUTING_LARGEST_GAP_SIZE);
                ISqlTransaction transaction = null;
                try {
                    transaction = sqlTemplate.startSqlTransaction();
                    deleteDataGap(transaction, lastGap);
                    insertDataGap(transaction, new DataGap(lastGap.getStartId(), maxDataId + maxDataToSelect));
                    transaction.commit();
                } catch (Error ex) {
                    if (transaction != null) {
                        transaction.rollback();
                    }
                    throw ex;
                } catch (RuntimeException ex) {
                    if (transaction != null) {
                        transaction.rollback();
                    }
                    throw ex;
                } finally {
                    close(transaction);
                }
            }
        }
        return fixed;
    }

    @Override
    public String findNodeIdsByNodeGroupId() {
        return getSql("findNodeIdsByNodeGroupIdSql");
    }

    protected void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("Thread received interrupt");
        }
    }

    public class DataMapper implements ISqlRowMapper<Data> {
        private List<TriggerRouter> triggerRouters;
        private List<TriggerHistory> activeTriggerHistories;
        private Collection<TriggerHistory> allTriggerHistories;
        private Map<Integer, TriggerHistory> remappedTriggerHistIds = new HashMap<Integer, TriggerHistory>();
        private HashMap<String, TriggerHistory> mismatchedTableName;
        private HashSet<Integer> missingConfigTriggerHist;
        private boolean lookupTriggerHist = true;

        public DataMapper() {
        }

        public DataMapper(boolean lookupTriggerHist) {
            this.lookupTriggerHist = lookupTriggerHist;
        }

        @Override
        public Data mapRow(Row row) {
            Data data = new Data();
            String rowData = row.getString("ROW_DATA", false);
            data.putCsvData(CsvData.ROW_DATA, isNotBlank(rowData) ? rowData : null);
            String pkData = row.getString("PK_DATA", false);
            data.putCsvData(CsvData.PK_DATA, isNotBlank(pkData) ? pkData : null);
            String oldData = row.getString("OLD_DATA", false);
            data.putCsvData(CsvData.OLD_DATA, isNotBlank(oldData) ? oldData : null);
            data.putAttribute(CsvData.ATTRIBUTE_CHANNEL_ID, row.getString("CHANNEL_ID"));
            data.putAttribute(CsvData.ATTRIBUTE_TX_ID, row.getString("TRANSACTION_ID", false));
            String tableName = row.getString("TABLE_NAME");
            data.putAttribute(CsvData.ATTRIBUTE_TABLE_NAME, tableName);
            data.setDataEventType(DataEventType.getEventType(row.getString("EVENT_TYPE")));
            data.putAttribute(CsvData.ATTRIBUTE_SOURCE_NODE_ID, row.getString("SOURCE_NODE_ID"));
            data.putAttribute(CsvData.ATTRIBUTE_EXTERNAL_DATA, row.getString("EXTERNAL_DATA"));
            data.putAttribute(CsvData.ATTRIBUTE_NODE_LIST, row.getString("NODE_LIST"));
            data.putAttribute(CsvData.ATTRIBUTE_DATA_ID, row.getLong("DATA_ID"));
            data.putAttribute(CsvData.ATTRIBUTE_CREATE_TIME, row.getDateTime("CREATE_TIME"));
            int triggerHistId = row.getInt("TRIGGER_HIST_ID");
            data.putAttribute(CsvData.ATTRIBUTE_TABLE_ID, triggerHistId);
            if (lookupTriggerHist) {
                TriggerHistory triggerHistory = engine.getTriggerRouterService().getTriggerHistory(triggerHistId);
                if (triggerHistory == null) {
                    triggerHistory = findOrCreateTriggerHistory(tableName, triggerHistId, data, false);
                } else if (!triggerHistory.getSourceTableName().equals(tableName)) {
                    if (mismatchedTableName == null) {
                        mismatchedTableName = new HashMap<String, TriggerHistory>();
                    }
                    TriggerHistory cachedTriggerHistory = mismatchedTableName.get(data.getTableName());
                    if (cachedTriggerHistory == null) {
                        log.warn("There was a mismatch between the data table name {} and the trigger_hist "
                                + "table name {} for data_id {}.  Attempting to look up a valid trigger_hist row by table name",
                                new Object[] { data.getTableName(),
                                        triggerHistory.getSourceTableName(), data.getDataId() });
                        triggerHistory = findOrCreateTriggerHistory(tableName, triggerHistId, data, true);
                        mismatchedTableName.put(data.getTableName(), triggerHistory);
                    } else {
                        triggerHistory = cachedTriggerHistory;
                    }
                }
                data.setTriggerHistory(triggerHistory);
            }
            data.setPreRouted(row.getBoolean("IS_PREROUTED"));
            return data;
        }

        private TriggerHistory findOrCreateTriggerHistory(String tableName, int triggerHistId, Data data, boolean isExistingTriggerHist) {
            TriggerHistory triggerHistory = null;
            Trigger trigger = null;
            Table table = null;
            long dataId = data.getDataId();
            triggerHistory = remappedTriggerHistIds.get(triggerHistId);
            if (triggerHistory != null) {
                return triggerHistory;
            }
            if (triggerRouters == null) {
                triggerRouters = engine.getTriggerRouterService().getAllTriggerRoutersForCurrentNode(engine.getNodeService().findIdentity().getNodeGroupId());
            }
            for (TriggerRouter triggerRouter : triggerRouters) {
                if (triggerRouter.isEnabled() && triggerRouter.getTrigger().getSourceTableName().equalsIgnoreCase(tableName)) {
                    trigger = triggerRouter.getTrigger();
                    table = platform.getTableFromCache(trigger.getSourceCatalogName(), trigger.getSourceSchemaName(), tableName, false);
                    break;
                }
            }
            if (table != null && trigger != null) {
                if (activeTriggerHistories == null) {
                    activeTriggerHistories = engine.getTriggerRouterService().getActiveTriggerHistories();
                    allTriggerHistories = engine.getTriggerRouterService().getHistoryRecords().values();
                }
                triggerHistory = findMatchingTriggerHistory(trigger, data, tableName);
                if (triggerHistory == null) {
                    triggerHistory = new TriggerHistory(table, trigger, engine.getSymmetricDialect().getTriggerTemplate());
                    triggerHistory.setTriggerHistoryId(isExistingTriggerHist ? 0 : triggerHistId);
                    triggerHistory.setLastTriggerBuildReason(TriggerReBuildReason.TRIGGER_HIST_MISSING);
                    List<String> triggerNamesGeneratedThisSession = new ArrayList<String>();
                    triggerHistory.setNameForInsertTrigger(engine.getTriggerRouterService().getTriggerName(DataEventType.INSERT,
                            symmetricDialect.getMaxTriggerNameLength(), trigger, table, activeTriggerHistories, null, triggerNamesGeneratedThisSession));
                    triggerHistory.setNameForUpdateTrigger(engine.getTriggerRouterService().getTriggerName(DataEventType.UPDATE,
                            symmetricDialect.getMaxTriggerNameLength(), trigger, table, activeTriggerHistories, null, triggerNamesGeneratedThisSession));
                    triggerHistory.setNameForDeleteTrigger(engine.getTriggerRouterService().getTriggerName(DataEventType.DELETE,
                            symmetricDialect.getMaxTriggerNameLength(), trigger, table, activeTriggerHistories, null, triggerNamesGeneratedThisSession));
                    log.warn("Could not find trigger history {} for table {} for data_id {}.  Generating a new trigger history row.",
                            triggerHistId, tableName, dataId);
                    engine.getTriggerRouterService().insert(triggerHistory);
                    activeTriggerHistories.add(triggerHistory);
                    allTriggerHistories.add(triggerHistory);
                } else {
                    remappedTriggerHistIds.put(triggerHistId, triggerHistory);
                    log.warn("Could not find trigger history {} for table {} for data_id {}.  Using trigger hist {} instead.",
                            triggerHistId, tableName, dataId, triggerHistory.getTriggerHistoryId());
                }
            } else {
                if (missingConfigTriggerHist == null) {
                    missingConfigTriggerHist = new HashSet<Integer>();
                }
                if (missingConfigTriggerHist.add(triggerHistId)) {
                    log.warn("Could not find trigger history {} for table {} for data_id {}.  Table is not configured to sync, so ignoring it.",
                            triggerHistId, tableName, dataId);
                }
                triggerHistory = new TriggerHistory(tableName, "", "");
                triggerHistory.setTriggerHistoryId(triggerHistId);
            }
            return triggerHistory;
        }

        private TriggerHistory findMatchingTriggerHistory(Trigger trigger, Data data, String tableName) {
            TriggerHistory triggerHistory = null;
            int columnCount = 0, pkColumnCount = 0;
            if (data.getDataEventType() == DataEventType.INSERT || data.getDataEventType() == DataEventType.UPDATE) {
                String[] rowData = data.getParsedData(CsvData.ROW_DATA);
                if (rowData == null) {
                    data = findData(data.getDataId());
                    if (data != null) {
                        rowData = data.getParsedData(CsvData.ROW_DATA);
                    }
                }
                if (rowData != null) {
                    columnCount = rowData.length;
                }
            }
            if (data.getDataEventType() == DataEventType.DELETE || data.getDataEventType() == DataEventType.UPDATE) {
                String[] pkData = data.getParsedData(CsvData.PK_DATA);
                if (pkData == null) {
                    data = findData(data.getDataId());
                    if (data != null) {
                        pkData = data.getParsedData(CsvData.PK_DATA);
                    }
                }
                if (pkData != null) {
                    pkColumnCount = pkData.length;
                }
            }
            for (TriggerHistory hist : allTriggerHistories) {
                if (hist.getTriggerId().equals(trigger.getTriggerId()) && hist.getSourceTableName().equalsIgnoreCase(tableName)
                        && (columnCount == 0 || columnCount == hist.getParsedColumnNames().length)
                        && (pkColumnCount == 0 || pkColumnCount == hist.getParsedPkColumnNames().length)) {
                    triggerHistory = hist;
                    break;
                }
            }
            if (triggerHistory == null) {
                for (TriggerHistory hist : activeTriggerHistories) {
                    if (hist.getTriggerId().equals(trigger.getTriggerId()) && hist.getSourceTableName().equalsIgnoreCase(tableName)) {
                        triggerHistory = hist;
                        break;
                    }
                }
            }
            return triggerHistory;
        }

        public Data findData(long dataId) {
            return sqlTemplateDirty.queryForObject(getSql("selectData", "whereDataId"), new DataMapper(false), dataId);
        }
    }

    @Override
    public int resendBatchAsReload(long batchId, String nodeId) {
        List<Data> dataList = new ArrayList<Data>();
        log.info("Resending as reload for batch {}-{}", nodeId, batchId);
        ISqlReadCursor<Data> cursor = selectDataFor(batchId, nodeId, true);
        try {
            Data data = null;
            while ((data = cursor.next()) != null) {
                dataList.add(data);
            }
        } finally {
            cursor.close();
        }
        resendDataAsReload(dataList, nodeId);
        return dataList.size();
    }

    @Override
    public int resendDataAsReload(long minDataId, long maxDataId) {
        List<Data> dataList = findData(minDataId, maxDataId);
        if (dataList.size() > 0) {
            resendDataAsReload(dataList, null);
        }
        return dataList.size();
    }

    protected void resendDataAsReload(List<Data> dataList, String nodeId) {
        IDataService dataService = engine.getDataService();
        IDatabasePlatform platform = engine.getDatabasePlatform();
        for (Data data : dataList) {
            TriggerHistory hist = data.getTriggerHistory();
            Table table = platform.getTableFromCache(hist.getSourceCatalogName(), hist.getSourceSchemaName(), hist.getSourceTableName(), false);
            if (table != null) {
                table = table.copyAndFilterColumns(hist.getParsedColumnNames(), hist.getParsedPkColumnNames(), true, false);
                if (data.getDataEventType() == DataEventType.INSERT || data.getDataEventType() == DataEventType.UPDATE) {
                    convertDataToReload(data, table, hist, nodeId);
                } else if (data.getDataEventType() == DataEventType.DELETE) {
                    String[] pkData = data.getParsedData(CsvData.PK_DATA);
                    Object[] values = platform.getObjectValues(engine.getSymmetricDialect().getBinaryEncoding(), pkData, table.getPrimaryKeyColumns());
                    DmlStatement st = platform.createDmlStatement(DmlType.COUNT, table.getCatalog(), table.getSchema(),
                            table.getName(), table.getPrimaryKeyColumns(), table.getPrimaryKeyColumns(), DmlStatement.getNullKeyValues(values), null);
                    int count = platform.getSqlTemplateDirty().queryForInt(st.getSql(), values);
                    if (count > 0) {
                        convertDataToReload(data, table, hist, nodeId);
                    } else {
                        data.setNodeList(nodeId);
                        data.setExternalData(null);
                        data.setPreRouted(false);
                        data.setChannelId(Constants.CHANNEL_RELOAD);
                    }
                }
            }
        }
        ISqlTransaction transaction = null;
        try {
            transaction = engine.getSqlTemplate().startSqlTransaction();
            for (Data data : dataList) {
                dataService.insertData(transaction, data);
            }
            transaction.commit();
        } catch (Error ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
    }

    protected void convertDataToReload(Data data, Table table, TriggerHistory hist, String nodeId) {
        IDatabasePlatform platform = engine.getDatabasePlatform();
        BinaryEncoding encoding = engine.getSymmetricDialect().getBinaryEncoding();
        String[] pkNames = hist.getParsedPkColumnNames();
        String[] pkData = null;
        if (data.getDataEventType() == DataEventType.INSERT) {
            pkData = ArrayUtils.subarray(data.getParsedData(CsvData.ROW_DATA), 0, pkNames.length);
        } else {
            pkData = data.getParsedData(CsvData.PK_DATA);
        }
        DmlStatement st = platform.createDmlStatement(DmlType.WHERE, table.getCatalog(), table.getSchema(),
                table.getName(), table.getPrimaryKeyColumns(), table.getPrimaryKeyColumns(), DmlStatement.getNullKeyValues(pkData), null);
        Row row = new Row(pkNames.length);
        Object[] values = platform.getObjectValues(encoding, pkData, table.getPrimaryKeyColumns());
        for (int i = 0; i < pkNames.length; i++) {
            row.put(pkNames[i], values[i]);
        }
        String where = st.buildDynamicSql(encoding, row, false, false);
        String delimiter = engine.getDatabasePlatform().getDatabaseInfo().getSqlCommandDelimiter();
        if (where != null && where.endsWith(delimiter)) {
            where = where.substring(0, where.length() - delimiter.length());
        }
        data.setPkData(null);
        data.setRowData(where);
        data.setOldData(null);
        data.setNodeList(nodeId);
        data.setExternalData(null);
        data.setPreRouted(false);
        data.setDataEventType(DataEventType.RELOAD);
        data.setChannelId(Constants.CHANNEL_RELOAD);
    }

    /**
     * Attempts to recapture stale data rows between specified Ids and re-insert them back into sym_data with a new transaction ID. Commits every
     * RECAPTURE_DATA_COMMIT_LIMIT records to avoid locking sym_data from other processes.
     * 
     * @return number of recaptured data rows
     */
    @Override
    public int reCaptureData(long minDataId, long maxDataId) {
        long queryStartDataId = minDataId;
        long queryEndDataId = Long.min(minDataId + RECAPTURE_DATA_COMMIT_LIMIT - 1, maxDataId);
        List<Data> dataList = findData(queryStartDataId, queryEndDataId);
        if (dataList == null || dataList.size() < 1) {
            return 0;
        }
        int reCapturedCount = 0;
        while (queryStartDataId <= maxDataId) {
            if (dataList.size() > 0) {
                reCapturedCount += reCaptureData(dataList);
            }
            queryStartDataId = ++queryEndDataId;
            queryEndDataId = Long.min(queryEndDataId + RECAPTURE_DATA_COMMIT_LIMIT - 1, maxDataId);
            if (queryStartDataId > maxDataId) {
                break;
            }
            dataList = findData(queryStartDataId, queryEndDataId);
        }
        return reCapturedCount;
    }

    /**
     * Attempts to recapture stale data rows and re-insert them back into sym_data with a new transaction ID (all records are in one transaction).
     * 
     * @return number of recaptured data rows
     */
    protected int reCaptureData(List<Data> dataList) {
        List<Data> insertList = new ArrayList<Data>();
        long lastUpdateTimestamp = 0;
        String recaptureTransactionId = this.generateTransactionIdFromTimestamp("recapture-", Instant.now());
        int insertedCount = 0;
        try {
            for (Data data : dataList) {
                Data recapturedData = fetchRecapturedData(data, recaptureTransactionId);
                if (recapturedData == null) {
                    continue;
                }
                insertList.add(recapturedData);
                if (System.currentTimeMillis() - lastUpdateTimestamp > PROGRESS_LOG_UPDATE_DELAY_MS) {
                    lastUpdateTimestamp = System.currentTimeMillis();
                    log.info("Recaptured stale data_id={}, table={}, list.size={}, transactionid={}", recapturedData.getDataId(), recapturedData.getTableName(),
                            dataList.size(), recaptureTransactionId);
                }
            }
            insertedCount = insertRecapturedData(insertList);
        } catch (RuntimeException e) {
            if (insertedCount == 0 && insertList.size() > 0) {
                insertedCount = insertRecapturedData(insertList);
            }
        }
        return insertedCount;
    }

    /**
     * Attempts to recapture expired data row, which is still relevant and assigns a new transaction ID
     * 
     * @return recaptured data row
     */
    protected Data fetchRecapturedData(Data data, String recaptureTransactionId) {
        if (data == null) {
            return null;
        }
        if (data.isPreRouted() || !(data.getDataEventType().isDml())) {
            return null;
        }
        Table table = null;
        String[] keys = null;
        ISqlTransaction transaction = null;
        try {
            TriggerHistory hist = data.getTriggerHistory();
            if (hist == null) {
                log.warn("Unable to recapture stale data_id={} because table={} no longer has trigger history! channel_id={}", data.getDataId(), data
                        .getTableName(), data.getChannelId());
                return null;
            }
            String fullTableName = hist.getSourceCatalogName() + "." + hist.getSourceSchemaName() + "." + hist.getSourceTableName();
            Set<TriggerRouter> triggerRouters = engine.getTriggerRouterService().getTriggerRouterForTableForCurrentNode(
                    hist.getSourceCatalogName(), hist.getSourceSchemaName(), hist.getSourceTableName(), false);
            if (triggerRouters == null || triggerRouters.size() <= 0) {
                log.warn("Unable to recapture stale data_id={} because table={} no longer has a trigger-router! channel_id={}", data.getDataId(),
                        fullTableName, data.getChannelId());
                return null;
            }
            table = platform.getTableFromCache(hist.getSourceCatalogName(), hist.getSourceSchemaName(), hist.getSourceTableName(), false);
            if (table == null) {
                log.warn("Unable to recapture stale data_id={} because table={} was not found!)", data.getDataId(), fullTableName);
                return null;
            }
            Trigger trigger = triggerRouters.iterator().next().getTrigger();
            table = table.copyAndFilterColumns(hist.getParsedColumnNames(), hist.getParsedPkColumnNames(), true, false);
            keys = recaptureKeysForData(table, data);
            Object[] values = platform.getObjectValues(engine.getSymmetricDialect().getBinaryEncoding(), keys, table.getPrimaryKeyColumns());
            if (keys == null || values == null) {
                return null;
            }
            String whereClause = recaptureWhereFilterForKeys(table, hist, keys, values);
            String actualRowData = null;
            String pkData = data.getPkData();
            String dataSummary4Log = String.format("data_id=%d, event=%s, table=%s, PK: %s", data.getDataId(),
                    data.getDataEventType().toString(), table.getFullyQualifiedTableName(), (whereClause.length() <= 100) ? whereClause
                            : whereClause.substring(0, 100));
            // Look this record up:
            transaction = sqlTemplate.startSqlTransaction();
            actualRowData = getCsvDataFor(transaction, trigger, hist, whereClause, false, table);
            if (actualRowData != null && data.getDataEventType() == DataEventType.INSERT) {
                pkData = getCsvDataFor(transaction, trigger, hist, whereClause, true, table);
            }
            close(transaction);
            transaction = null;
            Data recapturedData = null;
            if (data.getDataEventType() == DataEventType.INSERT || data.getDataEventType() == DataEventType.UPDATE) {
                if (actualRowData == null) {
                    log.info("Skipped recapture of stale data because record no longer exists in database. {}", dataSummary4Log);
                    return null;
                }
                recapturedData = data;
                recapturedData.setRowData(actualRowData);
                recapturedData.setPkData(pkData);
                recapturedData.setDataEventType(DataEventType.UPDATE);
            } else if (data.getDataEventType() == DataEventType.DELETE) {
                if (actualRowData != null) {
                    log.info("Skipped recapture of stale data because record exists in database. {}", dataSummary4Log);
                    return null;
                }
                recapturedData = data;
            }
            // Double-check that stale data still conforms to table definition:
            if (!hasColumnDataIntegrity(recapturedData, hist)) {
                log.warn("Unable to recapture stale data because row values no longer match tables columns! {}", dataSummary4Log);
                return null;
            }
            if (log.isDebugEnabled()) {
                log.debug("Recaptured stale data. {}", dataSummary4Log);
            }
            return recapturedData;
        } catch (RuntimeException e) {
            if (table != null && keys != null) {
                Column[] columns = table.getPrimaryKeyColumns();
                String[] names = new String[columns.length];
                String[] types = new String[columns.length];
                int i = 0;
                for (Column col : columns) {
                    names[i] = col.getName();
                    types[i++] = col.getMappedType();
                }
                StringBuilder failureMessage = new StringBuilder();
                failureMessage.append("Failed to recapture for data ").append(data.getDataId());
                failureMessage.append(" event type ").append(data.getDataEventType().toString());
                failureMessage.append(" table ").append(table.getName());
                failureMessage.append(" with primary key ").append(ArrayUtils.toString(names));
                failureMessage.append(" and types ").append(ArrayUtils.toString(types)).append("\n");
                data.writeCsvDataDetails(failureMessage);
                log.info(failureMessage.toString());
            } else {
                log.warn("Exception during recapture of stale data_id={}; table={}; Event type={}; Message={}", data.getDataId(), data.getTableName(), data
                        .getDataEventType().toString(), e.getMessage());
            }
            throw e;
        } catch (Exception ex) {
            log.error("Non-RuntimeException during recapture of stale data_id={}; table={}; Event type={}; Message={}", data.getDataId(), data.getTableName(),
                    data.getDataEventType().toString(), ex.getMessage());
            throw ex;
        } finally {
            if (transaction != null) {
                close(transaction);
                transaction = null;
            }
        }
    }

    /**
     * Inserts recaptured data back into sym_data
     * 
     * @return number of inserted rows
     */
    protected int insertRecapturedData(List<Data> insertList) {
        if (insertList == null || insertList.size() < 0) {
            return 0;
        }
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            for (int i = 0; i < insertList.size(); i++) {
                Data data = insertList.get(i);
                insertData(transaction, data);
            }
            transaction.commit();
        } catch (Exception ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            if (transaction != null) {
                close(transaction);
            }
        }
        return insertList.size();
    }

    /**
     * Looks up values of primary keys for specified data row object
     */
    protected String[] recaptureKeysForData(Table table, Data data) {
        String[] keys = null;
        if (data.getDataEventType() == DataEventType.INSERT) {
            keys = data.toParsedRowData();
            if (keys != null && keys.length >= table.getPrimaryKeyColumnCount()) {
                keys = ArrayUtils.subarray(keys, 0, table.getPrimaryKeyColumnCount());
            }
        } else {
            keys = data.toParsedPkData();
        }
        return keys;
    }

    /**
     * Extracts filter for a where clause for specified values of primary keys
     */
    protected String recaptureWhereFilterForKeys(Table table, TriggerHistory hist, String[] keys, Object[] values) {
        Row row = new Row(keys.length);
        String[] keyNames = table.getPrimaryKeyColumnNames();
        for (int i = 0; i < keyNames.length && i < values.length; i++) {
            row.put(keyNames[i], values[i]);
        }
        DmlStatement st = platform.createDmlStatement(DmlType.WHERE, hist.getSourceCatalogName(), hist.getSourceSchemaName(),
                hist.getSourceTableName(), table.getPrimaryKeyColumns(), table.getColumns(), DmlStatement.getNullKeyValues(keys), null);
        String whereClause = st.buildDynamicSql(symmetricDialect.getBinaryEncoding(), row, false,
                platform.getDatabaseInfo().isJdbcTimestampAllowed()).substring(6);
        String delimiter = platform.getDatabaseInfo().getSqlCommandDelimiter();
        if (delimiter != null && delimiter.length() > 0) {
            whereClause = whereClause.substring(0, whereClause.length() - delimiter.length());
        }
        return whereClause;
    }

    protected boolean hasColumnDataIntegrity(Data data, TriggerHistory hist) {
        String[] colNames = hist.getParsedColumnNames();
        String[] pkNames = hist.getParsedPkColumnNames();
        String[] rowData = data.toParsedRowData();
        String[] oldData = data.toParsedOldData();
        String[] pkData = data.toParsedPkData();
        DataEventType type = data.getDataEventType();
        boolean okay = false;
        if (type == DataEventType.INSERT) {
            okay = rowData != null && rowData.length == colNames.length;
        } else if (type == DataEventType.UPDATE) {
            okay = rowData != null && rowData.length == colNames.length && (oldData == null || oldData.length == colNames.length) &&
                    (pkData == null || pkData.length == pkNames.length) && (pkData != null || oldData != null);
        } else if (type == DataEventType.DELETE) {
            okay = (pkData == null || pkData.length == pkNames.length) && (oldData == null || oldData.length == colNames.length) &&
                    (pkData != null || oldData != null);
        }
        return okay;
    }

    public static class LastCaptureByChannelMapper implements ISqlRowMapper<String> {
        private Map<String, Date> captureMap;

        public LastCaptureByChannelMapper(Map<String, Date> map) {
            captureMap = map;
        }

        public Map<String, Date> getCaptureMap() {
            return captureMap;
        }

        @Override
        public String mapRow(Row row) {
            captureMap.put(row.getString("CHANNEL_ID"), row.getDateTime("CREATE_TIME"));
            return null;
        }
    }
}
