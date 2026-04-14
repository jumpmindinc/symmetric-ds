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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.dbcp2.BasicDataSource;

import javax.sql.DataSource;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.Test;

class DataSourceFactoryTest {
    @Test
    public void createReturnsDataSource() {
        TypedProperties properties = buildH2Properties();
        DataSource ds = DataSourceFactory.create(properties);
        assertNotNull(ds);
        assertInstanceOf(DataSource.class, ds);
    }

    @Test
    public void createReturnsResettableBasicDataSource() {
        TypedProperties properties = buildH2Properties();
        DataSource ds = DataSourceFactory.create(properties);
        assertInstanceOf(ResettableBasicDataSource.class, ds);
    }

    @Test
    public void createAppliesPoolProperties() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_INITIAL_SIZE, "3");
        properties.setProperty(DataSourceProperties.DB_POOL_MAX_ACTIVE, "15");
        properties.setProperty(DataSourceProperties.DB_POOL_MAX_IDLE, "5");
        ResettableBasicDataSource ds = (ResettableBasicDataSource) DataSourceFactory.create(properties);
        assertEquals(3, ds.getInitialSize());
        assertEquals(15, ds.getMaxTotal());
        assertEquals(5, ds.getMaxIdle());
    }

    @Test
    public void createThrowsOnMissingDriver() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(DataSourceProperties.DB_POOL_DRIVER, "com.example.NonExistentDriver");
        properties.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:nonexistent://localhost/test");
        assertThrows(IllegalStateException.class, () -> DataSourceFactory.create(properties));
    }

    @Test
    public void createWithHikariDisabled_ReturnsDbcp2DataSource() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_HIKARI_ENABLE, "false");
        DataSource ds = DataSourceFactory.create(properties);
        assertInstanceOf(BasicDataSource.class, ds);
    }

    @Test
    public void createWithHikariEnabled() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_HIKARI_ENABLE, "true");
        assertThrows(UnsupportedOperationException.class, () -> DataSourceFactory.create(properties));
    }

    @Test
    public void createThrowsOnNonDriverClass() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(DataSourceProperties.DB_POOL_DRIVER, "java.lang.String");
        properties.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:h2:mem:test");
        assertThrows(NotJdbcDriverException.class, () -> DataSourceFactory.create(properties));
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
