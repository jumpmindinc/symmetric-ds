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
package org.jumpmind.symmetric.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.properties.SortedProperties;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.security.SecurityServiceFactory.SecurityServiceType;
import org.jumpmind.symmetric.ApplicationHealthTracker;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.cache.ClusterPartitionGenerator;
import org.jumpmind.symmetric.cache.ClusterPeerServerState;
import org.jumpmind.symmetric.cache.ClusterPeerStatusMessage;
import org.jumpmind.symmetric.cache.ClusteredCacheManager;
import org.jumpmind.symmetric.cache.ClusteredEngineState;
import org.jumpmind.symmetric.cache.IClusteredCacheManager;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.ext.IDatabaseInstallStatementListener;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.NodeGroup;
import org.jumpmind.symmetric.model.NodeGroupLink;
import org.jumpmind.symmetric.model.NodeGroupLinkAction;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IRegistrationService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.util.PropertiesUtil;
import org.jumpmind.symmetric.util.SymmetricUtils;
import org.jumpmind.symmetric.util.TypedPropertiesFactory;
import org.jumpmind.util.CustomizableThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public class SymmetricEngineHolder implements ISymmetricEngineHolder {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static Map<String, ServerSymmetricEngine> staticEngines = Collections.synchronizedMap(new HashMap<String, ServerSymmetricEngine>());
    private static Set<SymmetricEngineStarter> staticEnginesStarting = Collections.synchronizedSet(new HashSet<SymmetricEngineStarter>());
    private static Set<String> staticEnginesStartingNames = Collections.synchronizedSortedSet(new TreeSet<String>());
    private static Map<String, FailedEngineInfo> staticEnginesFailed = Collections.synchronizedMap(new HashMap<String, FailedEngineInfo>());
    private Map<String, ServerSymmetricEngine> engines = Collections.synchronizedMap(new HashMap<String, ServerSymmetricEngine>());
    private Set<SymmetricEngineStarter> enginesStarting = Collections.synchronizedSet(new HashSet<SymmetricEngineStarter>());
    private Set<String> enginesStartingNames = Collections.synchronizedSortedSet(new TreeSet<String>());
    private Map<String, FailedEngineInfo> enginesFailed = Collections.synchronizedMap(new HashMap<String, FailedEngineInfo>());
    private ExecutorService restartExecutor;
    private boolean staticEnginesMode = false;
    private boolean multiServerMode = false;
    private boolean isAutoStartEnginesEnabled = true;
    private boolean isAutoDiscoverEnginesEnabled = true;
    private ApplicationContext springContext;
    private String singleServerPropertiesFile;
    private String deploymentType = Constants.DEPLOYMENT_TYPE_SERVER;
    private boolean holderHasBeenStarted = false;
    private static final String DEFAULT_CONCURRENT_ENGINES_STARTING_COUNT = "5";
    private static TypedProperties coreServerProperties;
    private static ISecurityService securityService = SecurityServiceFactory.create(SecurityServiceType.SERVER, null);
    private static IClusteredCacheManager clusteredCacheManager;

    SymmetricEngineHolder() {
        if (coreServerProperties == null) {
            coreServerProperties = fetchStaticServerProperties();
        }
        if (securityService == null) {
            securityService = SecurityServiceFactory.create(SecurityServiceType.SERVER, null);
        }
        if (clusteredCacheManager == null) {
            clusteredCacheManager = initClusteredCacheManager();
        }
    }

    private TypedProperties fetchStaticServerProperties() {
        TypedProperties envProps = new TypedProperties();
        TypedProperties symEnvVars = new TypedProperties();
        symEnvVars.collectFrom(TypedPropertiesFactory.getEnvironmentVariables(), ServerConstants.SYM_ENV_PREFIX, true);
        if (!symEnvVars.isEmpty()) {
            TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(envProps, true);
        }
        return envProps;
    }

    /**
     * Initialize JCS cluster peer heartbeat and discovery with no database dependency and no engine files. Additional peer servers can be linked later on.
     */
    private IClusteredCacheManager initClusteredCacheManager() {
        if (clusteredCacheManager != null && clusteredCacheManager.isInitialized()) {
            return clusteredCacheManager;
        }
        IClusteredCacheManager ccManager = ClusteredCacheManager.getInstance();
        String clusterPartitionId = ClusterPartitionGenerator.resolve(coreServerProperties);
        String serverId = ClusterPartitionGenerator.resolveServerId(coreServerProperties);
        ccManager.initialize(securityService, clusterPartitionId, serverId, isJcsEnabled(), this);
        ccManager.startClusterHeartbeat();
        ccManager.broadcastStateToPeers(ClusterPeerServerState.INITIALIZING);
        return ccManager;
    }

    /**
     * Resolved without any engine's {@code IParameterService}/the database, since no engine has been loaded yet at this point. Equivalent environment variable:
     * SYM_CLUSTER_LOCK_ENABLED.
     */
    private static boolean isJcsEnabled() {
        String value = System.getProperty(ParameterConstants.CLUSTER_LOCKING_ENABLED);
        if (StringUtils.isBlank(value)) {
            value = System.getenv("SYM_CLUSTER_LOCK_ENABLED");
        }
        return Boolean.parseBoolean(value);
    }

    public void start() {
        try {
            SymmetricUtils.logNotices();
            if (staticEnginesMode) {
                switchToStaticEnginesMode();
            }
            if (isAutoDiscoverEnginesEnabled) {
                discoverEngines();
            }
            startAllEngines();
        } finally {
            holderHasBeenStarted = true;
        }
    }

    private void switchToStaticEnginesMode() {
        // Switch engine holder class instance variables to static for multi-holder deployments.
        log.debug("In static engine mode");
        engines = staticEngines;
        enginesStarting = staticEnginesStarting;
        enginesStartingNames = staticEnginesStartingNames;
        enginesFailed = staticEnginesFailed;
    }

    private void discoverEngines() {
        if (log.isDebugEnabled()) {
            log.debug("Reading property files -> load SymmetricEngineStarters into enginesStarting. Current directory is {}", System.getProperty(
                    "user.dir"));
        }
        TypedProperties envProps = fetchEnvironmentProperties();
        if (isMultiServerMode()) {
            String enginesDirName = PropertiesUtil.getEnginesDir();
            loadMultiServerEngines(enginesDirName, envProps);
        } else {
            String engineFileName = singleServerPropertiesFile;
            loadSingleServerEngine(engineFileName, envProps);
        }
    }

    private void loadMultiServerEngines(String enginesDirName, TypedProperties envProps) {
        log.debug("Starting in multi-server mode with engines directory at {}", enginesDirName);
        File enginesDir = new File(enginesDirName);
        File[] engineFiles = enginesDir.listFiles();
        if (engineFiles == null) {
            String firstAttempt = enginesDir.getAbsolutePath();
            File currentDir = new File(".");
            log.warn("Unable to retrieve engine properties files from {}.  Trying current working directory {}",
                    firstAttempt, currentDir.getAbsolutePath());
            engineFiles = currentDir.listFiles();
        }
        if (engineFiles == null) {
            log.error("Still unable to retrieve engine properties files after checking default location and current working directory.  No engines to start.");
            return;
        }
        validateEngineFiles(engineFiles);
        int startingEngineCount = enginesStarting.size();
        for (File file : engineFiles) {
            if (file.getName().endsWith(".properties")) {
                enginesStarting.add(new SymmetricEngineStarter(file.getAbsolutePath(), this));
            }
        }
        if (enginesStarting.size() == startingEngineCount) {
            if (!SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps)
                    || !createAndAddEngineFromEnvironmentVars(enginesDir, envProps)) {
                log.info("No engine *.properties files found");
            }
        }
    }

    private boolean createAndAddEngineFromEnvironmentVars(File enginesDir, TypedProperties envProps) {
        try {
            File engineFile = SymmetricEngineFileUtils.createEngineFileFromEnvironmentVars(enginesDir, envProps);
            if (engineFile != null) {
                String engineFilePath = engineFile.getAbsolutePath();
                enginesStarting.add(new SymmetricEngineStarter(engineFilePath, this));
                log.debug("Built engine properties file from environment variables. Path={}", engineFilePath);
                return true;
            } else {
                log.warn("Failed to build engine properties file from environment variables!");
                return false;
            }
        } catch (Exception ex) {
            log.error("Error while building engine properties file from environment variables!", ex);
            return false;
        }
    }

    private void loadSingleServerEngine(String engineFileName, TypedProperties envProps) {
        log.debug("Load single engine from symmetric.properties file in single-server mode");
        if (StringUtils.isBlank(engineFileName)) {
            URL singleServerPropertiesURL = getClass().getClassLoader().getResource("/symmetric.properties");
            if (singleServerPropertiesURL != null) {
                engineFileName = singleServerPropertiesURL.getFile();
            }
        }
        if (StringUtils.isNotBlank(engineFileName)) {
            enginesStarting.add(new SymmetricEngineStarter(engineFileName, this));
        } else if (!SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps)
                || !createAndAddEngineFromEnvironmentVars(new File(PropertiesUtil.getEnginesDir()), envProps)) {
            log.error("No engine properties found in symmetric.properties file!");
        }
    }

    private void startAllEngines() {
        Set<SymmetricEngineStarter> registrationEngineStarters = filterEngineStartersByRegistrationType(true, enginesStarting);
        Set<SymmetricEngineStarter> nonRegistrationEngineStarters = filterEngineStartersByRegistrationType(false, enginesStarting);
        if (registrationEngineStarters.isEmpty()) {
            log.debug("No registration engines found. Starting all other engines.");
        } else {
            log.debug("Starting registration engines first");
            startEnginesAndWait(registrationEngineStarters);
            log.info("All registration engines have been started (before non-registration engines).");
        }
        log.debug("All engines now starting up.");
        startEngines(nonRegistrationEngineStarters);
    }

    protected Set<SymmetricEngineStarter> filterEngineStartersByRegistrationType(boolean isRegistrationEngineStarter,
            Set<SymmetricEngineStarter> source) {
        return source.stream()
                .filter(starter -> starter.isRegistrationEngineStarter() == isRegistrationEngineStarter)
                .collect(Collectors.toSet());
    }

    private void startEnginesAndWait(Set<SymmetricEngineStarter> starters) {
        // try-with-resources: close() blocks until these engines finish before returning
        try (ExecutorService executor = getEngineStarterExecutor()) {
            executeEngineStarters(executor, starters);
        } catch (Exception e) {
            log.error("Error while waiting for registration engines to start: {}", starters, e);
        }
    }

    private void startEngines(Set<SymmetricEngineStarter> starters) {
        executeEngineStarters(getEngineStarterExecutor(), starters);
    }

    private ExecutorService getEngineStarterExecutor() {
        int poolSize = Integer.parseInt(System.getProperty(SystemConstants.SYSPROP_CONCURRENT_ENGINES_STARTING_COUNT,
                DEFAULT_CONCURRENT_ENGINES_STARTING_COUNT));
        CustomizableThreadFactory launchThreadPoolFactory = new CustomizableThreadFactory("symmetric-engine-startup");
        return Executors.newFixedThreadPool(poolSize, launchThreadPoolFactory);
    }

    private void executeEngineStarters(ExecutorService executor, Set<SymmetricEngineStarter> engineStarters) {
        for (SymmetricEngineStarter starter : engineStarters) {
            executor.execute(starter);
        }
        executor.shutdown();
    }

    public synchronized void restart(String engineName) {
        FailedEngineInfo info = enginesFailed.get(engineName);
        if (info != null) {
            ISymmetricEngine engine = engines.get(engineName);
            if (engine != null) {
                try {
                    engine.destroy();
                } catch (Exception e) {
                    log.warn("Destroy of engine failed", e);
                }
                engines.remove(engineName);
                ApplicationHealthTracker.getTracker().stopTrackingEngine(engineName);
            }
            enginesFailed.remove(engineName);
            if (restartExecutor == null) {
                int poolSize = Integer.parseInt(System.getProperty(SystemConstants.SYSPROP_CONCURRENT_ENGINES_STARTING_COUNT, "5"));
                restartExecutor = Executors.newFixedThreadPool(poolSize, new CustomizableThreadFactory("symmetric-engine-restart"));
            }
            SymmetricEngineStarter starter = new SymmetricEngineStarter(info.getPropertyFileName(), this);
            enginesStarting.add(starter);
            restartExecutor.execute(starter);
        }
    }

    public synchronized void stop() {
        ApplicationHealthTracker.getTracker().onShutdown();
        for (ServerSymmetricEngine engine : engines.values()) {
            engine.destroy();
        }
        engines.clear();
        enginesStarting.clear();
        enginesFailed.clear();
        clusteredCacheManager.shutdown();
        clusteredCacheManager = null;
    }

    public ISymmetricEngine install(Properties passedInProperties) throws Exception {
        return install(passedInProperties, null, true, true);
    }

    public ISymmetricEngine install(Properties passedInProperties, IDatabaseInstallStatementListener listener) throws Exception {
        return install(passedInProperties, listener, true, true);
    }

    public ISymmetricEngine install(Properties passedInProperties, IDatabaseInstallStatementListener listener, boolean startJobs,
            boolean createConfig) throws Exception {
        ITypedPropertiesFactory factory = PropertiesUtil.createTypedPropertiesFactory(null, passedInProperties);
        TypedProperties properties = factory.reload(passedInProperties);
        String password = properties.getProperty(DataSourceProperties.DB_POOL_PASSWORD);
        if (StringUtils.isNotBlank(password) && !password.startsWith(SecurityConstants.PREFIX_ENC)) {
            try {
                ISecurityService service = SecurityServiceFactory.create(SecurityServiceType.CLIENT, properties);
                properties.setProperty(DataSourceProperties.DB_POOL_PASSWORD,
                        SecurityConstants.PREFIX_ENC + service.encrypt(password));
            } catch (Exception ex) {
                log.warn("Could not encrypt password", ex);
            }
        }
        String loadOnlyPassword = properties.getProperty(ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_PASSWORD);
        if (StringUtils.isNotBlank(loadOnlyPassword) && !loadOnlyPassword.startsWith(SecurityConstants.PREFIX_ENC)) {
            try {
                ISecurityService service = SecurityServiceFactory.create(SecurityServiceType.CLIENT, properties);
                properties.setProperty(ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_PASSWORD,
                        SecurityConstants.PREFIX_ENC + service.encrypt(loadOnlyPassword));
            } catch (Exception ex) {
                log.warn("Could not encrypt load-only password", ex);
            }
        }
        String engineName = validateRequiredProperties(properties);
        passedInProperties.setProperty(ParameterConstants.ENGINE_NAME, engineName);
        properties = factory.reload(properties);
        if (engines.get(engineName) != null) {
            try {
                engines.get(engineName).stop();
            } catch (Exception e) {
                log.error("", e);
            }
            engines.remove(engineName);
            ApplicationHealthTracker.getTracker().stopTrackingEngine(engineName);
        }
        File enginesDir = new File(PropertiesUtil.getEnginesDir());
        File symmetricProperties = new File(enginesDir, engineName + ".properties");
        try {
            SortedProperties sortedProperties = new SortedProperties();
            sortedProperties.putAll(properties);
            factory.save(sortedProperties, symmetricProperties, "Updated by SymmetricDS Pro");
        } catch (IOException ex) {
            throw new RuntimeException("Failed to write symmetric.properties to engine directory", ex);
        }
        ISymmetricEngine engine = null;
        try {
            String registrationUrl = properties.getProperty(ParameterConstants.REGISTRATION_URL);
            if (createConfig && StringUtils.isNotBlank(registrationUrl)) {
                Collection<ServerSymmetricEngine> all = new ArrayList<ServerSymmetricEngine>(getEngines().values());
                for (ISymmetricEngine currentEngine : all) {
                    if (currentEngine.getParameterService().getSyncUrl().equals(registrationUrl)) {
                        String serverNodeGroupId = currentEngine.getParameterService().getNodeGroupId();
                        String clientNodeGroupId = properties.getProperty(ParameterConstants.NODE_GROUP_ID);
                        String externalId = properties.getProperty(ParameterConstants.EXTERNAL_ID);
                        IConfigurationService configurationService = currentEngine.getConfigurationService();
                        ITriggerRouterService triggerRouterService = currentEngine.getTriggerRouterService();
                        List<NodeGroup> groups = configurationService.getNodeGroups();
                        boolean foundGroup = false;
                        for (NodeGroup nodeGroup : groups) {
                            if (nodeGroup.getNodeGroupId().equals(clientNodeGroupId)) {
                                foundGroup = true;
                            }
                        }
                        if (!foundGroup) {
                            configurationService.saveNodeGroup(new NodeGroup(clientNodeGroupId));
                            NodeGroupLink serverToClientLink = new NodeGroupLink(serverNodeGroupId, clientNodeGroupId, NodeGroupLinkAction.W);
                            configurationService.saveNodeGroupLink(serverToClientLink);
                            NodeGroupLink clientToServerLink = new NodeGroupLink(clientNodeGroupId, serverNodeGroupId, NodeGroupLinkAction.P);
                            configurationService.saveNodeGroupLink(clientToServerLink);
                            Router serverToClientRouter = new Router("", serverToClientLink);
                            serverToClientRouter.setRouterId(serverToClientRouter.createDefaultName());
                            Router clientToServerRouter = new Router("", clientToServerLink);
                            clientToServerRouter.setRouterId(clientToServerRouter.createDefaultName());
                            triggerRouterService.saveRouter(serverToClientRouter);
                            triggerRouterService.saveRouter(clientToServerRouter);
                            triggerRouterService.syncTriggers();
                        } else {
                            boolean foundLink = false;
                            List<NodeGroupLink> links = configurationService.getNodeGroupLinksFor(serverNodeGroupId, false);
                            for (NodeGroupLink nodeGroupLink : links) {
                                if (nodeGroupLink.getTargetNodeGroupId().equals(clientNodeGroupId)) {
                                    foundLink = true;
                                }
                            }
                            if (!foundLink) {
                                configurationService.saveNodeGroupLink(new NodeGroupLink(serverNodeGroupId, clientNodeGroupId, NodeGroupLinkAction.W));
                                triggerRouterService.syncTriggers();
                            }
                        }
                        IRegistrationService registrationService = currentEngine.getRegistrationService();
                        if (!registrationService.isAutoRegistration() && !registrationService.isRegistrationOpen(clientNodeGroupId, externalId)) {
                            Node node = new Node(properties);
                            if (TableConstants.getTables("").contains(TableConstants.SYM_CONSOLE_USER)) {
                                node.setDeploymentType(Constants.DEPLOYMENT_TYPE_PROFESSIONAL);
                            }
                            registrationService.openRegistration(node);
                        }
                    }
                }
            }
            engine = create(symmetricProperties.getAbsolutePath());
            if (engine != null) {
                if (listener != null) {
                    engine.getExtensionService().addExtensionPoint(listener);
                }
                engine.start(startJobs);
            } else {
                FileUtils.deleteQuietly(symmetricProperties);
                log.warn("The engine could not be created.  It will not be started");
            }
            return engine;
        } catch (RuntimeException ex) {
            if (engine != null) {
                engine.destroy();
            }
            FileUtils.deleteQuietly(symmetricProperties);
            throw ex;
        }
    }

    public void uninstallEngine(ISymmetricEngine engine) {
        Node node = engine.getNodeService().getCachedIdentity();
        String engineName = engine.getEngineName();
        File file = PropertiesUtil.findPropertiesFileForEngineWithName(engineName, engine.getParameterService().getReplacementValues());
        engine.uninstall();
        engine.destroy();
        if (file != null) {
            file.delete();
        }
        getEngines().remove(engineName);
        for (ISymmetricEngine existingEngine : this.getEngines().values()) {
            existingEngine.removeAndCleanupNode(node.getNodeId());
        }
    }

    public ISymmetricEngine create(String propertiesFile) {
        ServerSymmetricEngine engine = null;
        File file = new File(propertiesFile);
        String engineName = FilenameUtils.removeExtension(file.getName());
        try {
            TypedProperties properties = new TypedProperties();
            try (InputStream is = new FileInputStream(file.getAbsolutePath())) {
                properties.load(is);
            }
            engineName = getEngineName(properties);
            ApplicationHealthTracker.getTracker().setEngineReadiness(engineName, false);
            enginesStartingNames.add(engineName);
            validateRequiredProperties(properties);
            engine = new ServerSymmetricEngine(file, springContext, this);
            engine.setDeploymentType(deploymentType);
            engine.setDeploymentSubType(SymmetricUtils.getDeploymentSubType(properties));
            synchronized (this) {
                if (!engines.containsKey(engine.getEngineName())) {
                    engines.put(engine.getEngineName(), engine);
                } else {
                    String message = "An engine with the name of " + engineName +
                            " was not started because an engine of the same name has already been started.  " +
                            "Please set the engine.name property in the properties file to a unique name.";
                    log.error(message);
                    enginesFailed.put(engineName, new FailedEngineInfo(engineName, propertiesFile, message));
                    engine = null;
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize engine", e);
            enginesFailed.put(engineName, new FailedEngineInfo(engineName, propertiesFile, e));
            engine = null;
        }
        enginesStartingNames.remove(engineName);
        return engine;
    }

    protected void validateEngineFiles(File[] files) {
        Map<String, String> dbToPropertyFiles = new LinkedHashMap<String, String>();
        for (File file : files) {
            if (file.getName().endsWith(".properties")) {
                // external.id
                Properties properties = new Properties();
                try (InputStream fileInputStream = new FileInputStream(file.getAbsolutePath())) {
                    properties.load(fileInputStream);
                    final String userUrl = String.format("%s@%s",
                            properties.getProperty(DataSourceProperties.DB_POOL_USER, ""),
                            properties.getProperty(DataSourceProperties.DB_POOL_URL, ""));
                    final String KEY = String.format("%s@%s",
                            DataSourceProperties.DB_POOL_USER,
                            DataSourceProperties.DB_POOL_URL);
                    checkDuplicate(userUrl, KEY, dbToPropertyFiles, file);
                } catch (Exception ex) {
                    if (ex instanceof SymmetricException) {
                        log.error("**** FATAL **** error " + ex); // Jetty logs the stack at WARN level.
                        throw (SymmetricException) ex;
                    } else {
                        log.warn("Failed to validate engine properties file " + file, ex);
                    }
                }
            }
        }
    }

    protected void checkDuplicate(String value, String key, Map<String, String> values, File propertiesFile) {
        if (values.containsKey(value)) {
            throw new SymmetricException(String.format("Invalid configuration detected. 2 properties files reference "
                    + "the same %s: '%s'. Maybe an engines file was copied and needs to be moved. See: %s and %s.",
                    key, value, values.get(value), propertiesFile.getAbsolutePath()));
        } else {
            values.put(value, propertiesFile.getAbsolutePath());
        }
    }

    public String getEngineName(Properties properties) {
        String engineName = properties.getProperty(ParameterConstants.ENGINE_NAME);
        if (StringUtils.isBlank(engineName)) {
            String externalId = properties.getProperty(ParameterConstants.EXTERNAL_ID, "");
            String groupId = properties.getProperty(ParameterConstants.NODE_GROUP_ID, "");
            if (externalId.equals(groupId)) {
                engineName = groupId;
            } else {
                engineName = groupId + "-" + externalId;
            }
            engineName = engineName.replaceAll(" ", "_");
            String engineExt = "";
            int engineNumber = 0;
            while (new File(PropertiesUtil.getEnginesDir(), engineName + engineExt + ".properties").exists()) {
                engineNumber++;
                engineExt = "-" + engineNumber;
            }
            engineName = engineName + engineExt;
        }
        return engineName;
    }

    public String validateRequiredProperties(Properties properties) {
        String externalId = properties.getProperty(ParameterConstants.EXTERNAL_ID);
        if (StringUtils.isBlank(externalId)) {
            throw new IllegalStateException("Missing property " + ParameterConstants.EXTERNAL_ID);
        }
        String groupId = properties.getProperty(ParameterConstants.NODE_GROUP_ID);
        if (StringUtils.isBlank(groupId)) {
            throw new IllegalStateException("Missing property " + ParameterConstants.NODE_GROUP_ID);
        }
        String engineName = getEngineName(properties);
        properties.setProperty(ParameterConstants.ENGINE_NAME, engineName);
        if (StringUtils.isBlank(properties.getProperty(ParameterConstants.SYNC_URL))) {
            ParameterMetaData parameterMeta = ParameterConstants.getParameterMetaData().get(ParameterConstants.SYNC_URL);
            String defaultValue = "http://$(hostName):31415/sync/$(engineName)";
            if (parameterMeta != null) {
                defaultValue = parameterMeta.getDefaultValue();
            }
            log.debug("Defaulting node {} sync.url to {}", externalId, defaultValue);
            properties.setProperty(ParameterConstants.SYNC_URL, defaultValue);
        }
        if (StringUtils.isBlank(properties.getProperty(DataSourceProperties.DB_POOL_DRIVER))) {
            throw new IllegalStateException("Missing property " + DataSourceProperties.DB_POOL_DRIVER);
        }
        if (StringUtils.isBlank(properties.getProperty(DataSourceProperties.DB_POOL_URL))) {
            throw new IllegalStateException("Missing property " + DataSourceProperties.DB_POOL_URL);
        }
        if (!properties.containsKey(DataSourceProperties.DB_POOL_USER)) {
            throw new IllegalStateException("Missing property " + DataSourceProperties.DB_POOL_USER);
        }
        if (!properties.containsKey(DataSourceProperties.DB_POOL_PASSWORD)) {
            throw new IllegalStateException("Missing property " + DataSourceProperties.DB_POOL_PASSWORD);
        }
        if (!properties.containsKey(ParameterConstants.REGISTRATION_URL)) {
            properties.setProperty(ParameterConstants.REGISTRATION_URL, "");
        }
        return engineName;
    }

    public boolean hasAnyEngineInitialized() {
        for (ServerSymmetricEngine engine : engines.values()) {
            if (engine.isInitialized()) {
                return true;
            }
        }
        return false;
    }

    public boolean areEnginesStarting() {
        return !holderHasBeenStarted || enginesStarting.size() > 0;
    }

    public boolean areEnginesConfigured() {
        return enginesStarting.size() > 0 || engines.size() > 0 || enginesFailed.size() > 0;
    }

    public boolean areEnginesInError() {
        return enginesFailed.size() > 0;
    }

    public int getNumerOfEnginesStarting() {
        return enginesStarting.size();
    }

    public Map<String, ServerSymmetricEngine> getEngines() {
        return engines;
    }

    public int getEngineCount() {
        return engines.size();
    }

    /** Builds a consolidated snapshot of all currently registered engines and their states. */
    public Map<String, ClusteredEngineState> buildCurrentEngineStateSnapshot() {
        Map<String, ClusteredEngineState> snapshot = new java.util.HashMap<>();
        for (ISymmetricEngine engine : engines.values()) {
            String engineName = engine.getEngineName();
            ClusteredEngineState state = engine.getClusterService().getEngineState();
            snapshot.put(engineName, state);
        }
        for (SymmetricEngineStarter starter : enginesStarting) {
            snapshot.put(starter.getEngineName(), ClusteredEngineState.STARTING);
        }
        for (String engineName : enginesFailed.keySet()) {
            snapshot.put(engineName, ClusteredEngineState.FAILED);
        }
        return snapshot;
    }

    public Set<SymmetricEngineStarter> getEnginesStarting() {
        return enginesStarting;
    }

    public Set<String> getEnginesStartingNames() {
        return enginesStartingNames;
    }

    public Map<String, FailedEngineInfo> getEnginesFailed() {
        return enginesFailed;
    }

    public Set<String> getEnginesFailedNames() {
        return enginesFailed.keySet();
    }

    public void setSpringContext(ApplicationContext applicationContext) {
        this.springContext = applicationContext;
    }

    public ApplicationContext getSpringContext() {
        return springContext;
    }

    public void setDeploymentType(String deploymentType) {
        this.deploymentType = deploymentType;
    }

    public String getDeploymentType() {
        return deploymentType;
    }

    public void setMultiServerMode(boolean multiServerMode) {
        this.multiServerMode = multiServerMode;
    }

    public void setAutoDiscoverEngines(boolean autoCreate) {
        this.isAutoDiscoverEnginesEnabled = autoCreate;
    }

    public boolean isAutoDiscoverEngines() {
        return isAutoDiscoverEnginesEnabled;
    }

    public boolean isMultiServerMode() {
        return multiServerMode;
    }

    public void setStaticEnginesMode(boolean staticEnginesMode) {
        this.staticEnginesMode = staticEnginesMode;
    }

    public boolean isStaticEnginesMode() {
        return staticEnginesMode;
    }

    public void setSingleServerPropertiesFile(String singleServerPropertiesFile) {
        this.singleServerPropertiesFile = singleServerPropertiesFile;
    }

    public String getSingleServerPropertiesFile() {
        return singleServerPropertiesFile;
    }

    public void setAutoStart(boolean autoStart) {
        this.isAutoStartEnginesEnabled = autoStart;
    }

    public boolean isAutoStart() {
        return isAutoStartEnginesEnabled;
    }
}