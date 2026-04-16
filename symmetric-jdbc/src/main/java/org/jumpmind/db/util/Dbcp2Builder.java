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

import java.time.Duration;
import java.util.Arrays;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.properties.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Dbcp2Builder extends DataSourceBuilder {
    private static final Logger log = LoggerFactory.getLogger(Dbcp2Builder.class);
    static final String TYPE = "dbcp2";

    @Override
    public DataSource build(TypedProperties properties, String driverClassName, String user, String password) {
        ResettableBasicDataSource dataSource = new ResettableBasicDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(properties.get(DataSourceProperties.DB_POOL_URL, null));
        if (!StringUtils.isEmpty(user)) {
            dataSource.setUsername(user);
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
        dataSource.setMaxIdle(properties.getInt(DataSourceProperties.DB_POOL_MAX_IDLE, 8));
        dataSource.setMinIdle(properties.getInt(DataSourceProperties.DB_POOL_MIN_IDLE, 0));
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
        parseConnectionProperties(properties.get(DataSourceProperties.DB_POOL_CONNECTION_PROPERTIES, null))
                .forEach((key, value) -> {
                    log.info("Setting database connection property {} to {}", key, value);
                    dataSource.addConnectionProperty(key, value);
                });
        for (String key : DataSourceFactory.requiredConnectionProperties.keySet()) {
            String value = DataSourceFactory.requiredConnectionProperties.get(key);
            log.info("Setting required database connection property {}={}", key, value);
            dataSource.addConnectionProperty(key, value);
        }
        String[] initSqlStatements = splitInitSql(properties.get(DataSourceProperties.DB_POOL_INIT_SQL, null));
        if (initSqlStatements.length > 0) {
            dataSource.setConnectionInitSqls(Arrays.asList(initSqlStatements));
        }
        return dataSource;
    }
}
