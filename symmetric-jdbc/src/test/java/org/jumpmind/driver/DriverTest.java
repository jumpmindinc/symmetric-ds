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
package org.jumpmind.driver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jumpmind.properties.TypedProperties;
import org.slf4j.MDC;

class DriverTest {
    private static final String ENGINE_NAME_MDC_KEY = "engineName";
    private Driver driver;

    @BeforeEach
    void setUp() {
        driver = new Driver();
    }

    @AfterEach
    void tearDown() {
        MDC.remove(ENGINE_NAME_MDC_KEY);
    }

    @Test
    void testRegister_withNullProperties_doesNotThrow() {
        assertDoesNotThrow(() -> Driver.register(null));
    }

    @Test
    void testRegister_withNullProperties_doesNotModifyEngineProperties() throws Exception {
        Map<String, TypedProperties> allEngineProperties = getAllEngineProperties();
        int sizeBefore = allEngineProperties.size();
        int driverCountBefore = countRegisteredDrivers();
        Driver.register(null);
        assertEquals(sizeBefore, allEngineProperties.size());
        assertEquals(driverCountBefore + 1, countRegisteredDrivers());
    }

    @Test
    void testRegister_withMissingEngineName_storesPropertiesUnderNullKey() throws Exception {
        Map<String, TypedProperties> allEngineProperties = getAllEngineProperties();
        TypedProperties properties = new TypedProperties();
        properties.setProperty("some.other.key", "value");
        Driver.register(properties);
        assertSame(properties, allEngineProperties.get(null));
    }

    @Test
    void testRegister_withProperties_engineNamePropertiesAvailableOnConnect() throws SQLException {
        TypedProperties properties = new TypedProperties();
        properties.setProperty("engine.name", "registerEngine");
        Driver.register(properties);
        MDC.put(ENGINE_NAME_MDC_KEY, "registerEngine");
        Connection connection = driver.connect("jdbc:symds:h2:mem:driverTestRegister;DB_CLOSE_DELAY=-1", new Properties());
        try {
            assertNotNull(connection);
            assertTrue(connection instanceof ConnectionWrapper);
            assertSame(properties, ((ConnectionWrapper) connection).getEngineProperties());
        } finally {
            connection.close();
        }
    }

    @Test
    void testConnect_withNullUrl_returnsNull() throws SQLException {
        assertNull(driver.connect(null, new Properties()));
    }

    @Test
    void testConnect_withNonSymdsUrl_returnsNull() throws SQLException {
        assertNull(driver.connect("jdbc:h2:mem:driverTestNonSymds", new Properties()));
    }

    @Test
    void testConnect_withLowercaseSymdsUrl_returnsConnectionWrapperWithNullEngineProperties() throws SQLException {
        Connection connection = driver.connect("jdbc:symds:h2:mem:driverTestLowercase;DB_CLOSE_DELAY=-1", new Properties());
        try {
            assertNotNull(connection);
            assertTrue(connection instanceof ConnectionWrapper);
            assertNull(((ConnectionWrapper) connection).getEngineProperties());
        } finally {
            connection.close();
        }
    }

    // Defect pinned, not endorsed: getRealUrl() calls url.replace("symds:", "") on the original,
    // non-lowercased url, so an uppercase "SYMDS:" prefix is never stripped. The unchanged url is
    // then handed to DriverManager.getConnection(), which re-enters this same Driver's connect()
    // because its prefix check is case-insensitive, recursing without bound until the stack overflows.
    // Every other registered driver is deregistered for the test: DriverManager.getConnection() calls
    // connect() on every registered driver (not just ones whose acceptsURL matches) until one succeeds,
    // and a third-party driver throwing something other than SQLException for this malformed url (e.g.
    // PatternSyntaxException) would abort the recursion early — observed as an environment-dependent
    // flake (passed locally, failed in CI) since driver registration order/contents vary by environment.
    @Test
    void testConnect_withUppercaseSymdsUrl_doesNotStripPrefixAndOverflowsStack() throws SQLException {
        List<java.sql.Driver> otherDrivers = deregisterOtherDrivers();
        try {
            assertThrows(StackOverflowError.class,
                    () -> driver.connect("JDBC:SYMDS:h2:mem:driverTestUppercase;DB_CLOSE_DELAY=-1", new Properties()));
        } finally {
            for (java.sql.Driver otherDriver : otherDrivers) {
                DriverManager.registerDriver(otherDriver);
            }
        }
    }

