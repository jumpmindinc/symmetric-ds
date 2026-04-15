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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.security.SecurityConstants;
import org.jumpmind.security.SecurityServiceFactory;
import org.jumpmind.security.SecurityServiceFactory.SecurityServiceType;

public class DataSourceFactory {
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

    public static DataSource create(TypedProperties properties) {
        return create(properties, SecurityServiceFactory.create(SecurityServiceType.CLIENT, properties));
    }

    public static DataSource create(TypedProperties properties, ISecurityService securityService) {
        properties = properties.copy();
        properties.putAll(System.getProperties());
        String driverClassName = properties.get(DataSourceProperties.DB_POOL_DRIVER, null);
        try {
            prepareDriver(driverClassName);
        } catch (Exception e) {
            if (e instanceof ClassNotFoundException) {
                throw new IllegalStateException("Missing JDBC driver for '" + driverClassName
                        + "'.  Either provide the JAR or use 'symadmin module convert' command to find and install missing driver.", e);
            }
            if (e instanceof NotJdbcDriverException) {
                throw (NotJdbcDriverException) e;
            }
            throw new IllegalStateException("Had trouble registering the JDBC driver: " + driverClassName, e);
        }
        String user = properties.get(DataSourceProperties.DB_POOL_USER, "");
        if (user != null && user.startsWith(SecurityConstants.PREFIX_ENC)) {
            try {
                user = securityService.decrypt(user.substring(SecurityConstants.PREFIX_ENC.length()));
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to decrypt the database user from your engine properties file stored under the "
                        + DataSourceProperties.DB_POOL_USER + " property.   Please re-encrypt your user", ex);
            }
        }
        String password = properties.get(DataSourceProperties.DB_POOL_PASSWORD, "");
        if (password != null && password.startsWith(SecurityConstants.PREFIX_ENC)) {
            try {
                password = securityService.decrypt(password.substring(SecurityConstants.PREFIX_ENC.length()));
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to decrypt the database password from your engine properties file stored under the "
                        + DataSourceProperties.DB_POOL_PASSWORD + " property.   Please re-encrypt your password", ex);
            }
        }
        DataSourceBuilder builder = HikariBuilder.TYPE.equalsIgnoreCase(properties.get(DataSourceProperties.DB_POOL_TYPE, Dbcp2Builder.TYPE))
                ? new HikariBuilder()
                : new Dbcp2Builder();
        return builder.build(properties, driverClassName, user, password);
    }
}
