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

import java.util.function.LongConsumer;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.properties.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class HikariBuilder extends DataSourceBuilder {
    private static final Logger log = LoggerFactory.getLogger(HikariBuilder.class);
    static final String TYPE = "hikari";

    @Override
    public DataSource build(TypedProperties properties, String driverClassName, String user, String password) {
        log.warn("The following properties are not supported by HikariCP and will be ignored: {}, {}, {}, {}, {}, {}",
                DataSourceProperties.DB_POOL_INITIAL_SIZE,
                DataSourceProperties.DB_POOL_MAX_IDLE,
                DataSourceProperties.DB_POOL_TEST_ON_BORROW,
                DataSourceProperties.DB_POOL_TEST_ON_RETURN,
                DataSourceProperties.DB_POOL_TEST_WHILE_IDLE,
                DataSourceProperties.DB_POOL_MIN_EVICTABLE_IDLE_TIME_MILLIS);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.get(DataSourceProperties.DB_POOL_URL, null));
        config.setDriverClassName(driverClassName);
        if (StringUtils.isNotEmpty(user)) {
            config.setUsername(user);
        }
        if (StringUtils.isNotEmpty(password)) {
            config.setPassword(password);
        }
        config.setMaximumPoolSize(properties.getInt(DataSourceProperties.DB_POOL_MAX_ACTIVE, 10));
        int minIdle = properties.getInt(DataSourceProperties.DB_POOL_MIN_IDLE, 0);
        if (minIdle > 0) {
            config.setMinimumIdle(minIdle);
        }
        config.setConnectionTimeout(properties.getInt(DataSourceProperties.DB_POOL_MAX_WAIT, 30000));
        String validationQuery = properties.get(DataSourceProperties.DB_POOL_VALIDATION_QUERY, null);
        if (StringUtils.isNotBlank(validationQuery)) {
            config.setConnectionTestQuery(validationQuery);
        }
        applyInitSql(config, properties.get(DataSourceProperties.DB_POOL_INIT_SQL, null));
        parseConnectionProperties(properties.get(DataSourceProperties.DB_POOL_CONNECTION_PROPERTIES, null))
                .forEach((key, value) -> {
                    log.info("Setting HikariCP data source property {} to {}", key, value);
                    config.addDataSourceProperty(key, value);
                });
        applyLong(properties, DataSourceProperties.DB_POOL_MAX_LIFETIME, config::setMaxLifetime);
        applyLong(properties, DataSourceProperties.DB_POOL_IDLE_TIMEOUT, config::setIdleTimeout);
        applyLong(properties, DataSourceProperties.DB_POOL_KEEPALIVE_TIME, config::setKeepaliveTime);
        applyLong(properties, DataSourceProperties.DB_POOL_LEAK_DETECTION_THRESHOLD, config::setLeakDetectionThreshold);
        return new HikariDataSource(config);
    }

    private void applyInitSql(HikariConfig config, String initSql) {
        String[] statements = splitInitSql(initSql);
        if (statements.length == 0) {
            return;
        }
        if (statements.length > 1) {
            log.warn("{} contains multiple SQL statements; HikariCP only supports a single connectionInitSql — using the first statement only",
                    DataSourceProperties.DB_POOL_INIT_SQL);
        }
        config.setConnectionInitSql(statements[0]);
    }

    /**
     * Reads a long value from properties and passes it to the setter only if the property is explicitly set.
     */
    private void applyLong(TypedProperties properties, String key, LongConsumer setter) {
        String value = properties.get(key, null);
        if (StringUtils.isNotBlank(value)) {
            setter.accept(Long.parseLong(value.trim()));
        }
    }
}
