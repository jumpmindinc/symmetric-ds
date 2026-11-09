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

import javax.sql.DataSource;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

class DataSourceFactoryClickHouseTest {
    @AfterEach
    void tearDown() {
        // The map is shared by every data source in the JVM, so leave it as it was found
        DataSourceFactory.requiredConnectionProperties.remove("compress");
        DataSourceFactory.requiredConnectionProperties.remove("IGNORECASE");
    }

    @Test
    void testPrepareDriver_forClickHouse_turnsOffCompression() throws Exception {
        assertFalse(DataSourceFactory.requiredConnectionProperties.containsKey("compress"));
        DataSourceFactory.prepareDriver("com.clickhouse.jdbc.ClickHouseDriver");
        assertEquals("false", DataSourceFactory.requiredConnectionProperties.get("compress"));
    }

    @Test
    void testPrepareDriver_forOtherDrivers_doesNotAddCompression() throws Exception {
        DataSourceFactory.prepareDriver("org.h2.Driver");
        assertFalse(DataSourceFactory.requiredConnectionProperties.containsKey("compress"));
    }

    @Test
    void testHikariBuilder_appliesRequiredConnectionProperties() {
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
}
