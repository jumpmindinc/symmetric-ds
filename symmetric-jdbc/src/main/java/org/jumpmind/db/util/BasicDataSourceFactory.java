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
package org.jumpmind.db.util;

import java.sql.Driver;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.security.SecurityServiceFactory.SecurityServiceType;
import org.slf4j.LoggerFactory;

public class BasicDataSourceFactory {
    protected static Map<String, String> requiredConnectionProperties = new HashMap<String, String>();

    public static void prepareDriver(String clazzName) throws Exception {
        Class<?> clazz = Class.forName(clazzName);
        if (!Driver.class.isAssignableFrom(clazz)) {
            throw new NotJdbcDriverException(clazzName + " is not a JDBC driver");
        }
        Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
        synchronized (DriverManager.class) {
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver driver2 = (Driver) drivers.nextElement();
                /*
                 * MySQL and Maria DB drivers cannot co-exist because they use the same JDBC URL.
                 */
                if ((driver.getClass().getName().equals("com.mysql.jdbc.Driver") &&
                        driver2.getClass().getName().equals("org.mariadb.jdbc.Driver")) ||
                        (driver.getClass().getName().equals("org.mariadb.jdbc.Driver") &&
                                driver2.getClass().getName().equals("com.mysql.jdbc.Driver"))) {
                    DriverManager.deregisterDriver(driver2);
                }
            }
        }
        if (clazzName.equals("org.firebirdsql.jdbc.FBDriver")) {
            requiredConnectionProperties.put("columnLabelForName", "true");
        }
    }

    public static ResettableBasicDataSource create(TypedProperties properties) {
        return create(properties, SecurityServiceFactory.create(SecurityServiceType.CLIENT, properties));
    }

    public static ResettableBasicDataSource create(TypedProperties properties,
            ISecurityService securityService) {
        properties = properties.copy();
        properties.putAll(System.getProperties());
        ResettableBasicDataSource dataSource = new ResettableBasicDataSource();
        dataSource.setDriverClassName(properties.get(
                DataSourceProperties.DB_POOL_DRIVER, null));
        try {
            prepareDriver(dataSource.getDriverClassName());
        } catch (Exception e) {
            if (e instanceof ClassNotFoundException) {
                throw new IllegalStateException("Missing JDBC driver for '" + dataSource.getDriverClassName()
                        + "'.  Either provide the JAR or use 'symadmin module convert' command to find and install missing driver.", e);
            }
            if (e instanceof NotJdbcDriverException) {
                throw (NotJdbcDriverException) e;
            }
            throw new IllegalStateException("Had trouble registering the JDBC driver: " + dataSource.getDriverClassName(), e);
        }
        dataSource.setUrl(properties.get(DataSourceProperties.DB_POOL_URL, null));
        String user = properties.get(DataSourceProperties.DB_POOL_USER, "");
        if (user != null && user.startsWith(SecurityConstants.PREFIX_ENC)) {
            try {
                user = securityService.decrypt(user.substring(SecurityConstants.PREFIX_ENC.length()));
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to decrypt the database user from your engine properties file stored under the "
                        + DataSourceProperties.DB_POOL_USER + " property.   Please re-encrypt your user", ex);
            }
        }
        if (!StringUtils.isEmpty(user)) {
            dataSource.setUsername(user);
        }
        String password = properties.get(DataSourceProperties.DB_POOL_PASSWORD, "");
        if (password != null && password.startsWith(SecurityConstants.PREFIX_ENC)) {
            try {
                password = securityService.decrypt(password.substring(SecurityConstants.PREFIX_ENC
                        .length()));
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to decrypt the database password from your engine properties file stored under the "
                        + DataSourceProperties.DB_POOL_PASSWORD + " property.   Please re-encrypt your password", ex);
            }
        }
        if (!StringUtils.isEmpty(password)) {
            dataSource.setPassword(password);
        }
        dataSource.setInitialSize(properties.getInt(
                DataSourceProperties.DB_POOL_INITIAL_SIZE, 2));
        dataSource.setMaxTotal(properties.getInt(
                DataSourceProperties.DB_POOL_MAX_ACTIVE, 10));
        dataSource.setMaxWait(
                Duration.ofMillis(properties.getInt(DataSourceProperties.DB_POOL_MAX_WAIT, 5000)));
        dataSource.setMaxIdle(properties.getInt(DataSourceProperties.DB_POOL_MAX_IDLE,
                8));
        dataSource.setMinIdle(properties.getInt(DataSourceProperties.DB_POOL_MIN_IDLE,
                0));
        dataSource.setMinEvictableIdle(Duration.ofMillis(
                properties.getInt(DataSourceProperties.DB_POOL_MIN_EVICTABLE_IDLE_TIME_MILLIS, 60000)));
        dataSource.setDurationBetweenEvictionRuns(Duration.ofMillis(120000));
        dataSource.setNumTestsPerEvictionRun(10);
        dataSource.setValidationQuery(properties.get(
                DataSourceProperties.DB_POOL_VALIDATION_QUERY, null));
        dataSource.setTestOnBorrow(properties.is(
                DataSourceProperties.DB_POOL_TEST_ON_BORROW, true));
        dataSource.setTestOnReturn(properties.is(
                DataSourceProperties.DB_POOL_TEST_ON_RETURN, false));
        dataSource.setTestWhileIdle(properties.is(
                DataSourceProperties.DB_POOL_TEST_WHILE_IDLE, false));
        String connectionProperties = properties.get(
                DataSourceProperties.DB_POOL_CONNECTION_PROPERTIES, null);
        if (StringUtils.isNotBlank(connectionProperties)) {
            String[] tokens = connectionProperties.split(";");
            for (String property : tokens) {
                String[] keyValue = property.replaceAll("==", "!!").split("=");
                if (keyValue != null && keyValue.length > 1) {
                    keyValue[1] = keyValue[1].replaceAll("!!", "=");
                    LoggerFactory.getLogger(BasicDataSourceFactory.class).info(
                            "Setting database connection property {} to {}", keyValue[0], keyValue[1]);
                    dataSource.addConnectionProperty(keyValue[0], keyValue[1]);
                }
            }
        }
        for (String key : requiredConnectionProperties.keySet()) {
            String value = requiredConnectionProperties.get(key);
            LoggerFactory.getLogger(BasicDataSourceFactory.class).info(
                    "Setting required database connection property {}={}", key, value);
            dataSource.addConnectionProperty(key, value);
        }
        String initSql = properties.get(DataSourceProperties.DB_POOL_INIT_SQL, null);
        if (StringUtils.isNotBlank(initSql)) {
            List<String> initSqlList = new ArrayList<String>(1);
            initSql = initSql.replaceAll(";;", "!!");
            for (String i : initSql.split(";")) {
                i = i.replaceAll("!!", ";");
                initSqlList.add(i);
            }
            dataSource.setConnectionInitSqls(initSqlList);
        }
        return dataSource;
    }
}
