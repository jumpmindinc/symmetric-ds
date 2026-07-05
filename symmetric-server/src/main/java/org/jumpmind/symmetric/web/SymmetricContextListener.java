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

import java.net.URL;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ApplicationHealthTracker;
import org.jumpmind.symmetric.IApplicationHealthTracker;
import org.jumpmind.symmetric.cache.ClusteredCacheManager;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.util.TypedPropertiesFactory;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.support.WebApplicationContextUtils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public class SymmetricContextListener implements ServletContextListener {
    private static final Logger log = LoggerFactory.getLogger(SymmetricContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Class<?> remoteStatusEndpoint = loadRemoteStatusEndpoint();
        if (remoteStatusEndpoint != null) {
            // TODO: remote status service uses websockets
            /**
             * ServerContainer container = (ServerContainer) sce.getServletContext().getAttribute(ServerContainer.class.getName()); if (container != null) {
             * container.setDefaultMaxBinaryMessageBufferSize(Integer.MAX_VALUE); container.setDefaultMaxTextMessageBufferSize(Integer.MAX_VALUE);
             * ServerEndpointConfig websocketConfig = ServerEndpointConfig.Builder.create(remoteStatusEndpoint, "/control").build(); try {
             * container.addEndpoint(websocketConfig); } catch (DeploymentException e) { log.error("An exception occurred while adding the remote status
             * endpoint", e); } }
             */
        }
        SymmetricEngineHolder engineHolder = new SymmetricEngineHolder();
        ServletContext ctx = sce.getServletContext();
        String autoStart = ctx.getInitParameter(WebConstants.INIT_PARAM_AUTO_START);
        engineHolder.setAutoStart(autoStart == null ? true : autoStart.equalsIgnoreCase("true"));
        String autoCreate = ctx.getInitParameter(WebConstants.INIT_PARAM_AUTO_CREATE);
        engineHolder.setAutoCreate(autoCreate == null ? true : autoCreate.equalsIgnoreCase("true"));
        String multiServerMode = ctx.getInitParameter(WebConstants.INIT_PARAM_MULTI_SERVER_MODE);
        engineHolder.setMultiServerMode((multiServerMode != null && multiServerMode.equalsIgnoreCase("true")) ||
                StringUtils.isNotBlank(System.getProperty(SystemConstants.SYSPROP_ENGINES_DIR)));
        engineHolder.setSingleServerPropertiesFile(ctx
                .getInitParameter(WebConstants.INIT_SINGLE_SERVER_PROPERTIES_FILE));
        String staticEnginesMode = ctx.getInitParameter(WebConstants.INIT_PARAM_STATIC_ENGINES_MODE);
        engineHolder.setStaticEnginesMode(staticEnginesMode != null
                && staticEnginesMode.equalsIgnoreCase("true"));
        engineHolder.setDeploymentType(ctx.getInitParameter(WebConstants.INIT_PARAM_DEPLOYMENT_TYPE));
        ctx.setAttribute(WebConstants.ATTR_ENGINE_HOLDER, engineHolder);
        ApplicationHealthTracker.setTracker(AppUtils.newInstance(IApplicationHealthTracker.class, ApplicationHealthTracker.class));
        String useWebApplicationContext = ctx.getInitParameter(WebConstants.INIT_SINGLE_USE_WEBAPP_CONTEXT);
        if ("true".equals(useWebApplicationContext)) {
            engineHolder.setSpringContext(WebApplicationContextUtils.getWebApplicationContext(sce.getServletContext()));
        }
        if (!"true".equals(System.getProperty(SystemConstants.SYSPROP_LAUNCHER))) {
            injectServerPropertiesIntoSystem("/symmetric-server.properties");
        }
        engineHolder.start();
        Runtime.getRuntime().addShutdownHook(new Thread(SymmetricContextListener::shutdownClusterCommunicationAndHealth,
                "symmetric-cluster-communication-shutdown"));
    }

    private static void shutdownClusterCommunicationAndHealth() {
        ApplicationHealthTracker.getTracker().onShutdown();
        ClusteredCacheManager.getInstance().stopClusterCommunication();
    }

    protected Class<?> loadRemoteStatusEndpoint() {
        try {
            Class<?> clazz = ClassUtils.getClass("com.jumpmind.symmetric.console.remote.ServerEndpoint");
            return clazz;
        } catch (ClassNotFoundException ex) {
            // ServerEndpoint not found. This is an expected condition.
        } catch (Exception ex) {
            log.debug("Failed to load remote status endpoint.", ex);
        }
        return null;
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        SymmetricEngineHolder engineHolder = (SymmetricEngineHolder) ctx
                .getAttribute(WebConstants.ATTR_ENGINE_HOLDER);
        if (engineHolder != null) {
            engineHolder.stop();
            ctx.removeAttribute(WebConstants.ATTR_ENGINE_HOLDER);
        }
    }

    private void injectServerPropertiesIntoSystem(String resourcePath) {
        URL serverPropertiesURL = getClass().getClassLoader().getResource(resourcePath);
        if (serverPropertiesURL == null) {
            log.debug("Resource path {} not found", resourcePath);
            return;
        }
        TypedProperties serverProperties = new TypedProperties(serverPropertiesURL);
        TypedPropertiesFactory.mergeAndOverrideWithJvmAndEnvironmentVariables(serverProperties, false);
        System.getProperties().putAll(serverProperties);
    }
}
