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

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.AbstractBatch;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeSecurity;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.service.FilterCriterion;
import org.jumpmind.symmetric.service.FilterCriterion.FilterOption;
import org.jumpmind.symmetric.service.IAcknowledgeService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.IService;
import org.jumpmind.symmetric.transport.IOutgoingWithResponseTransport;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.jumpmind.symmetric.web.WebConstants;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractService implements IService {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected IParameterService parameterService;
    protected ISymmetricDialect symmetricDialect;
    protected ISqlTemplate sqlTemplate;
    protected ISqlTemplate sqlTemplateDirty;
    protected IDatabasePlatform platform;
    protected String tablePrefix;
    private ISqlMap sqlMap;
    private Set<String> logOnce = new HashSet<String>();

    public AbstractService() {
    }

    public AbstractService(IParameterService parameterService, ISymmetricDialect symmetricDialect) {
        this.symmetricDialect = symmetricDialect;
        this.parameterService = parameterService;
        this.tablePrefix = parameterService.getTablePrefix();
        this.platform = symmetricDialect.getPlatform();
        this.sqlTemplate = symmetricDialect.getPlatform().getSqlTemplate();
        this.sqlTemplateDirty = symmetricDialect.getPlatform().getSqlTemplateDirty();
    }

    private static final Comparator<BatchAck> BATCH_ID_COMPARATOR = new Comparator<BatchAck>() {
        public int compare(BatchAck batchInfo1, BatchAck batchInfo2) {
            Long batchId1 = batchInfo1.getBatchId();
            Long batchId2 = batchInfo2.getBatchId();
            return batchId1.compareTo(batchId2);
        }
    };

    protected Date maxDate(Date... dates) {
        Date date = null;
        if (dates != null) {
            for (Date d : dates) {
                if (d != null) {
                    if (date == null || d.after(date)) {
                        date = d;
                    }
                }
            }
        }
        return date;
    }

    protected void setSqlMap(ISqlMap sqlMap) {
        this.sqlMap = sqlMap;
    }

    public ISqlTemplate getJdbcTemplate() {
        return symmetricDialect.getPlatform().getSqlTemplate();
    }

    synchronized public void synchronize(Runnable runnable) {
        runnable.run();
    }

    protected boolean isSet(Object value) {
        if (value != null && value.toString().equals("1")) {
            return true;
        } else {
            return false;
        }
    }

    protected Map<String, String> createSqlReplacementTokens() {
        return AbstractSqlMap.mergeSqlReplacementTokens(symmetricDialect.getSqlReplacementTokens(), tablePrefix);
    }

    public String getSql(String... keys) {
        if (sqlMap != null) {
            return sqlMap.getSql(keys);
        } else {
            return null;
        }
    }

    public IParameterService getParameterService() {
        return parameterService;
    }

    public ISymmetricDialect getSymmetricDialect() {
        return symmetricDialect;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    protected void close(ISqlTransaction transaction) {
        if (transaction != null) {
            transaction.close();
        }
    }

    protected Set<String> toNodeIds(Set<Node> nodes) {
        return toNodeIds(nodes, null);
    }

    protected Set<String> toNodeIds(Set<Node> nodes, Set<String> nodeIds) {
        nodeIds = nodeIds == null ? new HashSet<String>(nodes.size()) : nodeIds;
        for (Node node : nodes) {
            nodeIds.add(node.getNodeId());
        }
        return nodeIds;
    }

    protected boolean isCalledFromSymmetricAdminTool() {
        boolean adminTool = false;
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : trace) {
            adminTool |= stackTraceElement.getClassName().equals(
                    "org.jumpmind.symmetric.SymmetricAdmin");
        }
        return adminTool;
    }

    protected String buildBatchWhere(List<String> nodeIds, List<String> channels,
            List<?> statuses, List<Long> loads, Date startAtLastUpdatedTime) {
        boolean containsErrorStatus = statuses.contains(AbstractBatch.Status.ER);
        boolean containsIgnoreStatus = statuses.contains(AbstractBatch.Status.IG);
        StringBuilder where = new StringBuilder();
        boolean needsAnd = false;
        if (nodeIds != null && nodeIds.size() > 0) {
            where.append("node_id in (:NODES)");
            needsAnd = true;
        }
        if (channels != null && channels.size() > 0) {
            if (needsAnd) {
                where.append(" and ");
            }
            where.append("channel_id in (:CHANNELS)");
            needsAnd = true;
        }
        if (loads != null && loads.size() > 0) {
            if (needsAnd) {
                where.append(" and ");
            }
            where.append("load_id in (:LOADS)");
            needsAnd = true;
        }
        if (statuses.size() > 0) {
            if (needsAnd) {
                where.append(" and ");
            }
            where.append("(status in (:STATUSES)");
            if (containsErrorStatus) {
                where.append(" or error_flag = 1 ");
            }
            if (containsIgnoreStatus) {
                where.append(" or ignore_count > 0 ");
            }
            where.append(")");
            needsAnd = true;
        }
        if (startAtLastUpdatedTime != null) {
            if (needsAnd) {
                where.append(" and ");
            }
            where.append("last_update_time >= :LAST_UPDATE_TIME");
            needsAnd = true;
        }
        if (where.length() > 0) {
            where.insert(0, " where ");
        }
        return where.toString();
    }

    protected String buildBatchWhereFromFilter(List<FilterCriterion> filter) {
        StringBuilder where = new StringBuilder();
        boolean needsAnd = false;
        int id = 0;
        for (FilterCriterion criterion : filter) {
            if (needsAnd) {
                where.append(" and ");
            } else {
                needsAnd = true;
            }
            FilterOption option = criterion.getOption();
            String optionSql = option.toSql();
            String prefix = null;
            boolean checkErrorFlag = false;
            switch (criterion.getPropertyId()) {
                case "nodeId":
                    prefix = "node_id " + optionSql;
                    break;
                case "batchId":
                    prefix = "batch_id " + optionSql;
                    break;
                case "status":
                    checkErrorFlag = (option == FilterOption.EQUALS || option == FilterOption.IN_LIST)
                            && criterion.getValues().contains(Status.ER.toString());
                    prefix = (checkErrorFlag ? "(error_flag = 1 or " : "") + "status " + optionSql;
                    break;
                case "channelId":
                    prefix = "channel_id " + optionSql;
                    break;
                case "createTime":
                    prefix = "create_time " + optionSql;
                    break;
                case "loadId":
                    prefix = "load_id " + optionSql;
                    break;
                case "lastUpdatedTime":
                    prefix = "last_update_time " + optionSql;
            }
            if (prefix != null) {
                if (option == FilterOption.IN_LIST || option == FilterOption.NOT_IN_LIST) {
                    where.append(prefix + " (:" + id++ + ")");
                } else {
                    where.append(prefix + " :" + id++);
                    if (option == FilterOption.BETWEEN) {
                        where.append(" and :" + id++);
                    }
                }
                if (checkErrorFlag) {
                    where.append(")");
                }
            }
        }
        if (where.length() > 0) {
            where.insert(0, " where ");
        }
        return where.toString();
    }

    protected Map<String, Object> buildBatchParams(List<FilterCriterion> filter) {
        Map<String, Object> params = new HashMap<String, Object>();
        int id = 0;
        for (FilterCriterion criterion : filter) {
            FilterOption option = criterion.getOption();
            List<Object> values = criterion.getValues();
            switch (criterion.getPropertyId()) {
                case "status":
                    List<String> statuses = new ArrayList<String>();
                    for (Object status : values) {
                        statuses.add(Status.getNameFromDescription((String) status));
                    }
                    params.put(String.valueOf(id++), statuses);
                    break;
                case "createTime":
                case "lastUpdatedTime":
                    if (option == FilterOption.IN_LIST || option == FilterOption.NOT_IN_LIST) {
                        params.put(String.valueOf(id++), values);
                    } else {
                        params.put(String.valueOf(id++), new Timestamp(((Date) values.get(0)).getTime()));
                        if (option == FilterOption.BETWEEN) {
                            params.put(String.valueOf(id++), new Timestamp(((Date) values.get(1)).getTime()));
                        }
                    }
                    break;
                default:
                    if (option == FilterOption.IN_LIST || option == FilterOption.NOT_IN_LIST) {
                        params.put(String.valueOf(id++), values);
                    } else if (option == FilterOption.CONTAINS || option == FilterOption.NOT_CONTAINS) {
                        params.put(String.valueOf(id++), "%" + values.get(0) + "%");
                    } else {
                        params.put(String.valueOf(id++), values.get(0));
                        if (option == FilterOption.BETWEEN) {
                            params.put(String.valueOf(id++), values.get(1));
                        }
                    }
            }
        }
        return params;
    }

    protected String buildBatchOrderBy(String orderColumn, String orderDirection) {
        String orderBy = " order by ";
        if (orderColumn == null) {
            if (this instanceof OutgoingBatchService) {
                orderBy += "batch_id desc, node_id desc";
            } else {
                orderBy += "create_time desc";
            }
        } else {
            if (orderColumn.equals("lastUpdatedTime")) {
                orderColumn = "last_update_time";
            } else if (orderColumn.equals("lastUpdatedHostName")) {
                orderColumn = "last_update_hostname";
            } else {
                orderColumn = orderColumn.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
            }
            orderBy += orderColumn;
            if (orderDirection.equals("DESCENDING")) {
                orderBy += " desc";
            }
        }
        return orderBy;
    }

    /**
     * Try a configured number of times to get the ACK through.
     */
    protected boolean sendAck(Node remote, Node local, NodeSecurity localSecurity,
            List<IncomingBatch> list, ITransportManager transportManager, String queue) throws IOException {
        assertNotNull(remote, "Node remote cannot be null. Maybe there is a missing sym_node row.");
        assertNotNull(local, "Node local cannot be null. Maybe there is a missing sym_node row.");
        assertNotNull(localSecurity, "NodeSecurity localSecurity cannot be null. Maybe there is a missing sym_node_security row.");
        Exception exception = null;
        int statusCode = -1;
        int numberOfStatusSendRetries = parameterService.getInt(ParameterConstants.DATA_LOADER_NUM_OF_ACK_RETRIES);
        boolean success = true;
        for (int i = 0; i < numberOfStatusSendRetries && statusCode != WebConstants.SC_OK; i++) {
            try {
                Map<String, String> requestProperties = null;
                if (StringUtils.isNotBlank(queue)) {
                    requestProperties = new HashMap<String, String>();
                    requestProperties.put(WebConstants.CHANNEL_QUEUE, queue);
                }
                statusCode = transportManager.sendAcknowledgement(remote, list, local,
                        localSecurity.getNodePassword(), requestProperties, parameterService.getRegistrationUrl());
                exception = null;
            } catch (Exception e) {
                success = false;
                exception = e;
            }
            if (statusCode == WebConstants.SC_OK) {
                success = true;
            } else {
                success = false;
                String httpMessage = WebConstants.getHttpMessage(statusCode);
                boolean retry = statusCode != WebConstants.REGISTRATION_REQUIRED && statusCode != WebConstants.REGISTRATION_PENDING &&
                        statusCode != WebConstants.SYNC_DISABLED && statusCode != WebConstants.SC_FORBIDDEN && statusCode != WebConstants.SC_AUTH_EXPIRED;
                String message = String.format("Ack was not sent successfully on try number %s of %s.", i + 1, numberOfStatusSendRetries);
                if (statusCode > 0) {
                    message += " httpCode=" + statusCode;
                }
                if (httpMessage != null) {
                    message += " httpMessage=" + httpMessage;
                }
                if (exception != null) {
                    log.info(message + " " + exception.getClass().getName() + ": " + exception.getMessage());
                } else {
                    log.info(message);
                }
                if (!retry) {
                    log.info("Leaving ack loop to recover from HTTP code that should not be retried");
                }
                if (retry && i < numberOfStatusSendRetries - 1) {
                    AppUtils.sleep(parameterService.getLong(ParameterConstants.DATA_LOADER_TIME_BETWEEN_ACK_RETRIES));
                } else {
                    log.warn("Ack was not sent successfully.");
                    if (!retry) {
                        break;
                    }
                }
            }
        }
        return success;
    }

    protected List<BatchAck> readAcks(List<OutgoingBatch> batches, String nodeId, IOutgoingWithResponseTransport transport,
            ITransportManager transportManager, IAcknowledgeService acknowledgeService, IDataExtractorService dataExtratorService)
            throws IOException {
        Set<Long> batchIds = new HashSet<Long>(batches.size());
        for (OutgoingBatch outgoingBatch : batches) {
            if (outgoingBatch.getStatus() == OutgoingBatch.Status.LD) {
                batchIds.add(outgoingBatch.getBatchId());
            }
        }
        BufferedReader reader = transport.readResponse();
        String ackString = reader.readLine();
        String ackExtendedString = reader.readLine();
        log.debug("Reading ack: {}", ackString);
        log.debug("Reading extend ack: {}", ackExtendedString);
        String line = null;
        do {
            line = reader.readLine();
            if (line != null) {
                log.info("Read another unexpected line {}", line);
            }
        } while (line != null);
        List<BatchAck> batchAcks = transportManager.readAcknowledgement(ackString,
                ackExtendedString);
        Collections.sort(batchAcks, BATCH_ID_COMPARATOR);
        long batchIdInError = Long.MAX_VALUE;
        for (BatchAck batchInfo : batchAcks) {
            batchIds.remove(batchInfo.getBatchId());
            if (!batchInfo.isOk()) {
                batchIdInError = batchInfo.getBatchId();
            }
            log.debug("Saving ack: {}, {}", batchInfo.getBatchId(), (batchInfo.isResend() ? "RS" : batchInfo.isOk() ? "OK" : "ER"));
            acknowledgeService.ack(batchInfo);
        }
        boolean isMissingAck = false;
        for (Long batchId : batchIds) {
            if (batchId < batchIdInError) {
                for (OutgoingBatch outgoingBatch : batches) {
                    if (outgoingBatch.getBatchId() == batchId) {
                        StringBuilder message = new StringBuilder(128);
                        message.append("Expected but did not receive an ack for batch ");
                        message.append(outgoingBatch.getNodeBatchId()).append(". ");
                        if (dataExtratorService != null) {
                            if (!outgoingBatch.isLoadFlag()) {
                                message.append("This could be because the batch is corrupt - removing the batch from staging.");
                                log.warn(message.toString());
                                dataExtratorService.removeBatchFromStaging(outgoingBatch);
                            } else {
                                message.append(
                                        "This could be because the batch is corrupt. Not removing the batch because it was a load batch, but you may need to clear the batch from staging manually.");
                                log.warn(message.toString());
                            }
                        } else {
                            log.warn(message.toString());
                        }
                        isMissingAck = true;
                    }
                }
            }
        }
        if (isMissingAck && dataExtratorService != null) {
            dataExtratorService.incrementBackOffCount(nodeId);
        }
        return batchAcks;
    }

    protected void logOnce(String message) {
        if (logOnce.add(message)) {
            log.info(message);
        }
    }

    protected boolean isStreamClosedByClient(Exception ex) {
        if (ExceptionUtils.indexOfType(ex, EOFException.class) >= 0) {
            return true;
        } else {
            return false;
        }
    }

    protected void assertNotNull(Object o, String message) {
        if (o == null) {
            throw new SymmetricException(message);
        }
    }

    protected ISymmetricDialect getTargetDialect() {
        return symmetricDialect.getTargetDialect();
    }

    protected boolean isSymmetricTable(String tableName) {
        return tableName.toUpperCase().startsWith(this.tablePrefix.toUpperCase());
    }

    protected IDatabasePlatform getTargetPlatform(String tableName) {
        return isSymmetricTable(tableName) ? symmetricDialect.getPlatform() : symmetricDialect.getTargetDialect().getPlatform();
    }

    protected IDatabasePlatform getTargetPlatform() {
        return symmetricDialect.getTargetDialect().getPlatform();
    }
}