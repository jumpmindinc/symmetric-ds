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
package org.jumpmind.symmetric;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.service.impl.StartupParameterService;
import org.jumpmind.symmetric.util.SymmetricUtils;
import org.jumpmind.symmetric.web.ServletUtils;
import org.jumpmind.symmetric.web.SymmetricEngineHolder;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;

import jakarta.servlet.ServletContext;

/**
 * Start up SymmetricDS through Spring Boot
 *
 * @see SymmetricLauncher#main(String[])
 */
public class SymmetricWebServer {
    private static final Logger log = LoggerFactory.getLogger(SymmetricWebServer.class);
    protected static final String DEFAULT_WEBAPP_DIR = System.getProperty(SystemConstants.SYSPROP_WEB_DIR, AppUtils.getSymHome() + "/web");
    public static final String DEFAULT_HTTP_PORT = System.getProperty(SystemConstants.SYSPROP_DEFAULT_HTTP_PORT, "31415");
    public static final String DEFAULT_HTTPS_PORT = System.getProperty(SystemConstants.SYSPROP_DEFAULT_HTTPS_PORT, "31417");
    public static final int DEFAULT_MAX_IDLE_TIME = 90000;
    protected ConfigurableApplicationContext context;
    protected boolean join = true;
    protected String webHome = "/";
    protected int maxIdleTime = DEFAULT_MAX_IDLE_TIME;
    protected boolean httpEnabled = true;
    protected int httpPort = Integer.parseInt(DEFAULT_HTTP_PORT);
    protected boolean httpsEnabled = false;
    protected boolean https2Enabled = false;
    protected int httpsPort = Integer.parseInt(DEFAULT_HTTPS_PORT);
    protected String propertiesFile = null;
    protected String host = null;
    protected String webAppDir = DEFAULT_WEBAPP_DIR;
    protected String name = "SymmetricDS";
    protected boolean httpsNeedClientAuth = false;
    protected boolean httpsWantClientAuth = false;

    public SymmetricWebServer() {
        this(null, DEFAULT_WEBAPP_DIR);
    }

    public SymmetricWebServer(String propertiesUrl) {
        this(propertiesUrl, DEFAULT_WEBAPP_DIR);
    }

    public SymmetricWebServer(int maxIdleTime, String propertiesUrl) {
        this(propertiesUrl, DEFAULT_WEBAPP_DIR);
        this.maxIdleTime = maxIdleTime;
    }

    public SymmetricWebServer(String webappDir, int maxIdleTime, String propertiesUrl, boolean join) {
        this(propertiesUrl, webappDir);
        this.maxIdleTime = maxIdleTime;
        this.join = join;
    }

    public SymmetricWebServer(String propertiesUrl, String webappDir) {
        this.propertiesFile = propertiesUrl;
        this.webAppDir = webappDir;
        initFromProperties();
    }

    protected final void initFromProperties() {
        try {
            Class.forName(AbstractCommandLauncher.class.getName());
        } catch (ClassNotFoundException e) {
        }
        TypedProperties serverProperties = StartupParameterService.getInstance().getGlobalTypedProperties();
        httpEnabled = serverProperties.is(ServerConstants.HTTP_ENABLE, true);
        httpsEnabled = serverProperties.is(ServerConstants.HTTPS_ENABLE, false);
        https2Enabled = serverProperties.is(ServerConstants.HTTPS2_ENABLE, false);
        httpPort = serverProperties.getInt(ServerConstants.HTTP_PORT, httpPort);
        httpsPort = serverProperties.getInt(ServerConstants.HTTPS_PORT, httpsPort);
        host = serverProperties.get(ServerConstants.HOST_BIND_NAME, host);
        httpsNeedClientAuth = serverProperties.is(ServerConstants.HTTPS_NEED_CLIENT_AUTH, false);
        httpsWantClientAuth = serverProperties.is(ServerConstants.HTTPS_WANT_CLIENT_AUTH, false);
        webHome = serverProperties.get(ServerConstants.SERVER_SERVLET_CONTEXT_PATH, webHome);
    }

    public SymmetricWebServer start() throws Exception {
        if (!(httpEnabled && httpPort > 0 || httpsEnabled && httpsPort > 0)) {
            throw new IllegalStateException("Either an http or https port needs to be set and enabled before starting the server.");
        }
        SymmetricUtils.logNotices();
        String protocolName = httpEnabled ? "HTTP/1.1" : httpsEnabled && https2Enabled ? "HTTPS/2" : "HTTPS/1.1";
        int port = httpEnabled ? httpPort : httpsPort;
        log.info("About to start {} web server on {}:{}:{} with context path {}", name, host == null ? "default" : host,
                port, protocolName, webHome);
        setSystemProperty(SystemConstants.SYSPROP_STANDALONE_WEB, Boolean.toString(true));
        setSystemProperty(ServerConstants.HTTP_ENABLE, Boolean.toString(httpEnabled));
        setSystemProperty(ServerConstants.HTTPS_ENABLE, Boolean.toString(httpsEnabled));
        setSystemProperty(ServerConstants.HTTP_PORT, Integer.toString(httpPort));
        setSystemProperty(ServerConstants.HTTPS_PORT, Integer.toString(httpsPort));
        setSystemProperty(ServerConstants.SERVER_CONNECTION_IDLE_TIMEOUT, Integer.toString(maxIdleTime));
        setSystemPropertyIfNotNull(ServerConstants.HOST_BIND_NAME, host);
        setSystemPropertyIfNotNull(ServerConstants.SERVER_SERVLET_CONTEXT_PATH, webHome);
        setSystemPropertyIfNotNull(ServerConstants.SERVER_SINGLE_PROPERTIES_FILE, propertiesFile);
        setSystemPropertyIfNotNull(SystemConstants.SYSPROP_WEB_DIR, webAppDir);
        context = SymmetricBoot.run(new String[0]);
        return this;
    }

