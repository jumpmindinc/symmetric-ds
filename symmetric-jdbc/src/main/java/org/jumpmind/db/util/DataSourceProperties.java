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

/**
 * Database data source properties that represent parameters that can be retrieved or saved via the {@link IParameterService}.
 */
public final class DataSourceProperties {
    private DataSourceProperties() {
    }

    public final static String DB_POOL_URL = "db.url";
    public final static String DB_POOL_DRIVER = "db.driver";
    public final static String DB_POOL_USER = "db.user";
    public final static String DB_POOL_PASSWORD = "db.password";
    public final static String DB_POOL_INITIAL_SIZE = "db.pool.initial.size";
    public final static String DB_POOL_MAX_ACTIVE = "db.pool.max.active";
    public final static String DB_POOL_MAX_IDLE = "db.pool.max.idle";
    public final static String DB_POOL_MIN_IDLE = "db.pool.min.idle";
    public final static String DB_POOL_MAX_WAIT = "db.pool.max.wait.millis";
    public final static String DB_POOL_MIN_EVICTABLE_IDLE_TIME_MILLIS = "db.pool.min.evictable.idle.millis";
    public final static String DB_POOL_VALIDATION_QUERY = "db.validation.query";
    public final static String DB_POOL_TEST_ON_BORROW = "db.test.on.borrow";
    public final static String DB_POOL_TEST_ON_RETURN = "db.test.on.return";
    public final static String DB_POOL_TEST_WHILE_IDLE = "db.test.while.idle";
    public final static String DB_POOL_INIT_SQL = "db.init.sql";
    public final static String DB_POOL_CONNECTION_PROPERTIES = "db.connection.properties";
    public static final String DB_DELIMITED_IDENTIFIER_MODE = "db.delimited.identifier.mode";
    public final static String[] ALL_PROPS = new String[] { DB_POOL_URL, DB_POOL_DRIVER, DB_POOL_USER, DB_POOL_PASSWORD, DB_POOL_INITIAL_SIZE,
            DB_POOL_MAX_ACTIVE, DB_POOL_MAX_IDLE, DB_POOL_MIN_IDLE, DB_POOL_MAX_WAIT, DB_POOL_MIN_EVICTABLE_IDLE_TIME_MILLIS,
            DB_POOL_VALIDATION_QUERY, DB_POOL_TEST_ON_BORROW, DB_POOL_TEST_ON_RETURN, DB_POOL_TEST_WHILE_IDLE, DB_POOL_INIT_SQL,
            DB_POOL_CONNECTION_PROPERTIES, DB_DELIMITED_IDENTIFIER_MODE };
}