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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

class HikariBuilderTest {
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String H2_URL = "jdbc:h2:mem:hikaribuildertest;DB_CLOSE_DELAY=-1";
    private HikariDataSource dataSource;

    @AfterEach
    void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    void buildReturnsHikariDataSource() {
        assertInstanceOf(HikariDataSource.class, build(buildH2Properties()));
    }

    @Test
    void buildSetsJdbcUrlAndDriver() {
        dataSource = build(buildH2Properties());
        assertEquals(H2_URL, dataSource.getJdbcUrl());
        assertEquals(H2_DRIVER, dataSource.getDriverClassName());
    }

    @Test
    void buildSetsMaximumPoolSize() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_MAX_ACTIVE, "7");
        dataSource = build(properties);
        assertEquals(7, dataSource.getMaximumPoolSize());
    }

    @Test
    void buildSetsMinimumIdleWhenPositive() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_MIN_IDLE, "3");
        dataSource = build(properties);
        assertEquals(3, dataSource.getMinimumIdle());
    }

    @Test
    void buildSetsConnectionTimeout() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_MAX_WAIT, "10000");
        dataSource = build(properties);
        assertEquals(10000, dataSource.getConnectionTimeout());
    }

    @Test
    void buildSetsValidationQuery() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_VALIDATION_QUERY, "select 1");
        dataSource = build(properties);
        assertEquals("select 1", dataSource.getConnectionTestQuery());
    }

    @Test
    void buildOmitsValidationQueryWhenBlank() {
        dataSource = build(buildH2Properties());
        assertNull(dataSource.getConnectionTestQuery());
    }

    @Test
    void buildSetsInitSql() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_INIT_SQL, "select 1");
        dataSource = build(properties);
        assertEquals("select 1", dataSource.getConnectionInitSql());
    }

    @Test
    void buildUsesFirstInitSqlStatementWhenMultiple() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_INIT_SQL, "select 1;select 2");
        dataSource = build(properties);
        assertEquals("select 1", dataSource.getConnectionInitSql());
    }

    @Test
    void buildSetsMaxLifetime() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_MAX_LIFETIME, "60000");
        dataSource = build(properties);
        assertEquals(60000, dataSource.getMaxLifetime());
    }

    @Test
    void buildSetsIdleTimeout() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_IDLE_TIMEOUT, "120000");
        dataSource = build(properties);
        assertEquals(120000, dataSource.getIdleTimeout());
    }

    @Test
    void buildSetsKeepaliveTime() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_KEEPALIVE_TIME, "60000");
        dataSource = build(properties);
        assertEquals(60000, dataSource.getKeepaliveTime());
    }

    @Test
    void buildSetsLeakDetectionThreshold() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_LEAK_DETECTION_THRESHOLD, "5000");
        dataSource = build(properties);
        assertEquals(5000, dataSource.getLeakDetectionThreshold());
    }

    private HikariDataSource build(TypedProperties properties) {
        return (HikariDataSource) new HikariBuilder().build(properties, H2_DRIVER, "sa", "");
    }

    private TypedProperties buildH2Properties() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(DataSourceProperties.DB_POOL_DRIVER, H2_DRIVER);
        properties.setProperty(DataSourceProperties.DB_POOL_URL, H2_URL);
        return properties;
    }
}
