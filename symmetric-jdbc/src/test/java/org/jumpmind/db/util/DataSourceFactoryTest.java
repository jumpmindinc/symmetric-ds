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
package org.jumpmind.db.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

class DataSourceFactoryTest {
    @Test
    void createReturnsDataSource() {
        TypedProperties properties = buildH2Properties();
        DataSource ds = DataSourceFactory.create(properties);
        assertNotNull(ds);
        assertInstanceOf(DataSource.class, ds);
    }

    @Test
    void createWithDefault() {
        TypedProperties properties = buildH2Properties();
        DataSource ds = DataSourceFactory.create(properties);
        assertInstanceOf(HikariDataSource.class, ds);
    }

    @Test
    void createAppliesPoolProperties() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_TYPE, Dbcp2Builder.TYPE);
        properties.setProperty(DataSourceProperties.DB_POOL_INITIAL_SIZE, "3");
        properties.setProperty(DataSourceProperties.DB_POOL_MAX_ACTIVE, "15");
        properties.setProperty(DataSourceProperties.DB_POOL_MAX_IDLE, "5");
        ResettableBasicDataSource ds = (ResettableBasicDataSource) DataSourceFactory.create(properties);
        assertEquals(3, ds.getInitialSize());
        assertEquals(15, ds.getMaxTotal());
        assertEquals(5, ds.getMaxIdle());
    }

    @Test
    void createThrowsOnMissingDriver() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(DataSourceProperties.DB_POOL_DRIVER, "com.example.NonExistentDriver");
        properties.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:nonexistent://localhost/test");
        assertThrows(IllegalStateException.class, () -> DataSourceFactory.create(properties));
    }

    @Test
    void createWithDbcp2() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_TYPE, Dbcp2Builder.TYPE);
        DataSource ds = DataSourceFactory.create(properties);
        assertInstanceOf(BasicDataSource.class, ds);
    }

    @Test
    void createWithHikari() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_TYPE, HikariBuilder.TYPE);
        DataSource ds = DataSourceFactory.create(properties);
        assertInstanceOf(HikariDataSource.class, ds);
    }

    @Test
    void createThrowsOnNonDriverClass() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(DataSourceProperties.DB_POOL_DRIVER, "java.lang.String");
        properties.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:h2:mem:test");
        assertThrows(NotJdbcDriverException.class, () -> DataSourceFactory.create(properties));
    }

    @AfterEach
    void removeRequiredConnectionProperties() {
        // The map is shared by every data source in the JVM, so leave it as it was found
        DataSourceFactory.requiredConnectionProperties.remove("compress");
        DataSourceFactory.requiredConnectionProperties.remove("IGNORECASE");
    }

    @Test
    void prepareDriverForClickHouseTurnsOffCompression() throws Exception {
        assertFalse(DataSourceFactory.requiredConnectionProperties.containsKey("compress"));
        DataSourceFactory.prepareDriver("com.clickhouse.jdbc.ClickHouseDriver");
        assertEquals("false", DataSourceFactory.requiredConnectionProperties.get("compress"));
    }

    @Test
    void prepareDriverForOtherDriversDoesNotAddCompression() throws Exception {
        DataSourceFactory.prepareDriver("org.h2.Driver");
        assertFalse(DataSourceFactory.requiredConnectionProperties.containsKey("compress"));
    }

    @Test
    void hikariBuilderAppliesRequiredConnectionProperties() {
        DataSourceFactory.requiredConnectionProperties.put("IGNORECASE", "TRUE");
        TypedProperties properties = new TypedProperties();
        properties.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:h2:mem:required_props_test");
        DataSource dataSource = new HikariBuilder().build(properties, "org.h2.Driver", "sa", "");
        try {
            assertEquals("TRUE", ((HikariDataSource) dataSource).getDataSourceProperties().getProperty("IGNORECASE"));
        } finally {
            ((HikariDataSource) dataSource).close();
        }
    }

    private TypedProperties buildH2Properties() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(DataSourceProperties.DB_POOL_DRIVER, "org.h2.Driver");
        properties.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:h2:mem:datasourcefactorytest");
        properties.setProperty(DataSourceProperties.DB_POOL_USER, "sa");
        properties.setProperty(DataSourceProperties.DB_POOL_PASSWORD, "");
        return properties;
    }
}