    @Test
    void testConnect_withMdcEngineNameUnregistered_engineHasNullProperties() throws SQLException {
        MDC.put(ENGINE_NAME_MDC_KEY, "neverRegisteredEngine");
        Connection connection = driver.connect("jdbc:symds:h2:mem:driverTestUnregistered;DB_CLOSE_DELAY=-1", new Properties());
        try {
            assertNotNull(connection);
            assertNull(((ConnectionWrapper) connection).getEngineProperties());
        } finally {
            connection.close();
        }
    }

    @Test
    void testGetRealUrl_stripsLowercaseSymdsPrefix() throws Exception {
        Method getRealUrl = Driver.class.getDeclaredMethod("getRealUrl", String.class);
        getRealUrl.setAccessible(true);
        assertEquals("jdbc:h2:mem:testdb", getRealUrl.invoke(driver, "jdbc:symds:h2:mem:testdb"));
        assertEquals("jdbc:jtds:sqlserver://host/db", getRealUrl.invoke(driver, "jdbc:symds:jtds:sqlserver://host/db"));
    }

    @Test
    void testAcceptsURL_withNullUrl_returnsFalse() throws SQLException {
        assertFalse(driver.acceptsURL(null));
    }

    @Test
    void testAcceptsURL_withLowercaseSymdsUrl_returnsTrue() throws SQLException {
        assertTrue(driver.acceptsURL("jdbc:symds:h2:mem:driverTestAccepts"));
    }

    @Test
    void testAcceptsURL_withUppercaseSymdsUrl_returnsTrue() throws SQLException {
        assertTrue(driver.acceptsURL("JDBC:SYMDS:H2:MEM:DRIVERTESTACCEPTS"));
    }

    @Test
    void testAcceptsURL_withNonSymdsUrl_returnsFalse() throws SQLException {
        assertFalse(driver.acceptsURL("jdbc:h2:mem:driverTestAccepts"));
    }

    @Test
    void testGetPropertyInfo_returnsNull() throws SQLException {
        assertNull(driver.getPropertyInfo("jdbc:symds:h2:mem:driverTestPropertyInfo", new Properties()));
        assertNull(driver.getPropertyInfo(null, null));
    }

    @Test
    void testGetMajorVersion_returnsOne() {
        assertEquals(1, driver.getMajorVersion());
    }

    @Test
    void testGetMinorVersion_returnsZero() {
        assertEquals(0, driver.getMinorVersion());
    }

    @Test
    void testJdbcCompliant_returnsFalse() {
        assertFalse(driver.jdbcCompliant());
    }

    @Test
    void testGetParentLogger_returnsNull() throws SQLFeatureNotSupportedException {
        assertNull(driver.getParentLogger());
    }

    @SuppressWarnings("unchecked")
    private Map<String, TypedProperties> getAllEngineProperties() throws Exception {
        Field field = Driver.class.getDeclaredField("allEngineProperties");
        field.setAccessible(true);
        return (Map<String, TypedProperties>) field.get(null);
    }

    private int countRegisteredDrivers() {
        int count = 0;
        Enumeration<java.sql.Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            if (drivers.nextElement() instanceof Driver) {
                count++;
            }
        }
        return count;
    }

    private List<java.sql.Driver> deregisterOtherDrivers() throws SQLException {
        List<java.sql.Driver> otherDrivers = new ArrayList<>();
        Enumeration<java.sql.Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            java.sql.Driver registeredDriver = drivers.nextElement();
            if (!(registeredDriver instanceof Driver)) {
                otherDrivers.add(registeredDriver);
            }
        }
        for (java.sql.Driver otherDriver : otherDrivers) {
            DriverManager.deregisterDriver(otherDriver);
        }
        return otherDrivers;
    }
}
