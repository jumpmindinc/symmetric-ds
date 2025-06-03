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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class AbstractTransportManager {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final int FORM_KEYS_PER_BATCH = 24;
    protected IExtensionService extensionService;

    public AbstractTransportManager() {
    }

    public AbstractTransportManager(IExtensionService extensionService) {
        this.extensionService = extensionService;
    }

    /**
     * Build the url for remote node communication. Use the remote sync_url first, if it is null or blank, then use the registration url instead.
     */
    public String resolveURL(String syncUrl, String registrationUrl) {
        if (StringUtils.isBlank(syncUrl) || syncUrl.startsWith(Constants.PROTOCOL_NONE)) {
            log.debug("Using the registration URL to contact the remote node because the syncURL for the node is blank");
            return registrationUrl;
        }
        try {
            URI uri = new URI(syncUrl);
            for (ISyncUrlExtension handler : extensionService.getExtensionPointList(ISyncUrlExtension.class)) {
                syncUrl = handler.resolveUrl(uri);
                uri = new URI(syncUrl);
            }
        } catch (URISyntaxException e) {
            log.error(e.getMessage(), e);
        }
        return syncUrl;
    }

    protected List<String> getAcknowledgementData(boolean requires13Format, String nodeId, List<IncomingBatch> list,
            int maxFormKeys, int maxByteSize) throws IOException {
        List<String> ackDataList = new ArrayList<String>();
        StringBuilder builder = new StringBuilder();
        int keyCount = 0;
        if (maxFormKeys > 0) {
            maxFormKeys = Math.max(maxFormKeys, FORM_KEYS_PER_BATCH);
        }
        for (IncomingBatch batch : list) {
            long batchId = batch.getBatchId();
            Object value = batch.getStatus() == Status.OK ? WebConstants.ACK_BATCH_OK : batch.getFailedRowNumber();
            StringBuilder localBuilder = new StringBuilder();
            int localKeyCount;
            if (!requires13Format) {
                if (batch.getStatus() == Status.RS) {
                    value = WebConstants.ACK_BATCH_RESEND;
                }
                append(localBuilder, WebConstants.ACK_BATCH_NAME + batchId, value);
                append(localBuilder, WebConstants.ACK_NODE_ID + batchId, nodeId);
                append(localBuilder, WebConstants.ACK_NETWORK_MILLIS + batchId, batch.getNetworkMillis());
                append(localBuilder, WebConstants.ACK_FILTER_MILLIS + batchId, batch.getFilterMillis());
                append(localBuilder, WebConstants.ACK_DATABASE_MILLIS + batchId, batch.getLoadMillis());
                append(localBuilder, WebConstants.ACK_START_TIME + batchId, batch.getStartTime());
                append(localBuilder, WebConstants.ACK_BYTE_COUNT + batchId, batch.getByteCount());
                append(localBuilder, WebConstants.ACK_LOAD_ROW_COUNT + batchId, batch.getLoadRowCount());
                append(localBuilder, WebConstants.TRANSFORM_TIME + batchId, batch.getTransformLoadMillis());
                append(localBuilder, WebConstants.ACK_LOAD_INSERT_ROW_COUNT + batchId, batch.getLoadInsertRowCount());
                append(localBuilder, WebConstants.ACK_LOAD_UPDATE_ROW_COUNT + batchId, batch.getLoadUpdateRowCount());
                append(localBuilder, WebConstants.ACK_LOAD_DELETE_ROW_COUNT + batchId, batch.getLoadDeleteRowCount());
                append(localBuilder, WebConstants.ACK_FALLBACK_INSERT_COUNT + batchId, batch.getFallbackInsertCount());
                append(localBuilder, WebConstants.ACK_FALLBACK_UPDATE_COUNT + batchId, batch.getFallbackUpdateCount());
                append(localBuilder, WebConstants.ACK_CONFLICT_WIN_COUNT + batchId, batch.getConflictWinCount());
                append(localBuilder, WebConstants.ACK_CONFLICT_LOSE_COUNT + batchId, batch.getConflictLoseCount());
                append(localBuilder, WebConstants.ACK_IGNORE_ROW_COUNT + batchId, batch.getIgnoreRowCount());
                append(localBuilder, WebConstants.ACK_MISSING_DELETE_COUNT + batchId, batch.getMissingDeleteCount());
                append(localBuilder, WebConstants.ACK_SKIP_COUNT + batchId, batch.getSkipCount());
                append(localBuilder, WebConstants.ACK_BULK_LOADER_FLAG + batchId, batch.isBulkLoaderFlag());
                localKeyCount = 20;
                if (batch.getIgnoreCount() > 0) {
                    append(localBuilder, WebConstants.ACK_IGNORE_COUNT + batchId, batch.getIgnoreCount());
                    localKeyCount++;
                }
                if (batch.getStatus() == Status.ER) {
                    String sqlState = batch.getSqlState();
                    if (sqlState != null && sqlState.length() > 10) {
                        sqlState = sqlState.replace("JDBC-", "");
                        if (sqlState.length() > 10) {
                            sqlState = sqlState.substring(0, 10);
                        }
                    }
                    append(localBuilder, WebConstants.ACK_SQL_STATE + batchId, sqlState);
                    append(localBuilder, WebConstants.ACK_SQL_CODE + batchId, batch.getSqlCode());
                    append(localBuilder, WebConstants.ACK_SQL_MESSAGE + batchId, batch.getSqlMessage());
                    localKeyCount += 3;
                }
            } else {
                if (batch.getStatus() == Status.IG) {
                    value = WebConstants.ACK_BATCH_OK;
                }
                append(localBuilder, WebConstants.ACK_BATCH_NAME + batchId, value);
                localKeyCount = 1;
            }
            if ((maxByteSize > 0 && builder.length() + localBuilder.length() > maxByteSize)
                    || (maxFormKeys > 0 && keyCount + localKeyCount > maxFormKeys)) {
                ackDataList.add(builder.toString());
                builder = new StringBuilder();
                keyCount = 0;
            } else if (keyCount > 0) {
                builder.append("&");
            }
            builder.append(localBuilder);
            keyCount += localKeyCount;
        }
        ackDataList.add(builder.toString());
        return ackDataList;
    }

    protected static void append(StringBuilder builder, String name, Object value) {
        try {
            int len = builder.length();
            if (len > 0 && builder.charAt(len - 1) != '?') {
                builder.append("&");
            }
            if (value == null) {
                value = "";
            }
            builder.append(name).append("=").append(URLEncoder.encode(value.toString(), StandardCharsets.UTF_8.name()));
        } catch (IOException ex) {
            throw new IoException(ex);
        }
    }

    public List<BatchAck> readAcknowledgement(String parameterString1, String parameterString2) throws IOException {
        return readAcknowledgement(parameterString1 + "&" + parameterString2);
    }

    public List<BatchAck> readAcknowledgement(String parameterString) throws IOException {
        Map<String, String> parameters = getParametersFromQueryUrl(parameterString.replace("\n", ""));
        return readAcknowledgement(parameters);
    }

    public static List<BatchAck> readAcknowledgement(Map<String, ? extends Object> parameters) {
        List<BatchAck> batches = new ArrayList<BatchAck>();
        for (String parameterName : parameters.keySet()) {
            if (parameterName.startsWith(WebConstants.ACK_BATCH_NAME)) {
                long batchId = NumberUtils.toLong(parameterName.substring(WebConstants.ACK_BATCH_NAME.length()));
                BatchAck batchInfo = getBatchInfo(parameters, batchId);
                batches.add(batchInfo);
            }
        }
        return batches;
    }

    public void writeRequestProperties(Map<String, String> requestProperties, OutputStream os) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
            if (sb.length() != 0) {
                sb.append("&");
            }
            sb.append(requestProperty.getKey()).append("=");
            if (requestProperty.getValue() != null && !requestProperty.getValue().equals("")) {
                sb.append(URLEncoder.encode(requestProperty.getValue(), StandardCharsets.UTF_8.name()));
            }
        }
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
        pw.println(sb.toString());
        pw.flush();
    }

    public Map<String, String> readRequestProperties(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String parameterString = reader.readLine();
        return getParametersFromQueryUrl(parameterString);
    }

    private static BatchAck getBatchInfo(Map<String, ? extends Object> parameters, long batchId) {
        BatchAck batchInfo = new BatchAck(batchId);
        String nodeId = getParam(parameters, WebConstants.ACK_NODE_ID + batchId);
        if (StringUtils.isBlank(nodeId)) {
            nodeId = getParam(parameters, WebConstants.NODE_ID);
        }
        batchInfo.setNodeId(nodeId);
        batchInfo.setNetworkMillis(getParamAsNum(parameters, WebConstants.ACK_NETWORK_MILLIS + batchId));
        batchInfo.setFilterMillis(getParamAsNum(parameters, WebConstants.ACK_FILTER_MILLIS + batchId));
        batchInfo.setLoadMillis(getParamAsNum(parameters, WebConstants.ACK_DATABASE_MILLIS + batchId));
        batchInfo.setByteCount(getParamAsNum(parameters, WebConstants.ACK_BYTE_COUNT + batchId));
        batchInfo.setLoadRowCount(getParamAsNum(parameters, WebConstants.ACK_LOAD_ROW_COUNT + batchId));
        batchInfo.setTransformLoadMillis(getParamAsNum(parameters, WebConstants.TRANSFORM_TIME + batchId));
        batchInfo.setLoadInsertRowCount(getParamAsNum(parameters, WebConstants.ACK_LOAD_INSERT_ROW_COUNT + batchId));
        batchInfo.setLoadUpdateRowCount(getParamAsNum(parameters, WebConstants.ACK_LOAD_UPDATE_ROW_COUNT + batchId));
        batchInfo.setLoadDeleteRowCount(getParamAsNum(parameters, WebConstants.ACK_LOAD_DELETE_ROW_COUNT + batchId));
        batchInfo.setFallbackInsertCount(getParamAsNum(parameters, WebConstants.ACK_FALLBACK_INSERT_COUNT + batchId));
        batchInfo.setFallbackUpdateCount(getParamAsNum(parameters, WebConstants.ACK_FALLBACK_UPDATE_COUNT + batchId));
        batchInfo.setConflictWinCount(getParamAsNum(parameters, WebConstants.ACK_CONFLICT_WIN_COUNT + batchId));
        batchInfo.setConflictLoseCount(getParamAsNum(parameters, WebConstants.ACK_CONFLICT_LOSE_COUNT + batchId));
        batchInfo.setIgnoreRowCount(getParamAsNum(parameters, WebConstants.ACK_IGNORE_ROW_COUNT + batchId));
        batchInfo.setMissingDeleteCount(getParamAsNum(parameters, WebConstants.ACK_MISSING_DELETE_COUNT + batchId));
        batchInfo.setSkipCount(getParamAsNum(parameters, WebConstants.ACK_SKIP_COUNT + batchId));
        batchInfo.setIgnored(getParamAsBoolean(parameters, WebConstants.ACK_IGNORE_COUNT + batchId));
        batchInfo.setBulkLoaderFlag(Boolean.parseBoolean(getParam(parameters, WebConstants.ACK_BULK_LOADER_FLAG + batchId)));
        String status = getParam(parameters, WebConstants.ACK_BATCH_NAME + batchId, "").trim();
        batchInfo.setOk(status.equalsIgnoreCase(WebConstants.ACK_BATCH_OK) || status.equalsIgnoreCase(WebConstants.ACK_BATCH_RESEND));
        batchInfo.setResend(status.equalsIgnoreCase(WebConstants.ACK_BATCH_RESEND));
        batchInfo.setStartTime(getParamAsNum(parameters, WebConstants.ACK_START_TIME + batchId));
        if (!batchInfo.isOk()) {
            batchInfo.setErrorLine(NumberUtils.toLong(status));
            batchInfo.setSqlState(getParam(parameters, WebConstants.ACK_SQL_STATE + batchId));
            batchInfo.setSqlCode((int) getParamAsNum(parameters, WebConstants.ACK_SQL_CODE + batchId));
            batchInfo.setSqlMessage(getParam(parameters, WebConstants.ACK_SQL_MESSAGE + batchId));
        }
        return batchInfo;
    }

    protected Map<String, String> getParametersFromQueryUrl(String parameterString) throws IOException {
        Map<String, String> parameters = new HashMap<String, String>();
        String[] tokens = parameterString.split("&");
        for (String param : tokens) {
            String[] nameValuePair = param.split("=");
            if (nameValuePair.length == 2) {
                parameters.put(nameValuePair[0], URLDecoder.decode(nameValuePair[1], StandardCharsets.UTF_8.name()));
            }
        }
        return parameters;
    }

    private static long getParamAsNum(Map<String, ? extends Object> parameters, String parameterName) {
        return NumberUtils.toLong(getParam(parameters, parameterName));
    }

    private static boolean getParamAsBoolean(Map<String, ? extends Object> parameters, String parameterName) {
        return getParamAsNum(parameters, parameterName) > 0;
    }

    private static String getParam(Map<String, ? extends Object> parameters, String parameterName, String defaultValue) {
        String value = getParam(parameters, parameterName);
        return value == null ? defaultValue : value;
    }

    private static String getParam(Map<String, ? extends Object> parameters, String parameterName) {
        Object value = parameters.get(parameterName);
        if (value instanceof String[]) {
            String[] arrayValue = (String[]) value;
            if (arrayValue.length > 0) {
                value = StringUtils.trim(arrayValue[0]);
            }
        }
        return (String) value;
    }
}
