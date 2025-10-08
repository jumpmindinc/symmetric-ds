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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.Row;
import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.cache.ICacheManager;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.ChannelMap;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeChannel;
import org.jumpmind.symmetric.model.NodeGroup;
import org.jumpmind.symmetric.model.NodeGroupChannelWindow;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;

/**
 * @see IConfigurationService
 */
public class ConfigurationService extends AbstractService implements IConfigurationService {
    private INodeService nodeService;
    private Map<String, Channel> defaultChannels;
    private Date lastUpdateTime;
    private ICacheManager cacheManager;

    public ConfigurationService(ISymmetricEngine engine, ISymmetricDialect dialect) {
        super(engine.getParameterService(), dialect);
        this.nodeService = engine.getNodeService();
        this.cacheManager = engine.getCacheManager();
        createDefaultChannels();
        setSqlMap(new ConfigurationServiceSqlMap(symmetricDialect.getPlatform(),
                createSqlReplacementTokens()));
    }

    protected final void createDefaultChannels() {
        Map<String, Channel> updatedDefaultChannels = new LinkedHashMap<String, Channel>();
        updatedDefaultChannels.put(Constants.CHANNEL_CONFIG, new Channel(Constants.CHANNEL_CONFIG, 0, 2000, 10, true, 0, true, Constants.QUEUE_SYSTEM));
        updatedDefaultChannels.put(Constants.CHANNEL_SYSTEM, new Channel(Constants.CHANNEL_SYSTEM, 0, 2000, 10, true, 0, true, Constants.QUEUE_SYSTEM));
        Channel reloadChannel;
        if (parameterService.is(ParameterConstants.INITIAL_LOAD_USE_EXTRACT_JOB)) {
            reloadChannel = new Channel(Constants.CHANNEL_RELOAD, 1, 500000, 10, true, 0, false, true, false);
            reloadChannel.setQueue(Constants.QUEUE_RELOAD);
        } else {
            reloadChannel = new Channel(Constants.CHANNEL_RELOAD, 1, 1, 1, true, 0, false, true, false);
        }
        reloadChannel.setDataLoaderType("bulk");
        updatedDefaultChannels.put(Constants.CHANNEL_RELOAD, reloadChannel);
        updatedDefaultChannels.put(Constants.CHANNEL_MONITOR, new Channel(Constants.CHANNEL_MONITOR, 2, 100, 10, true, 0, true, Constants.QUEUE_SYSTEM));
        updatedDefaultChannels.put(Constants.CHANNEL_HEARTBEAT, new Channel(Constants.CHANNEL_HEARTBEAT, 2, 100, 10, true, 0, false, Constants.QUEUE_SYSTEM));
        updatedDefaultChannels.put(Constants.CHANNEL_DEFAULT, new Channel(Constants.CHANNEL_DEFAULT, 500000, 1000, 10, true, 0, false));
        updatedDefaultChannels.put(Constants.CHANNEL_DYNAMIC, new Channel(Constants.CHANNEL_DYNAMIC, 99999, 1000, 10, true, 0, false));
        if (parameterService.is(ParameterConstants.FILE_SYNC_ENABLE)) {
            updatedDefaultChannels.put(Constants.CHANNEL_FILESYNC,
                    new Channel(Constants.CHANNEL_FILESYNC, 3, 100, 10, true, 0, false, "nontransactional", false, true));
            updatedDefaultChannels.put(Constants.CHANNEL_FILESYNC_RELOAD,
                    new Channel(Constants.CHANNEL_FILESYNC_RELOAD, 1, 100, 10, true, 0, false, "nontransactional", true, true));
        }
        this.defaultChannels = updatedDefaultChannels;
    }

    @Override
    public boolean isBulkLoaderEnabled() {
        boolean enabled = false;
        for (Channel channel : getChannels(false).values()) {
            if (channel.isReloadFlag() && channel.getDataLoaderType().equals("bulk")) {
                enabled = true;
                break;
            }
        }
        return enabled;
    }

    @Override
    public boolean isMasterToMaster() {
        boolean masterToMaster = false;
        Node me = nodeService.findIdentity();
        if (me != null) {
            NodeGroupLink nodeGroupLink = getNodeGroupLinkFor(me.getNodeGroupId(), me.getNodeGroupId(), false);
            masterToMaster = nodeGroupLink != null && nodeGroupLink.getDataEventAction() != NodeGroupLinkAction.R;
        }
        return masterToMaster;
    }

    @Override
    public boolean containsMasterToMaster() {
        boolean masterToMasterOnly = false;
        Node me = nodeService.findIdentity();
        if (me != null) {
            masterToMasterOnly = sqlTemplate.queryForInt(getSql("countGroupLinksForSql"), me.getNodeGroupId(), me.getNodeGroupId(),
                    NodeGroupLinkAction.R.name()) == 1;
        }
        return masterToMasterOnly;
    }

