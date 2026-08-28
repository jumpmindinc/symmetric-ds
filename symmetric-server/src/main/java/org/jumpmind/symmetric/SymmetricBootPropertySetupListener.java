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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.security.SecurityServiceFactory.SecurityServiceType;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.web.WebConstants;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

public class SymmetricBootPropertySetupListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        TypedProperties sysProps = new TypedProperties(System.getProperties());
        boolean httpEnabled = sysProps.is(ServerConstants.HTTP_ENABLE, true);
        boolean httpsEnabled = sysProps.is(ServerConstants.HTTPS_ENABLE);
        int httpPort = sysProps.getInt(ServerConstants.HTTP_PORT, Integer.parseInt(SymmetricWebServer.DEFAULT_HTTP_PORT));
        int httpsPort = sysProps.getInt(ServerConstants.HTTPS_PORT, Integer.parseInt(SymmetricWebServer.DEFAULT_HTTPS_PORT));
        ConfigurableEnvironment environment = event.getEnvironment();
        Properties bootProps = new Properties();
        setIfNotBlank(ServerConstants.HOST_BIND_NAME, "server.address", sysProps, bootProps);
        setIfNotBlank(ServerConstants.SERVER_ACCESS_LOG_ENABLED, "server.jetty.accesslog.enabled", sysProps, bootProps);
        setIfNotBlank(ServerConstants.SERVER_ACCESS_LOG_FILE, "server.jetty.accesslog.filename", sysProps, bootProps);
        setIfNotBlank(ServerConstants.SERVER_SERVLET_CONTEXT_PATH, "server.servlet.context-path", sysProps, bootProps);
        setIfNotBlank(ServerConstants.SERVER_COOKIE_NAME, "server.servlet.session.cookie.name", sysProps, bootProps,
                getCookieName(httpEnabled, httpsEnabled, httpPort, httpsPort));
        setIfNotBlank("org.eclipse.jetty.server.Request.maxFormContentSize", "server.jetty.max-http-form-post-size", sysProps, bootProps);
        setIfNotBlank("org.eclipse.jetty.server.Request.maxFormKeys", "server.jetty.max-form-keys", sysProps, bootProps);
        bootProps.put("server.jetty.connection-idle-timeout", sysProps.getInt(ServerConstants.SERVER_CONNECTION_IDLE_TIMEOUT,
                SymmetricWebServer.DEFAULT_MAX_IDLE_TIME));
        bootProps.put("server.servlet.jsp.init-parameters.listings", sysProps.is(ServerConstants.SERVER_ALLOW_DIR_LISTING));
        bootProps.put("server.servlet.session.cookie.http-only", Boolean.toString(true));
        bootProps.put("spring.web.resources.static-locations", "classpath:[/META-INF/resources/,/resources/,/static/],file:" +
                sysProps.get(SystemConstants.SYSPROP_WEB_DIR, "web"));
        if (sysProps.is(ServerConstants.HTTPS_NEED_CLIENT_AUTH)) {
            bootProps.put("server.ssl.client-auth", "need");
        } else if (sysProps.is(ServerConstants.HTTPS_WANT_CLIENT_AUTH)) {
            bootProps.put("server.ssl.client-auth", "want");
        }
        if (httpEnabled && !httpsEnabled) {
            bootProps.put("server.port", String.valueOf(httpPort));
        } else if (httpsEnabled) {
            bootProps.put("server.port", String.valueOf(httpsPort));
            bootProps.put("server.ssl.enabled", Boolean.toString(true));
            setIfNotBlank(ServerConstants.HTTPS2_ENABLE, "server.http2.enabled", sysProps, bootProps);
            if (!httpEnabled) {
                bootProps.setProperty("server.servlet.session.cookie.secure", Boolean.toString(true));
            }
            ISecurityService securityService = SecurityServiceFactory.create(SecurityServiceType.SERVER, sysProps);
            bootProps.put("server.ssl.key-store", sysProps.get(SecurityConstants.SYSPROP_KEYSTORE));
            bootProps.put("server.ssl.key-store-password", StringUtils.defaultIfBlank(securityService.unobfuscateIfNeeded(
                    SecurityConstants.SYSPROP_KEYSTORE_PASSWORD), SecurityConstants.KEYSTORE_PASSWORD));
            bootProps.put("server.ssl.key-store-type", securityService.getKeyStore().getType());
            bootProps.put("server.ssl.key-alias", sysProps.get(SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS,
                    SecurityConstants.ALIAS_SYM_PRIVATE_KEY));
            String trustStore = sysProps.get(SecurityConstants.SYSPROP_TRUSTSTORE);
            if (StringUtils.isNotBlank(trustStore)) {
                bootProps.put("server.ssl.trust-store", trustStore);
                bootProps.put("server.ssl.trust-store-password", StringUtils.defaultIfBlank(securityService.unobfuscateIfNeeded(
                        SecurityConstants.SYSPROP_TRUSTSTORE_PASSWORD), SecurityConstants.KEYSTORE_PASSWORD));
                bootProps.put("server.ssl.trust-store-type", SecurityConstants.KEYSTORE_TYPE_JKS);
            }
        }
        environment.getPropertySources().addFirst(new PropertiesPropertySource("symBootProps", bootProps));
        if (sysProps.is(ServerConstants.SERVER_HTTP_COOKIES_ENABLED)) {
            if (CookieHandler.getDefault() == null) {
                CookieHandler.setDefault(new CookieManager());
            }
        }
    }

    protected static String getCookieName(boolean httpEnable, boolean httpsEnable, int httpPort, int httpsPort) {
        StringBuilder sb = new StringBuilder(WebConstants.SESSION_PREFIX);
        if (httpsEnable) {
            sb.append(httpsPort);
        }
        if (httpEnable) {
            sb.append("_").append(httpPort);
        }
        return sb.toString();
    }

    protected void setIfNotBlank(String sysName, String bootName, TypedProperties sysProps, Properties bootProps) {
        String value = sysProps.getProperty(sysName);
        if (isNotBlank(value)) {
            bootProps.put(bootName, value);
        }
    }

    protected void setIfNotBlank(String sysName, String bootName, TypedProperties sysProps, Properties bootProps, String defaultValue) {
        String value = sysProps.getProperty(sysName);
        if (isNotBlank(value)) {
            bootProps.put(bootName, value);
        } else {
            bootProps.put(bootName, defaultValue);
        }
    }
}
