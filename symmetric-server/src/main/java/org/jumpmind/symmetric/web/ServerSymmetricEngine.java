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
package org.jumpmind.symmetric.web;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.security.SecurityServiceFactory.SecurityServiceType;
import org.jumpmind.symmetric.ClientSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.ServerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public class ServerSymmetricEngine extends ClientSymmetricEngine {
    private static final Logger log = LoggerFactory.getLogger(ServerSymmetricEngine.class);
    protected List<IUriHandler> uriHandlers;
    protected SymmetricEngineHolder engineHolder;
    protected Map<String, Integer> errorCountByNode = new HashMap<String, Integer>();

    public ServerSymmetricEngine(File propertiesFile) {
        super(propertiesFile);
    }

    public ServerSymmetricEngine(File propertiesFile, ApplicationContext springContext) {
        super(propertiesFile, springContext);
    }

    public ServerSymmetricEngine(File propertiesFile, ApplicationContext springContext, SymmetricEngineHolder engineHolder) {
        super(propertiesFile, springContext);
        this.engineHolder = engineHolder;
    }

    public ServerSymmetricEngine(DataSource dataSource, ApplicationContext springContext,
            Properties properties, boolean registerEngine, SymmetricEngineHolder engineHolder) {
        super(dataSource, springContext, properties, registerEngine);
        this.engineHolder = engineHolder;
    }

    public SymmetricEngineHolder getEngineHolder() {
        return engineHolder;
    }

    @Override
    protected SecurityServiceType getSecurityServiceType() {
        return SecurityServiceType.SERVER;
    }

    @Override
    protected void init() {
        super.init();
        boolean useSessionAuth = parameterService.is(ParameterConstants.TRANSPORT_HTTP_USE_SESSION_AUTH);
        int sessionExpireSeconds = parameterService.getInt(ParameterConstants.TRANSPORT_HTTP_SESSION_EXPIRE_SECONDS);
        int maxSessions = parameterService.getInt(ParameterConstants.TRANSPORT_HTTP_SESSION_MAX_COUNT, 1000);
        AuthenticationInterceptor authInterceptor = new AuthenticationInterceptor(nodeService, securityService, useSessionAuth, sessionExpireSeconds,
                maxSessions);
        NodeConcurrencyInterceptor concurrencyInterceptor = new NodeConcurrencyInterceptor(
                concurrentConnectionManager, configurationService, nodeService, statisticManager);
        IInterceptor[] customInterceptors = buildCustomInterceptors();
        this.uriHandlers = new ArrayList<IUriHandler>();
        this.uriHandlers.add(new AckUriHandler(parameterService, acknowledgeService,
                add(customInterceptors, authInterceptor, concurrencyInterceptor)));
        this.uriHandlers.add(new PingUriHandler(parameterService, customInterceptors));
        this.uriHandlers
                .add(new InfoUriHandler(parameterService, nodeService, configurationService, clusterService, customInterceptors));
        this.uriHandlers.add(new BandwidthSamplerUriHandler(parameterService, add(customInterceptors, authInterceptor, concurrencyInterceptor)));
        this.uriHandlers.add(new PullUriHandler(parameterService, nodeService,
                configurationService, dataExtractorService, registrationService, statisticManager, outgoingBatchService,
                add(customInterceptors, authInterceptor, concurrencyInterceptor)));
        this.uriHandlers.add(new PushUriHandler(parameterService, dataLoaderService,
                statisticManager, nodeService, add(customInterceptors, authInterceptor, concurrencyInterceptor)));
        this.uriHandlers.add(new PushStatusUriHandler(parameterService, nodeCommunicationService,
                add(customInterceptors, authInterceptor, concurrencyInterceptor)));
        this.uriHandlers.add(new RegistrationUriHandler(parameterService, registrationService,
                add(customInterceptors, concurrencyInterceptor)));
        this.uriHandlers.add(new ConfigurationUriHandler(parameterService, dataExtractorService,
                add(customInterceptors, authInterceptor, concurrencyInterceptor)));
        this.uriHandlers.add(new FileSyncPullUriHandler(this, add(customInterceptors, authInterceptor, concurrencyInterceptor)));
        this.uriHandlers.add(new FileSyncPushUriHandler(this, add(customInterceptors, authInterceptor, concurrencyInterceptor)));
        this.uriHandlers.add(new CopyNodeUriHandler(this, add(customInterceptors, authInterceptor)));
        this.uriHandlers.add(new SnapshotUriHandler(this, customInterceptors));
        for (IServerSymmetricEngineLifecycle ext : extensionService.getExtensionPointList(IServerSymmetricEngineLifecycle.class)) {
            ext.initServer(this, uriHandlers, customInterceptors, authInterceptor, concurrencyInterceptor);
        }
    }

    protected IInterceptor[] buildCustomInterceptors() {
        String customInterceptorProperty = parameterService.getString(ServerConstants.SERVER_ENGINE_URI_INTERCEPTORS);
        List<IInterceptor> customInterceptors = new ArrayList<IInterceptor>();
        if (!StringUtils.isEmpty(customInterceptorProperty)) {
            String[] classNames = customInterceptorProperty.split(";");
            for (String className : classNames) {
                className = className.trim();
                try {
                    Class<?> clazz = ClassUtils.getClass(className);
                    IInterceptor interceptor = null;
                    for (Constructor<?> c : clazz.getConstructors()) {
                        if (c.getParameterTypes().length == 1
                                && c.getParameterTypes()[0].isAssignableFrom(ISymmetricEngine.class)) {
                            interceptor = (IInterceptor) c.newInstance(this);
                        }
                    }
                    if (interceptor == null) {
                        interceptor = (IInterceptor) clazz.getDeclaredConstructor().newInstance();
                    }
                    customInterceptors.add(interceptor);
                } catch (Exception ex) {
                    log.error("Failed to load custom interceptor class '" + className + "'", ex);
                }
            }
        }
        return customInterceptors.toArray(new IInterceptor[customInterceptors.size()]);
    }

    public IInterceptor[] add(IInterceptor[] array, IInterceptor... elements) {
        IInterceptor[] newArray = new IInterceptor[array.length + elements.length];
        int index = 0;
        for (IInterceptor interceptor : elements) {
            newArray[index++] = interceptor;
        }
        for (IInterceptor interceptor : array) {
            newArray[index++] = interceptor;
        }
        return newArray;
    }

    public synchronized int getErrorCountFor(String nodeId) {
        Integer errorCount = errorCountByNode.get(nodeId);
        if (errorCount == null) {
            errorCount = 1;
        }
        return errorCount;
    }

    public synchronized void incrementErrorCountForNode(String nodeId) {
        Integer errorCount = errorCountByNode.get(nodeId);
        if (errorCount == null) {
            errorCount = 1;
        }
        errorCountByNode.put(nodeId, errorCount + 1);
    }

    public synchronized void resetErrorCountForNode(String nodeId) {
        errorCountByNode.put(nodeId, 0);
    }

    public List<IUriHandler> getUriHandlers() {
        return uriHandlers;
    }
}