    protected void setSystemProperty(String property, String value) {
        System.setProperty(property, value);
        StartupParameterService.getInstance().refreshSystemProperty(property);
    }

    protected void setSystemPropertyIfNotNull(String property, String value) {
        if (value != null) {
            setSystemProperty(property, value);
        }
    }

    public SymmetricWebServer start(int httpPort) throws Exception {
        this.httpPort = httpPort;
        this.httpEnabled = true;
        this.httpsEnabled = false;
        return start();
    }

    public SymmetricWebServer startSecure(int httpsPort) throws Exception {
        this.httpsPort = httpsPort;
        this.httpEnabled = false;
        this.httpsEnabled = true;
        return start();
    }

    public SymmetricWebServer startMixed(int httpPort, int httpsPort) throws Exception {
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.httpEnabled = true;
        this.httpsEnabled = true;
        return start();
    }

    public ServletContext getServletContext() {
        JettyCustomizer jettyCustomized = context.getBean(JettyCustomizer.class);
        return jettyCustomized != null ? jettyCustomized.getServletContext() : null;
    }

    public ISymmetricEngine getEngine() {
        ISymmetricEngine engine = null;
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            SymmetricEngineHolder engineHolder = ServletUtils.getSymmetricEngineHolder(servletContext);
            if (engineHolder != null) {
                if (engineHolder.getEngines().size() == 1) {
                    return engineHolder.getEngines().values().iterator().next();
                } else {
                    throw new IllegalStateException("Could not choose a single engine to return.  There are "
                            + engineHolder.getEngines().size() + " engines configured.");
                }
            }
        }
        return engine;
    }

    public ISymmetricEngine getEngine(String name) {
        ISymmetricEngine engine = null;
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            SymmetricEngineHolder engineHolder = ServletUtils.getSymmetricEngineHolder(servletContext);
            if (engineHolder != null) {
                return engineHolder.getEngines().get(name);
            }
        }
        return engine;
    }

    public void waitForEnginesToComeOnline(long maxWaitTimeInMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            SymmetricEngineHolder engineHolder = ServletUtils.getSymmetricEngineHolder(servletContext);
            while (engineHolder.areEnginesStarting()) {
                AppUtils.sleep(500);
                if ((System.currentTimeMillis() - startTime) > maxWaitTimeInMs) {
                    throw new InterruptedException("Timed out waiting for engines to start");
                }
            }
        }
    }

    public void stop() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    public static void main(String[] args) throws Exception {
        new SymmetricWebServer().start(Integer.parseInt(DEFAULT_HTTP_PORT));
    }

    public boolean isJoin() {
        return join;
    }

    public void setJoin(boolean join) {
        this.join = join;
    }

    public void setWebHome(String webHome) {
        this.webHome = webHome;
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setMaxIdleTime(int maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpsPort(int httpsPort) {
        this.httpsPort = httpsPort;
    }

    public int getHttpsPort() {
        return httpsPort;
    }

    public void setPropertiesFile(String propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setWebAppDir(String webAppDir) {
        this.webAppDir = webAppDir;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setHttpEnabled(boolean httpEnabled) {
        this.httpEnabled = httpEnabled;
    }

    public boolean isHttpEnabled() {
        return httpEnabled;
    }

    public void setHttpsEnabled(boolean httpsEnabled) {
        this.httpsEnabled = httpsEnabled;
    }

    public boolean isHttpsEnabled() {
        return httpsEnabled;
    }

    public void setHttps2Enabled(boolean https2Enabled) {
        this.https2Enabled = https2Enabled;
    }

    public boolean isHttps2Enabled() {
        return https2Enabled;
    }

    public void setHttpsNeedClientAuth(boolean httpsNeedClientAuth) {
        this.httpsNeedClientAuth = httpsNeedClientAuth;
    }

    public boolean isHttpsNeedClientAuth() {
        return httpsNeedClientAuth;
    }

    public boolean isHttpsWantClientAuth() {
        return httpsWantClientAuth;
    }

    public void setHttpsWantClientAuth(boolean httpsWantClientAuth) {
        this.httpsWantClientAuth = httpsWantClientAuth;
    }
}
