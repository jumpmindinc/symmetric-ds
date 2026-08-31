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

import java.net.CookieHandler;
import java.net.CookieManager;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.security.SecurityServiceFactory.SecurityServiceType;
import org.jumpmind.symmetric.common.ServerConstants;
import org.jumpmind.symmetric.transport.TransportManagerFactory;
import org.jumpmind.symmetric.util.SymmetricUtils;
import org.jumpmind.symmetric.web.HealthServlet;
import org.jumpmind.symmetric.web.HttpMethodFilter;
import org.jumpmind.symmetric.web.SymmetricContextListener;
import org.jumpmind.symmetric.web.SymmetricServlet;
import org.jumpmind.symmetric.web.WebConstants;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import io.micrometer.common.util.StringUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

@SpringBootApplication(scanBasePackages = { "org.jumpmind.symmetric", "com.jumpmind.symmetric" })
public class SymmetricBoot extends SpringBootServletInitializer {
    private final Environment env;

    public SymmetricBoot(Environment env) {
        this.env = env;
    }

    @Bean
    ServletContextInitializer servletContextInitializer() {
        return new ServletContextInitializer() {
            @Override
            public void onStartup(ServletContext servletContext) throws ServletException {
                servletContext.setInitParameter(WebConstants.INIT_PARAM_AUTO_START, Boolean.toString(true));
                String singlePropertiesFile = AbstractCommandLauncher.getStartupParameterService().getString(ServerConstants.SERVER_SINGLE_PROPERTIES_FILE);
                if (StringUtils.isBlank(singlePropertiesFile)) {
                    singlePropertiesFile = env.getProperty("server.servlet.context-parameters." + WebConstants.INIT_SINGLE_SERVER_PROPERTIES_FILE);
                }
                if (StringUtils.isNotBlank(singlePropertiesFile)) {
                    servletContext.setInitParameter(WebConstants.INIT_SINGLE_SERVER_PROPERTIES_FILE, singlePropertiesFile);
                    servletContext.setInitParameter(WebConstants.INIT_PARAM_MULTI_SERVER_MODE, Boolean.toString(false));
                } else {
                    servletContext.setInitParameter(WebConstants.INIT_PARAM_MULTI_SERVER_MODE, Boolean.toString(true));
                }
            }
        };
    }

    @Bean
    SymmetricContextListener symmetricContextListener() {
        return new SymmetricContextListener();
    }

    @Bean
    ServletRegistrationBean<SymmetricServlet> symmetricServlet() {
        ServletRegistrationBean<SymmetricServlet> bean = new ServletRegistrationBean<>(new SymmetricServlet(),
                "/sync/*");
        bean.setLoadOnStartup(1);
        return bean;
    }

    @Bean
    ServletRegistrationBean<HealthServlet> healthServlet() {
        ServletRegistrationBean<HealthServlet> bean = new ServletRegistrationBean<>(new HealthServlet(), "/health/*");
        bean.setLoadOnStartup(1);
        return bean;
    }

    @Bean
    FilterRegistrationBean<HttpMethodFilter> httpMethodFilter() {
        FilterRegistrationBean<HttpMethodFilter> bean = new FilterRegistrationBean<HttpMethodFilter>();
        bean.setFilter(new HttpMethodFilter());
        bean.setAsyncSupported(true);
        bean.addUrlPatterns("/*");
        Map<String, String> param = new HashMap<String, String>();
        param.put(ServerConstants.SERVER_DISALLOW_HTTP_METHODS, System.getProperty(ServerConstants.SERVER_DISALLOW_HTTP_METHODS, "OPTIONS"));
        bean.setInitParameters(param);
        bean.setOrder(1);
        return bean;
    }

    public static ConfigurableApplicationContext run(String[] args) {
        SymmetricUtils.logNotices();
        TypedProperties sysProps = AbstractCommandLauncher.getStartupParameterService().asTypedProperties();
        boolean httpsEnabled = sysProps.is(ServerConstants.HTTPS_ENABLE);
        boolean https2Enabled = sysProps.is(ServerConstants.HTTPS2_ENABLE);
        boolean allowSelfSignedCerts = sysProps.is(ServerConstants.HTTPS_ALLOW_SELF_SIGNED_CERTS, true);
        String allowServerNames = sysProps.get(ServerConstants.HTTPS_VERIFIED_SERVERS, "all");
        TransportManagerFactory.initHttps(allowServerNames, allowSelfSignedCerts, https2Enabled);
        if (httpsEnabled) {
            ISecurityService securityService = SecurityServiceFactory.create(SecurityServiceType.SERVER, sysProps);
            securityService.installDefaultSslCert(sysProps.get(ServerConstants.HOST_BIND_NAME));
        }
        return new SpringApplicationBuilder().registerShutdownHook(false)
                .listeners(new SymmetricBootPropertySetupListener(), new SymmetricBootStartedListener())
                .bannerMode(Banner.Mode.OFF).sources(SymmetricBoot.class).run(args);
    }

    public static void main(String[] args) {
        run(args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        TypedProperties sysProps = AbstractCommandLauncher.getStartupParameterService().asTypedProperties();
        if (sysProps.is(ServerConstants.SERVER_HTTP_COOKIES_ENABLED)) {
            if (CookieHandler.getDefault() == null) {
                CookieHandler.setDefault(new CookieManager());
            }
        }
        return builder.sources(SymmetricBoot.class);
    }
}