    @Override
    public boolean isMasterToMasterOnly() {
        Node me = nodeService.findIdentity();
        int masterCount = 0;
        int otherCount = 0;
        if (me != null) {
            for (NodeGroupLink nodeGroupLink : getNodeGroupLinksFor(me.getNodeGroupId(), false)) {
                if (nodeGroupLink.getTargetNodeGroupId().equals(me.getNodeGroupId()) &&
                        nodeGroupLink.getDataEventAction() != NodeGroupLinkAction.R) {
                    masterCount++;
                } else {
                    otherCount++;
                }
            }
        }
        return masterCount >= 1 && otherCount == 0;
    }

    @Override
    public boolean isUseSourceStagingEnabled(String nodeId) {
        if (isUseSourceStagingEnabled(parameterService)) {
            ISymmetricEngine targetEngine = AbstractSymmetricEngine.findEngineByNodeId(nodeId);
            return targetEngine != null && isUseSourceStagingEnabled(targetEngine.getParameterService());
        }
        return false;
    }

    private boolean isUseSourceStagingEnabled(IParameterService parameterService) {
        return parameterService.is(ParameterConstants.INCOMING_BATCHES_USE_SOURCE_STAGING) && !parameterService.is(ParameterConstants.NODE_OFFLINE)
                && parameterService.is(ParameterConstants.STREAM_TO_FILE_ENABLED) && (!parameterService.is(ParameterConstants.CLUSTER_LOCKING_ENABLED)
                        || parameterService.is(ParameterConstants.CLUSTER_STAGING_ENABLED));
    }

    @Override
    public boolean refreshFromDatabase() {
        Date date1 = sqlTemplate.queryForObject(getSql("selectMaxChannelLastUpdateTime"),
                Date.class);
        Date date2 = sqlTemplate.queryForObject(getSql("selectMaxNodeGroupLastUpdateTime"),
                Date.class);
        Date date3 = sqlTemplate.queryForObject(getSql("selectMaxNodeGroupLinkLastUpdateTime"),
                Date.class);
        Date date = maxDate(date1, date2, date3);
        if (date != null) {
            if (lastUpdateTime == null || lastUpdateTime.before(date)) {
                if (lastUpdateTime != null) {
                    log.info("Newer channel or group settings were detected");
                }
                lastUpdateTime = date;
                clearCache();
                return true;
            }
        }
        return false;
    }

    @Override
    public void saveNodeGroupLink(NodeGroupLink link) {
        if (!doesNodeGroupExist(link.getSourceNodeGroupId())) {
            saveNodeGroup(new NodeGroup(link.getSourceNodeGroupId()));
        }
        if (!doesNodeGroupExist(link.getTargetNodeGroupId())) {
            saveNodeGroup(new NodeGroup(link.getTargetNodeGroupId()));
        }
        link.setLastUpdateTime(new Date());
        if (sqlTemplate.update(getSql("updateNodeGroupLinkSql"), link.getDataEventAction().name(),
                link.isSyncConfigEnabled() ? 1 : 0, link.isSyncSqlEnabled() ? 1 : 0, link.isReversible() ? 1 : 0,
                link.getLastUpdateTime(),
                link.getLastUpdateBy(), link.getSourceNodeGroupId(), link.getTargetNodeGroupId()) <= 0) {
            link.setCreateTime(new Date());
            sqlTemplate.update(getSql("insertNodeGroupLinkSql"), link.getDataEventAction().name(),
                    link.getSourceNodeGroupId(), link.getTargetNodeGroupId(),
                    link.isSyncConfigEnabled() ? 1 : 0, link.isSyncSqlEnabled() ? 1 : 0, link.isReversible() ? 1 : 0,
                    link.getLastUpdateTime(),
                    link.getLastUpdateBy(), link.getCreateTime());
        }
        clearCache();
    }

