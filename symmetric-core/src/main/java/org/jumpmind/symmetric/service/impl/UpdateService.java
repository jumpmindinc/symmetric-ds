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
package org.jumpmind.symmetric.service.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ContextConstants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeHost;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IUpdateService;
import org.jumpmind.util.AppUtils;
import org.slf4j.MDC;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

public class UpdateService extends AbstractService implements IUpdateService {
    protected final long MILLIS_BETWEEN_CHECKS = 86400000l;
    protected final long MILLIS_AFTER_NODE_OFFLINE = 86400000l;
    protected ISymmetricEngine engine;
    protected boolean sendUsage;
    protected boolean checkUpdates;
    protected String latestVersion;
    protected String downloadUrl;
    protected Thread sleepThread;
    protected boolean stopped = false;

    public UpdateService(ISymmetricEngine engine) {
        super(engine.getParameterService(), engine.getSymmetricDialect());
        this.engine = engine;
        setSqlMap(new UpdateServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
    }

    public void init() {
        sendUsage = parameterService.is(ParameterConstants.SEND_USAGE_STATS, true);
        checkUpdates = parameterService.is(ParameterConstants.CHECK_SOFTWARE_UPDATES, true);
        if (sendUsage || checkUpdates) {
            sleepThread = new Thread() {
                public void run() {
                    MDC.put("engineName", engine.getEngineName());
                    log.debug("Starting thread to check for updates, running every {} millis", MILLIS_BETWEEN_CHECKS);
                    while (!stopped) {
                        try {
                            Thread.sleep(MILLIS_BETWEEN_CHECKS);
                            checkForUpdates();
                        } catch (InterruptedException e) {
                            log.debug("Stopping check update thread");
                        } catch (Exception e) {
                            log.debug("Failed to check updates", e);
                        }
                    }
                }
            };
            sleepThread.setDaemon(true);
            sleepThread.start();
        }
    }

    public void checkForUpdates() {
        try {
            log.debug("Starting check for updates");
            Map<String, Object> prop = getProperties();
            if (sendUsage) {
                addUsageProperties(prop);
            }
            byte[] postData = getPostData(prop);
            postDataForVersion(getUpdateUrl(), postData);
            if (checkUpdates && latestVersion != null && Version.isOlderThanVersion(Version.version(), latestVersion)) {
                log.warn("New version of SymmetricDS (" + latestVersion + ") is available for download from " + downloadUrl);
            }
        } catch (MalformedURLException e) {
            log.debug("Failed to obtain URL for checking updates", e);
        } catch (Exception e) {
            log.debug("Failed to communicate for checking updates", e);
        } finally {
            log.debug("Ending check for updates");
        }
    }

    protected byte[] getPostData(Map<String, Object> prop) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        for (Object key : prop.keySet()) {
            Object value = prop.get(key);
            sb.append(URLEncoder.encode(key.toString(), StandardCharsets.UTF_8.name())).append("=");
            sb.append(URLEncoder.encode(value == null ? "" : value.toString(), StandardCharsets.UTF_8.name())).append("&");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    protected void postDataForVersion(URL url, byte[] postData) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(postData.length);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.connect();
        try (OutputStream os = conn.getOutputStream()) {
            os.write(postData);
        }
        parseHeaders(conn);
        parseResponse(conn);
        conn.disconnect();
    }

    protected void parseHeaders(HttpURLConnection conn) {
    }

    protected void parseResponse(HttpURLConnection conn) throws IOException {
        Gson gson = new Gson();
        JsonReader reader = gson.newJsonReader(new InputStreamReader(conn.getInputStream()));
        String fieldName = "";
        while (reader.hasNext()) {
            JsonToken jsonToken = reader.peek();
            if (jsonToken == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
            } else if (jsonToken == JsonToken.NAME) {
                fieldName = reader.nextName();
            } else if (jsonToken == JsonToken.STRING) {
                String value = reader.nextString();
                if ("version".equals(fieldName)) {
                    latestVersion = value;
                } else if ("link".equals(fieldName)) {
                    downloadUrl = value;
                }
            } else if (jsonToken == JsonToken.NUMBER) {
                reader.nextLong();
            }
        }
        reader.close();
    }

    protected Map<String, Object> getProperties() {
        Map<String, Object> prop = new HashMap<String, Object>();
        IContextService contextService = engine.getContextService();
        String guid = contextService.getString(ContextConstants.GUID);
        if (guid == null) {
            guid = UUID.randomUUID().toString();
            contextService.save(ContextConstants.GUID, guid);
        }
        prop.put(ContextConstants.GUID, guid.replace("-", ""));
        prop.put("time_zone", AppUtils.getTimezoneOffset());
        prop.put("version", Version.version());
        return prop;
    }

    protected void addUsageProperties(Map<String, Object> prop) {
        Node node = engine.getNodeService().findIdentity();
        prop.put("node_id", node.getNodeId());
        prop.put("node_group_id", node.getNodeGroupId());
        prop.put("hostname", AppUtils.getHostName());
        prop.put("ip_address", AppUtils.getIpAddress());
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        prop.put("os_processors", String.valueOf(osBean.getAvailableProcessors()));
        prop.put("os_name", System.getProperty("os.name"));
        prop.put("os_arch", System.getProperty("os.arch"));
        prop.put("os_version", System.getProperty("os.version"));
        prop.put("jvm_title", Runtime.class.getPackage().getImplementationTitle());
        prop.put("jvm_vendor", Runtime.class.getPackage().getImplementationVendor());
        prop.put("jvm_version", Runtime.class.getPackage().getImplementationVersion());
        prop.put("jvm_memory", String.valueOf(Runtime.getRuntime().maxMemory()));
        prop.put("nodes", engine.getNodeService().findAllNodeSecurity(true).size());
        int clusterNodeCount = 0;
        Date nodeCreateDate = null;
        boolean isClustered = engine.getParameterService().is(ParameterConstants.CLUSTER_LOCKING_ENABLED);
        long offlineTime = System.currentTimeMillis() - MILLIS_AFTER_NODE_OFFLINE;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        for (NodeHost host : engine.getNodeService().findNodeHosts(node.getNodeId())) {
            if (isClustered && host.getHeartbeatTime() != null && host.getHeartbeatTime().getTime() >= offlineTime) {
                clusterNodeCount++;
            }
            if (host.getCreateTime() != null && (nodeCreateDate == null || host.getCreateTime().before(nodeCreateDate))) {
                nodeCreateDate = host.getCreateTime();
            }
        }
        prop.put("cluster.nodes", clusterNodeCount);
        prop.put("node_create_date", nodeCreateDate == null ? "" : dateFormat.format(nodeCreateDate));
        prop.put("node_groups", engine.getConfigurationService().getNodeGroups().size());
        Map<String, Channel> channels = engine.getConfigurationService().getChannels(false);
        int channelCount = 0;
        int advChannelCount = 0;
        for (String name : channels.keySet()) {
            if (!(name.equals("config") || name.equals("dynamic") || name.equals("filesync") || name.equals("filesync_reload")
                    || name.equals("heartbeat") || name.equals("reload"))) {
                channelCount++;
            }
            Channel channel = channels.get(name);
            if (!channel.getDataLoaderType().equals("default")) {
                advChannelCount++;
            }
        }
        prop.put("channels", channelCount);
        prop.put("channels_advanced", advChannelCount);
        engine.getConfigurationService().getNodeGroupLinks(false).size();
        List<Router> routers = engine.getTriggerRouterService().getRouters(false);
        int advRouterCount = 0;
        for (Router router : routers) {
            if (!router.getRouterType().equals("default")) {
                advRouterCount++;
            }
        }
        prop.put("routers", routers.size());
        prop.put("routers_advanced", advRouterCount);
        prop.put("triggers", engine.getTriggerRouterService().getTriggers().size());
        prop.put("trigger_routers", engine.getTriggerRouterService().getTriggerRouters(false).size());
        int tableCount = 0;
        int columnCount = 0;
        for (TriggerHistory hist : engine.getTriggerRouterService().getActiveTriggerHistoriesFromCache()) {
            if (!hist.getSourceTableName().startsWith(parameterService.getTablePrefix())) {
                tableCount++;
                columnCount += hist.getParsedColumnNames().length;
            }
        }
        prop.put("tables", tableCount);
        prop.put("table_columns", columnCount);
        prop.put("file_triggers", engine.getFileSyncService().getFileTriggers().size());
        prop.put("conflicts", engine.getDataLoaderService().getConflictSettingsNodeGroupLinks().size());
        prop.put("transforms", engine.getTransformService().getTransformTables(false).size());
        prop.put("load_filters", engine.getLoadFilterService().getLoadFilterNodeGroupLinks().size());
        prop.put("extensions", engine.getExtensionService().getExtensions().size());
        prop.put("db_type", symmetricDialect.getName());
        prop.put("db_version", symmetricDialect.getVersion());
        long mobileNodeCount = 0;
        Set<String> databaseTypes = new HashSet<String>();
        for (Node clientNode : engine.getNodeService().findAllNodes()) {
            String databaseType = clientNode.getDatabaseType() + " " + clientNode.getDatabaseVersion();
            if (databaseTypes.add(databaseType)) {
                prop.put("db_type_" + databaseTypes.size(), clientNode.getDatabaseType());
                prop.put("db_version_" + databaseTypes.size(), clientNode.getDatabaseVersion());
                if (databaseTypes.size() == 10) {
                    break;
                }
            }
            String deployType = clientNode.getDeploymentType();
            if (deployType.equals("android") || deployType.equals(Constants.DEPLOYMENT_TYPE_CCLIENT)) {
                mobileNodeCount++;
            }
        }
        prop.put("mobile_nodes", mobileNodeCount);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -1);
        Map<String, Object> outMap = sqlTemplateDirty.queryForMap(getSql("countOutgoing"), cal.getTime());
        prop.put("out_batches", outMap.get("batch_count"));
        prop.put("out_bytes", outMap.get("byte_count") == null ? 0L : outMap.get("byte_count"));
        prop.put("out_rows", outMap.get("rows_count") == null ? 0L : outMap.get("rows_count"));
        Map<String, Object> inMap = sqlTemplateDirty.queryForMap(getSql("countIncoming"), cal.getTime());
        prop.put("in_batches", outMap.get("batch_count"));
        prop.put("in_bytes", inMap.get("byte_count") == null ? 0L : inMap.get("byte_count"));
        prop.put("in_rows", inMap.get("rows_count") == null ? 0L : inMap.get("rows_count"));
    }

    protected URL getUpdateUrl() throws MalformedURLException {
        return URI.create("https://status.symmetricds.org/api/getlatest.php").toURL();
    }

    public boolean isNewVersionAvailable() {
        return latestVersion != null && Version.isOlderThanVersion(Version.version(), latestVersion);
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void stop() {
        stopped = true;
        if (sleepThread != null) {
            sleepThread.interrupt();
        }
    }
}
