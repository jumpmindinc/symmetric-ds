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

/**
 * @deprecated Use {@link DataSourceProperties} instead.
 */
@Deprecated(forRemoval = true)
final public class BasicDataSourcePropertyConstants {
    /* Unused -- will be removed with the class. */
    public static final String ALL = "ALL";

    private BasicDataSourcePropertyConstants() {
    }

    public final static String DB_POOL_URL = DataSourceProperties.DB_POOL_URL;
    public final static String DB_POOL_DRIVER = DataSourceProperties.DB_POOL_DRIVER;
    public final static String DB_POOL_USER = DataSourceProperties.DB_POOL_USER;
    public final static String DB_POOL_PASSWORD = DataSourceProperties.DB_POOL_PASSWORD;
    public final static String DB_POOL_INITIAL_SIZE = DataSourceProperties.DB_POOL_INITIAL_SIZE;
    public final static String DB_POOL_MAX_ACTIVE = DataSourceProperties.DB_POOL_MAX_ACTIVE;
    public final static String DB_POOL_MAX_IDLE = DataSourceProperties.DB_POOL_MAX_IDLE;
    public final static String DB_POOL_MIN_IDLE = DataSourceProperties.DB_POOL_MIN_IDLE;
    public final static String DB_POOL_MAX_WAIT = DataSourceProperties.DB_POOL_MAX_WAIT;
    public final static String DB_POOL_MIN_EVICTABLE_IDLE_TIME_MILLIS = DataSourceProperties.DB_POOL_MIN_EVICTABLE_IDLE_TIME_MILLIS;
    public final static String DB_POOL_VALIDATION_QUERY = DataSourceProperties.DB_POOL_VALIDATION_QUERY;
    public final static String DB_POOL_TEST_ON_BORROW = DataSourceProperties.DB_POOL_TEST_ON_BORROW;
    public final static String DB_POOL_TEST_ON_RETURN = DataSourceProperties.DB_POOL_TEST_ON_RETURN;
    public final static String DB_POOL_TEST_WHILE_IDLE = DataSourceProperties.DB_POOL_TEST_WHILE_IDLE;
    public final static String DB_POOL_INIT_SQL = DataSourceProperties.DB_POOL_INIT_SQL;
    public final static String DB_POOL_CONNECTION_PROPERTIES = DataSourceProperties.DB_POOL_CONNECTION_PROPERTIES;
    public static final String DB_DELIMITED_IDENTIFIER_MODE = DataSourceProperties.DB_DELIMITED_IDENTIFIER_MODE;
    public final static String[] ALL_PROPS = DataSourceProperties.ALL_PROPS;
}