    @Override
    public void renameNodeGroupLink(String oldSourceId, String oldTargetId, NodeGroupLink link) {
        saveNodeGroupLink(link);
        ISqlTransaction transaction = null;
        try {
            boolean sourceChanged = !oldSourceId.equals(link.getSourceNodeGroupId());
            boolean targetChanged = !oldTargetId.equals(link.getTargetNodeGroupId());
            transaction = sqlTemplate.startSqlTransaction();
            if (sourceChanged && targetChanged) {
                transaction.prepareAndExecute(getSql("updateConflictGroupsSql"), link.getSourceNodeGroupId(),
                        link.getTargetNodeGroupId(), oldSourceId, oldTargetId);
                transaction.prepareAndExecute(getSql("updateLoadFilterGroupsSql"), link.getSourceNodeGroupId(),
                        link.getTargetNodeGroupId(), oldSourceId, oldTargetId);
                transaction.prepareAndExecute(getSql("updateRouterGroupsSql"), link.getSourceNodeGroupId(),
                        link.getTargetNodeGroupId(), oldSourceId, oldTargetId);
                transaction.prepareAndExecute(getSql("updateTransformGroupsSql"), link.getSourceNodeGroupId(),
                        link.getTargetNodeGroupId(), oldSourceId, oldTargetId);
            }
            if (sourceChanged) {
                transaction.prepareAndExecute(getSql("updateConflictSourceGroupSql"), link.getSourceNodeGroupId(), oldSourceId);
                transaction.prepareAndExecute(getSql("updateLoadFilterSourceGroupSql"), link.getSourceNodeGroupId(), oldSourceId);
                transaction.prepareAndExecute(getSql("updateRouterSourceGroupSql"), link.getSourceNodeGroupId(), oldSourceId);
                transaction.prepareAndExecute(getSql("updateTransformSourceGroupSql"), link.getSourceNodeGroupId(), oldSourceId);
            }
            if (targetChanged) {
                transaction.prepareAndExecute(getSql("updateConflictTargetGroupSql"), link.getTargetNodeGroupId(), oldTargetId);
                transaction.prepareAndExecute(getSql("updateLoadFilterTargetGroupSql"), link.getTargetNodeGroupId(), oldTargetId);
                transaction.prepareAndExecute(getSql("updateRouterTargetGroupSql"), link.getTargetNodeGroupId(), oldTargetId);
                transaction.prepareAndExecute(getSql("updateTransformTargetGroupSql"), link.getTargetNodeGroupId(), oldTargetId);
            }
            transaction.commit();
        } catch (Exception ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
        deleteNodeGroupLink(oldSourceId, oldTargetId);
    }

    public boolean doesNodeGroupExist(String nodeGroupId) {
        boolean exists = false;
        List<NodeGroup> groups = getNodeGroups();
        for (NodeGroup nodeGroup : groups) {
            exists |= nodeGroup.getNodeGroupId().equals(nodeGroupId);
        }
        return exists;
    }

    @Override
    public void saveNodeGroup(NodeGroup group) {
        group.setLastUpdateTime(new Date());
        if (sqlTemplate.update(getSql("updateNodeGroupSql"), group.getDescription(),
                group.getLastUpdateTime(), group.getLastUpdateBy(), group.getNodeGroupId()) <= 0) {
            group.setCreateTime(new Date());
            sqlTemplate.update(getSql("insertNodeGroupSql"), group.getDescription(),
                    group.getNodeGroupId(), group.getLastUpdateTime(), group.getLastUpdateBy(),
                    group.getCreateTime());
        }
    }

    @Override
    public void deleteNodeGroup(String nodeGroupId) {
        sqlTemplate.update(getSql("deleteNodeGroupSql"), nodeGroupId);
    }

    @Override
    public void deleteNodeGroupLink(NodeGroupLink link) {
        deleteNodeGroupLink(link.getSourceNodeGroupId(), link.getTargetNodeGroupId());
    }

    private void deleteNodeGroupLink(String sourceId, String targetId) {
        sqlTemplate.update(getSql("deleteNodeGroupLinkSql"), sourceId, targetId);
    }

    @Override
    public void deleteAllNodeGroupLinks() {
        sqlTemplate.update(getSql("deleteAllNodeGroupLinksSql"));
    }

    @Override
    public List<NodeGroup> getNodeGroups() {
        return sqlTemplate.query(getSql("selectNodeGroupsSql"), new NodeGroupMapper());
    }

    @Override
    public List<NodeGroupLink> getNodeGroupLinks(boolean refreshCache) {
        if (refreshCache) {
            nodeService.flushNodeGroupCache();
        }
        return cacheManager.getNodeGroupLinks(refreshCache);
    }

    @Override
    public List<NodeGroupLink> getNodeGroupLinksFromDb() {
        String sql = getSql("groupsLinksCompatibleSql");
        Table table = platform.getTableFromCache(TableConstants.getTableName(parameterService.getTablePrefix(), TableConstants.SYM_NODE_GROUP_LINK), false);
        if (table != null && table.findColumn("sync_sql_enabled") != null) {
            sql = getSql("groupsLinksSql");
        }
        return sqlTemplate.query(sql, new NodeGroupLinkMapper());
    }

    @Override
    public List<NodeGroupLink> getNodeGroupLinksFor(String sourceNodeGroupId, boolean refreshCache) {
        List<NodeGroupLink> links = getNodeGroupLinks(refreshCache);
        List<NodeGroupLink> target = new ArrayList<NodeGroupLink>(links.size());
        for (NodeGroupLink nodeGroupLink : links) {
            if (nodeGroupLink.getSourceNodeGroupId().equals(sourceNodeGroupId)) {
                target.add(nodeGroupLink);
            }
        }
        return target;
    }

    @Override
    public NodeGroupLink getNodeGroupLinkFor(String sourceNodeGroupId, String targetNodeGroupId,
            boolean refreshCache) {
        List<NodeGroupLink> links = getNodeGroupLinks(refreshCache);
        for (NodeGroupLink nodeGroupLink : links) {
            if (nodeGroupLink.getTargetNodeGroupId().equals(targetNodeGroupId)
                    && nodeGroupLink.getSourceNodeGroupId().equals(sourceNodeGroupId)) {
                return nodeGroupLink;
            }
        }
        return null;
    }

    @Override
    public boolean isChannelInUse(String channelId) {
        return sqlTemplate.queryForInt(getSql("isChannelInUseSql"), channelId) > 0;
    }

    @Override
    public void saveChannel(Channel channel, boolean reloadChannels) {
        channel.setLastUpdateTime(new Date());
        if (0 >= sqlTemplate.update(
                getSql("updateChannelSql"),
                new Object[] { channel.getProcessingOrder(), channel.getMaxBatchSize(),
                        channel.getMaxBatchToSend(), channel.getMaxDataToRoute(),
                        channel.isUseOldDataToRoute() ? 1 : 0,
                        channel.isUseRowDataToRoute() ? 1 : 0,
                        channel.isUsePkDataToRoute() ? 1 : 0, channel.isContainsBigLob() ? 1 : 0,
                        channel.isEnabled() ? 1 : 0, channel.getBatchAlgorithm(),
                        channel.getExtractPeriodMillis(), channel.getDataLoaderType(),
                        channel.getLastUpdateTime(), channel.getLastUpdateBy(),
                        channel.isReloadFlag() ? 1 : 0, channel.isFileSyncFlag() ? 1 : 0,
                        channel.getQueue(), channel.getMaxKBytesPerSecond(), channel.getDataEventAction() == null ? null : channel.getDataEventAction().name(),
                        channel.getDescription(), channel.getChannelId() })) {
            channel.setCreateTime(new Date());
            sqlTemplate.update(
                    getSql("insertChannelSql"),
                    new Object[] { channel.getChannelId(), channel.getProcessingOrder(),
                            channel.getMaxBatchSize(), channel.getMaxBatchToSend(),
                            channel.getMaxDataToRoute(), channel.isUseOldDataToRoute() ? 1 : 0,
                            channel.isUseRowDataToRoute() ? 1 : 0,
                            channel.isUsePkDataToRoute() ? 1 : 0,
                            channel.isContainsBigLob() ? 1 : 0, channel.isEnabled() ? 1 : 0,
                            channel.getBatchAlgorithm(), channel.getDescription(), channel.getExtractPeriodMillis(),
                            channel.getDataLoaderType(), channel.getLastUpdateTime(),
                            channel.getLastUpdateBy(), channel.getCreateTime(),
                            channel.isReloadFlag() ? 1 : 0, channel.isFileSyncFlag() ? 1 : 0,
                            channel.getQueue(), channel.getMaxKBytesPerSecond(), channel.getDataEventAction() == null ? null
                                    : channel.getDataEventAction().name() });
        }
        if (reloadChannels) {
            clearCache();
        }
    }

    @Override
    public void saveChannel(NodeChannel nodeChannel, boolean reloadChannels) {
        saveChannel(nodeChannel.getChannel(), reloadChannels);
    }

    @Override
    public void saveChannelAsCopy(Channel channel, boolean reloadChannels) {
        String newId = channel.getChannelId();
        List<Channel> channels = sqlTemplate.query(getSql("selectChannelsSql", "whereChannelIdLikeSql"), new ChannelMapper(),
                newId + "%");
        List<String> ids = channels.stream().map(Channel::getChannelId).collect(Collectors.toList());
        String suffix = "";
        for (int i = 2; ids.contains(newId + suffix); i++) {
            suffix = "_" + i;
        }
        channel.setChannelId(newId + suffix);
        saveChannel(channel, reloadChannels);
    }

    @Override
    public void renameChannel(String oldId, Channel channel) {
        saveChannel(channel, true);
        ISqlTransaction transaction = null;
        try {
            transaction = sqlTemplate.startSqlTransaction();
            transaction.prepareAndExecute(getSql("updateConflictChannelSql"), channel.getChannelId(), oldId);
            transaction.prepareAndExecute(getSql("updateTriggerChannelSql"), channel.getChannelId(), oldId);
            transaction.prepareAndExecute(getSql("updateTriggerReloadChannelSql"), channel.getChannelId(), oldId);
            transaction.prepareAndExecute(getSql("updateFileTriggerChannelSql"), channel.getChannelId(), oldId);
            transaction.prepareAndExecute(getSql("updateFileTriggerReloadChannelSql"), channel.getChannelId(), oldId);
            transaction.commit();
        } catch (Exception ex) {
            if (transaction != null) {
                transaction.rollback();
            }
            throw ex;
        } finally {
            close(transaction);
        }
        deleteChannel(oldId);
    }

    @Override
    public void saveNodeChannel(NodeChannel nodeChannel, boolean reloadChannels) {
        saveChannel(nodeChannel.getChannel(), false);
        saveNodeChannelControl(nodeChannel, reloadChannels);
    }

    @Override
    public void saveNodeChannelControl(NodeChannel nodeChannel, boolean reloadChannels) {
        if (0 >= sqlTemplate.update(
                getSql("updateNodeChannelControlSql"),
                new Object[] { nodeChannel.isSuspendEnabled() ? 1 : 0,
                        nodeChannel.isIgnoreEnabled() ? 1 : 0, nodeChannel.getLastExtractTime(),
                        nodeChannel.getNodeId(), nodeChannel.getChannelId() })) {
            sqlTemplate
                    .update(getSql("insertNodeChannelControlSql"),
                            new Object[] { nodeChannel.getNodeId(), nodeChannel.getChannelId(),
                                    nodeChannel.isSuspendEnabled() ? 1 : 0,
                                    nodeChannel.isIgnoreEnabled() ? 1 : 0,
                                    nodeChannel.getLastExtractTime() });
        }
        if (reloadChannels) {
            clearCache();
        }
    }

    @Override
    public void deleteChannel(Channel channel) {
        deleteChannel(channel.getChannelId());
    }

    private void deleteChannel(String id) {
        sqlTemplate.update(getSql("deleteNodeChannelSql"), new Object[] { id });
        sqlTemplate.update(getSql("deleteChannelSql"), new Object[] { id });
        clearCache();
    }

    @Override
    public void deleteNodeChannelControl(String nodeId, String channelId) {
        sqlTemplate.update(getSql("deleteNodeChannelControlSql"), new Object[] { nodeId, channelId });
        clearCache();
    }

    @Override
    public void deleteAllChannels() {
        sqlTemplate.update(getSql("deleteAllChannelsSql"));
        clearCache();
    }

    @Override
    public NodeChannel getNodeChannel(String channelId, boolean refreshExtractMillis) {
        return getNodeChannel(channelId, nodeService.findIdentityNodeId(), refreshExtractMillis);
    }

    @Override
    public NodeChannel getNodeChannel(String channelId, String nodeId, boolean refreshExtractMillis) {
        List<NodeChannel> channels = getNodeChannels(nodeId, refreshExtractMillis);
        for (NodeChannel nodeChannel : channels) {
            if (nodeChannel.getChannelId().equals(channelId)) {
                return nodeChannel;
            }
        }
        return null;
    }

    @Override
    public List<NodeChannel> getNodeChannels(boolean refreshExtractMillis) {
        return getNodeChannels(nodeService.findIdentityNodeId(), refreshExtractMillis);
    }

    @Override
    public List<NodeChannel> getNodeChannels(final String nodeId, boolean refreshExtractMillis) {
        boolean loaded = false;
        List<NodeChannel> nodeChannels;
        if (nodeId != null) {
            long origNodeChannelCacheTime = cacheManager.getNodeChannelCacheTime();
            nodeChannels = cacheManager.getNodeChannels(nodeId);
            long nodeChannelCacheTime = cacheManager.getNodeChannelCacheTime();
            if (nodeChannelCacheTime != origNodeChannelCacheTime) {
                loaded = true;
            }
        } else {
            nodeChannels = new ArrayList<NodeChannel>(0);
        }
        if (!loaded && refreshExtractMillis) {
            /*
             * need to read last extracted time from database regardless of whether we used the cache or not. locate the nodes in the cache, and update it.
             */
            final Map<String, NodeChannel> nodeChannelsMap = new HashMap<String, NodeChannel>();
            boolean usingExtractPeriod = false;
            for (NodeChannel nc : nodeChannels) {
                nodeChannelsMap.put(nc.getChannelId(), nc);
                usingExtractPeriod |= nc.getExtractPeriodMillis() > 0;
            }
            if (usingExtractPeriod) {
                sqlTemplate.query(getSql("selectNodeChannelControlSql"),
                        new ISqlRowMapper<Object>() {
                            @Override
                            public Object mapRow(Row row) {
                                String channelId = row.getString("channel_id");
                                Date extractTime = row.getDateTime("last_extract_time");
                                NodeChannel nodeChannel = nodeChannelsMap.get(channelId);
                                if (nodeChannel != null) {
                                    nodeChannel.setLastExtractTime(extractTime);
                                }
                                return nodeChannelsMap;
                            };
                        }, nodeId);
            }
        }
        return nodeChannels;
    }

    @Override
    public List<NodeChannel> getNodeChannelsFromDb(String nodeId) {
        List<NodeChannel> nodeChannels = sqlTemplate.query(getSql("selectNodeChannelsSql"), new NodeChannelMapper(nodeId));
        List<NodeChannel> nodeChannelControls = sqlTemplate.query(getSql("selectNodeChannelControlSql"),
                new ISqlRowMapper<NodeChannel>() {
                    @Override
                    public NodeChannel mapRow(Row row) {
                        NodeChannel nodeChannel = new NodeChannel();
                        nodeChannel.setChannelId(row.getString("channel_id"));
                        nodeChannel.setLastExtractTime(row.getDateTime("last_extract_time"));
                        nodeChannel.setIgnoreEnabled(row.getBoolean("ignore_enabled"));
                        nodeChannel.setSuspendEnabled(row.getBoolean("suspend_enabled"));
                        return nodeChannel;
                    };
                }, nodeId);
        for (NodeChannel nodeChannelControl : nodeChannelControls) {
            for (NodeChannel nodeChannel : nodeChannels) {
                if (nodeChannel.getChannelId().equals(nodeChannelControl.getChannelId())) {
                    nodeChannel.setIgnoreEnabled(nodeChannelControl.isIgnoreEnabled());
                    nodeChannel.setSuspendEnabled(nodeChannelControl.isSuspendEnabled());
                    nodeChannel.setLastExtractTime(nodeChannelControl.getLastExtractTime());
                }
            }
        }
        return nodeChannels;
    }

    @Override
    public void clearCache() {
        cacheManager.flushNodeChannels();
        cacheManager.flushChannels();
        cacheManager.flushNodeGroupLinks();
        cacheManager.flushNodeGroupChannelWindows();
    }

    @Override
    public NodeGroupLinkAction getDataEventActionByGroupLinkId(String sourceGroupId,
            String targetGroupId) {
        String code = sqlTemplate.queryForObject(getSql("selectDataEventActionsByIdSql"),
                String.class, sourceGroupId, targetGroupId);
        return NodeGroupLinkAction.fromCode(code);
    }

    @Override
    public void initDefaultChannels() {
        if (defaultChannels != null) {
            clearCache();
            createDefaultChannels();
            List<NodeChannel> channels = getNodeChannels(false);
            for (Channel defaultChannel : defaultChannels.values()) {
                Channel channel = defaultChannel.findInList(channels);
                if (channel == null) {
                    log.info("Auto-configuring {} channel", defaultChannel.getChannelId());
                    saveChannel(defaultChannel, true);
                } else if (channel.getChannelId().equals(Constants.CHANNEL_RELOAD)
                        && !channel.isReloadFlag()) {
                    log.info("Setting reload flag on reload channel");
                    channel.setReloadFlag(true);
                    saveChannel(channel, true);
                } else if (channel.getChannelId().equals(Constants.CHANNEL_FILESYNC)
                        && !channel.isFileSyncFlag()) {
                    log.info("Setting file sync flag on file sync channel");
                    channel.setFileSyncFlag(true);
                    saveChannel(channel, true);
                } else if (channel.getChannelId().equals(Constants.CHANNEL_FILESYNC_RELOAD)
                        && (!channel.isFileSyncFlag() || !channel.isReloadFlag())) {
                    log.info("Setting reload and file sync flag on file sync reload channel");
                    channel.setFileSyncFlag(true);
                    saveChannel(channel, true);
                } else {
                    log.debug("No need to create channel {}.  It already exists",
                            defaultChannel.getChannelId());
                }
            }
            clearCache();
        }
    }

    @Override
    public List<NodeGroupChannelWindow> getNodeGroupChannelWindows(String notUsed, String channelId) {
        return cacheManager.getNodeGroupChannelWindows().get(channelId);
    }

    @Override
    public Map<String, List<NodeGroupChannelWindow>> getNodeGroupChannelWindowsFromDb() {
        Map<String, List<NodeGroupChannelWindow>> channelWindowsByChannel = new HashMap<String, List<NodeGroupChannelWindow>>();
        String nodeGroupId = parameterService.getNodeGroupId();
        Set<String> channelIds = getChannels(false).keySet();
        for (String id : channelIds) {
            channelWindowsByChannel.put(id, sqlTemplate.query(getSql("selectNodeGroupChannelWindowSql"),
                    new NodeGroupChannelWindowMapper(), nodeGroupId, id));
        }
        return channelWindowsByChannel;
    }

    @Override
    public void deleteNodeGroupChannelWindowsByNodeGroup(String nodeGroupId) {
        sqlTemplate.update(getSql("deleteNodeGroupChannelWindowByNodeGroupSql"), nodeGroupId);
        cacheManager.flushNodeGroupChannelWindows();
    }

    @Override
    public ChannelMap getSuspendIgnoreChannelLists(final String nodeId) {
        ChannelMap map = new ChannelMap();
        List<NodeChannel> ncs = getNodeChannels(nodeId, true);
        if (ncs != null) {
            for (NodeChannel nc : ncs) {
                if (nc.isSuspendEnabled()) {
                    map.addSuspendChannels(nc.getChannelId());
                }
                if (nc.isIgnoreEnabled()) {
                    map.addIgnoreChannels(nc.getChannelId());
                }
            }
        }
        return map;
    }

    @Override
    public List<Channel> getFileSyncChannels() {
        List<Channel> list = new ArrayList<Channel>(getChannels(false).values());
        Iterator<Channel> it = list.iterator();
        while (it.hasNext()) {
            Channel channel = it.next();
            if (!channel.isFileSyncFlag()) {
                it.remove();
            }
        }
        return list;
    }

    @Override
    public Map<String, Channel> getChannels(boolean refreshCache) {
        return cacheManager.getChannels(refreshCache);
    }

    @Override
    public Collection<String> getQueues(boolean refreshCache) {
        return cacheManager.getQueues(refreshCache);
    }

    @Override
    public Map<String, Channel> getChannelsFromDb() {
        Map<String, Channel> channels = new HashMap<String, Channel>();
        List<Channel> list = sqlTemplate.query(getSql("selectChannelsSql", "orderChannelsBySql"), new ChannelMapper());
        for (Channel channel : list) {
            channels.put(channel.getChannelId(), channel);
        }
        return channels;
    }

    @Override
    public Channel getChannel(String channelId) {
        NodeChannel nodeChannel = getNodeChannel(channelId, false);
        if (nodeChannel != null) {
            return nodeChannel.getChannel();
        } else {
            return null;
        }
    }

    @Override
    public ChannelMap getSuspendIgnoreChannelLists() {
        return getSuspendIgnoreChannelLists(nodeService.findIdentityNodeId());
    }

    @Override
    public Map<String, String> getRegistrationRedirectMap() {
        return this.sqlTemplate.queryForMap(getSql("getRegistrationRedirectSql"),
                "registrant_external_id", "registration_node_id");
    }

    @Override
    public void updateLastExtractTime(NodeChannel channel) {
        sqlTemplate.update(getSql("updateNodeChannelLastExtractTime"), channel.getLastExtractTime(), channel.getChannelId(), channel.getNodeId());
    }

    static class NodeGroupChannelWindowMapper implements ISqlRowMapper<NodeGroupChannelWindow> {
        @Override
        public NodeGroupChannelWindow mapRow(Row row) {
            NodeGroupChannelWindow window = new NodeGroupChannelWindow();
            window.setNodeGroupId(row.getString("node_group_id"));
            window.setChannelId(row.getString("channel_id"));
            window.setStartTime(row.getTime("start_time"));
            window.setEndTime(row.getTime("end_time"));
            window.setEnabled(row.getBoolean("enabled"));
            return window;
        }
    }

    static class NodeGroupLinkMapper implements ISqlRowMapper<NodeGroupLink> {
        @Override
        public NodeGroupLink mapRow(Row row) {
            NodeGroupLink link = new NodeGroupLink();
            link.setSourceNodeGroupId(row.getString("source_node_group_id"));
            link.setTargetNodeGroupId(row.getString("target_node_group_id"));
            link.setDataEventAction(NodeGroupLinkAction.fromCode(row.getString("data_event_action")));
            link.setSyncConfigEnabled(row.getBoolean("sync_config_enabled"));
            if (row.containsKey("sync_sql_enabled")) {
                link.setSyncSqlEnabled(row.getBoolean("sync_sql_enabled"));
            }
            link.setReversible(row.getBoolean("is_reversible"));
            link.setCreateTime(row.getDateTime("create_time"));
            link.setLastUpdateBy(row.getString("last_update_by"));
            link.setLastUpdateTime(row.getDateTime("last_update_time"));
            return link;
        }
    }

    static class NodeGroupMapper implements ISqlRowMapper<NodeGroup> {
        @Override
        public NodeGroup mapRow(Row row) {
            NodeGroup group = new NodeGroup();
            group.setNodeGroupId(row.getString("node_group_id"));
            group.setDescription(row.getString("description"));
            group.setCreateTime(row.getDateTime("create_time"));
            group.setLastUpdateBy(row.getString("last_update_by"));
            group.setLastUpdateTime(row.getDateTime("last_update_time"));
            return group;
        }
    }

    static class NodeChannelMapper implements ISqlRowMapper<NodeChannel> {
        String nodeId;

        public NodeChannelMapper(String nodeId) {
            this.nodeId = nodeId;
        }

        @Override
        public NodeChannel mapRow(Row row) {
            NodeChannel nodeChannel = new NodeChannel();
            nodeChannel.setChannelId(row.getString("channel_id"));
            nodeChannel.setNodeId(nodeId);
            nodeChannel.setProcessingOrder(row.getInt("processing_order"));
            nodeChannel.setMaxBatchSize(row.getInt("max_batch_size"));
            nodeChannel.setEnabled(row.getBoolean("enabled"));
            nodeChannel.setMaxBatchToSend(row.getInt("max_batch_to_send"));
            nodeChannel.setMaxDataToRoute(row.getInt("max_data_to_route"));
            nodeChannel.setUseOldDataToRoute(row.getBoolean("use_old_data_to_route"));
            nodeChannel.setUseRowDataToRoute(row.getBoolean("use_row_data_to_route"));
            nodeChannel.setUsePkDataToRoute(row.getBoolean("use_pk_data_to_route"));
            nodeChannel.setContainsBigLob(row.getBoolean("contains_big_lob"));
            nodeChannel.setBatchAlgorithm(row.getString("batch_algorithm"));
            nodeChannel.setExtractPeriodMillis(row.getLong("extract_period_millis"));
            nodeChannel.setDataLoaderType(row.getString("data_loader_type"));
            nodeChannel.setCreateTime(row.getDateTime("create_time"));
            nodeChannel.setLastUpdateBy(row.getString("last_update_by"));
            nodeChannel.setLastUpdateTime(row.getDateTime("last_update_time"));
            nodeChannel.setFileSyncFlag(row.getBoolean("file_sync_flag"));
            nodeChannel.setReloadFlag(row.getBoolean("reload_flag"));
            nodeChannel.setQueue(row.getString("queue"));
            nodeChannel.setMaxKBytesPerSecond(row.getBigDecimal("max_network_kbps"));
            nodeChannel.setDataEventAction(NodeGroupLinkAction.fromCode(row.getString("data_event_action")));
            return nodeChannel;
        }
    }

    static class ChannelMapper implements ISqlRowMapper<Channel> {
        @Override
        public Channel mapRow(Row row) {
            Channel channel = new Channel();
            channel.setChannelId(row.getString("channel_id"));
            channel.setProcessingOrder(row.getInt("processing_order"));
            channel.setMaxBatchSize(row.getInt("max_batch_size"));
            channel.setEnabled(row.getBoolean("enabled"));
            channel.setMaxBatchToSend(row.getInt("max_batch_to_send"));
            channel.setMaxDataToRoute(row.getInt("max_data_to_route"));
            channel.setUseOldDataToRoute(row
                    .getBoolean("use_old_data_to_route"));
            channel.setUseRowDataToRoute(row
                    .getBoolean("use_row_data_to_route"));
            channel.setUsePkDataToRoute(row
                    .getBoolean("use_pk_data_to_route"));
            channel.setContainsBigLob(row.getBoolean("contains_big_lob"));
            channel.setBatchAlgorithm(row.getString("batch_algorithm"));
            channel.setExtractPeriodMillis(row
                    .getLong("extract_period_millis"));
            channel.setDataLoaderType(row.getString("data_loader_type"));
            channel.setCreateTime(row.getDateTime("create_time"));
            channel.setLastUpdateBy(row.getString("last_update_by"));
            channel.setLastUpdateTime(row.getDateTime("last_update_time"));
            channel.setReloadFlag(row.getBoolean("reload_flag"));
            channel.setFileSyncFlag(row.getBoolean("file_sync_flag"));
            channel.setQueue(row.getString("queue"));
            channel.setMaxKBytesPerSecond(row.getBigDecimal("max_network_kbps"));
            channel.setDataEventAction(NodeGroupLinkAction.fromCode(row.getString("data_event_action")));
            if (row.containsKey("description")) {
                channel.setDescription(row.getString("description"));
            }
            return channel;
        }
    }
}
