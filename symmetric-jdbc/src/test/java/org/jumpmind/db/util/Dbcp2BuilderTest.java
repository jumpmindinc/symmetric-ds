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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.sql.DataSource;

import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.Test;

class Dbcp2BuilderTest {
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String H2_URL = "jdbc:h2:mem:dbcp2buildertest";

    @Test
    void buildReturnsResettableBasicDataSource() {
        DataSource ds = new Dbcp2Builder().build(buildH2Properties(), H2_DRIVER, "sa", "");
        assertInstanceOf(ResettableBasicDataSource.class, ds);
    }

    @Test
    void buildSetsUrlDriver() {
        ResettableBasicDataSource ds = build(buildH2Properties(), "sa", "secret");
        assertEquals(H2_URL, ds.getUrl());
        assertEquals(H2_DRIVER, ds.getDriverClassName());
    }

    @Test
    void buildAppliesPoolSizeProperties() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_INITIAL_SIZE, "4");
        properties.setProperty(DataSourceProperties.DB_POOL_MAX_ACTIVE, "20");
        properties.setProperty(DataSourceProperties.DB_POOL_MAX_IDLE, "10");
        properties.setProperty(DataSourceProperties.DB_POOL_MIN_IDLE, "2");
        ResettableBasicDataSource ds = build(properties);
        assertEquals(4, ds.getInitialSize());
        assertEquals(20, ds.getMaxTotal());
        assertEquals(10, ds.getMaxIdle());
        assertEquals(2, ds.getMinIdle());
    }

    @Test
    void buildAppliesValidationQuery() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_VALIDATION_QUERY, "select 1");
        ResettableBasicDataSource ds = build(properties);
        assertEquals("select 1", ds.getValidationQuery());
    }

    @Test
    void buildAppliesTestOnBorrowReturnAndIdle() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_TEST_ON_BORROW, "false");
        properties.setProperty(DataSourceProperties.DB_POOL_TEST_ON_RETURN, "true");
        properties.setProperty(DataSourceProperties.DB_POOL_TEST_WHILE_IDLE, "true");
        ResettableBasicDataSource ds = build(properties);
        assertEquals(false, ds.getTestOnBorrow());
        assertEquals(true, ds.getTestOnReturn());
        assertEquals(true, ds.getTestWhileIdle());
    }

    @Test
    void buildParsesInitSql() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_INIT_SQL, "SET MODE MySQL;SET TRACE_LEVEL_FILE 0");
        ResettableBasicDataSource ds = build(properties);
        List<String> initSqls = ds.getConnectionInitSqls();
        assertEquals(2, initSqls.size());
        assertTrue(initSqls.contains("SET MODE MySQL"));
        assertTrue(initSqls.contains("SET TRACE_LEVEL_FILE 0"));
    }

    @Test
    void buildParsesInitSqlWithEscapedSemicolon() {
        TypedProperties properties = buildH2Properties();
        properties.setProperty(DataSourceProperties.DB_POOL_INIT_SQL, "SET OPTION 'a==b;;c==d'");
        ResettableBasicDataSource ds = build(properties);
        List<String> initSqls = ds.getConnectionInitSqls();
        assertEquals(1, initSqls.size());
        assertEquals("SET OPTION 'a==b;c==d'", initSqls.get(0));
    }

    private ResettableBasicDataSource build(TypedProperties properties) {
        return build(properties, "sa", "");
    }

    private ResettableBasicDataSource build(TypedProperties properties, String user, String password) {
        return (ResettableBasicDataSource) new Dbcp2Builder().build(properties, H2_DRIVER, user, password);
    }

    private TypedProperties buildH2Properties() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty(DataSourceProperties.DB_POOL_DRIVER, H2_DRIVER);
        properties.setProperty(DataSourceProperties.DB_POOL_URL, H2_URL);
        return properties;
    }
}
