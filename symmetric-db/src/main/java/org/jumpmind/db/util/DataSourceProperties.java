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
 * Database connection pool properties.
 *
 * <p>
 * Each property key has a companion {@code _DEFAULT} constant that carries the default value applied when the property is absent. Keeping the key and default
 * together makes it straightforward to see effective configuration at a glance and eliminates magic numbers scattered across pool builder classes. The
 * {@code _DEFAULT} constants are package-private; only the property key constants are part of the public API.
 * </p>
 *
 * <p>
 * Properties are grouped by scope:
 * <ul>
 * <li><b>Shared</b> — accepted by all supported connection pool implementations.</li>
 * <li><b>DBCP2</b> — ignored when {@code db.pool.type!=dbcp2}</li>
 * <li><b>Hikari</b> — ignored when {@code db.pool.type!=hikari}</li>
 * </ul>
 * </p>
 */
public final class DataSourceProperties {
    private DataSourceProperties() {
    }

    // -------------------------------------------------------------------------
    // Shared connection pool properties
    // -------------------------------------------------------------------------
    public static final String DB_POOL_URL = "db.url";
    public static final String DB_POOL_DRIVER = "db.driver";
    public static final String DB_POOL_USER = "db.user";
    public static final String DB_POOL_PASSWORD = "db.password";
    /** Sets the connection pool implementation. */
    public static final String DB_POOL_TYPE = "db.pool.type";
    /** Default pool type - for backward compatibility. */
    static final String DB_POOL_TYPE_DEFAULT = "hikari";
    /**
     * Maximum number of connections in the pool.
     *
     * <p>
     * The effective meaning varies slightly by pool implementation:
     * <ul>
     * <li><b>DBCP2</b> — maps to {@code maxTotal}: the cap on <em>active</em> (borrowed) connections. Idle connections are managed separately via
     * {@link #DB_POOL_MAX_IDLE}.</li>
     * <li><b>HikariCP</b> — maps to {@code maximumPoolSize}: the cap on the <em>total</em> pool size, which includes both active and idle connections.
     * {@link #DB_POOL_MAX_IDLE} is ignored.</li>
     * </ul>
     * In both cases this is the upper bound on open database connections, so the same value works correctly across pool types.
     * </p>
     */
    public static final String DB_POOL_MAX_ACTIVE = "db.pool.max.active";
    static final int DB_POOL_MAX_ACTIVE_DEFAULT = 10;
    /**
     * Minimum number of idle connections to maintain in the pool.
     *
     * <p>
     * The behavior differs by pool implementation:
     * <ul>
     * <li><b>DBCP2</b> — the eviction thread will create new connections to keep the idle count at or above this value.</li>
     * <li><b>HikariCP</b> — HikariCP will maintain this many idle connections. At {@code 0} (the default) the pool is fully elastic: it creates connections on
     * demand and reclaims them once they have been idle for {@code idleTimeout}. Per HikariCP's documentation, pairing {@code minimumIdle=0} with an
     * {@code idleTimeout} and {@code maxLifetime} shorter than the database's own connection timeout is recommended to avoid stale-connection memory
     * leaks.</li>
     * </ul>
     * </p>
     */
    public static final String DB_POOL_MIN_IDLE = "db.pool.min.idle";
    static final int DB_POOL_MIN_IDLE_DEFAULT = 0;
    /**
     * Maximum time (ms) a client will wait for a connection from the pool before a {@link java.sql.SQLException} is thrown.
     *
     * <p>
     * The behavior and naming differ by pool implementation:
     * <ul>
     * <li><b>DBCP2</b> — {@code maxWaitMillis}. Default: {@value #DB_POOL_MAX_WAIT_DBCP2_DEFAULT} ms.</li>
     * <li><b>HikariCP</b> — {@code connectionTimeout}. The lowest acceptable value is 250 ms. Default: {@value #DB_POOL_MAX_WAIT_HIKARI_DEFAULT} ms.</li>
     * </ul>
     * The defaults intentionally differ between pool types; consider aligning them to a single value in a future release.
     * </p>
     *
     * @see #DB_POOL_MAX_WAIT_HIKARI_DEFAULT
     * @see #DB_POOL_MAX_WAIT_DBCP2_DEFAULT
     */
    public static final String DB_POOL_MAX_WAIT = "db.pool.max.wait.millis";
    /** HikariCP ({@code connectionTimeout}) default for {@link #DB_POOL_MAX_WAIT}. */
    static final int DB_POOL_MAX_WAIT_HIKARI_DEFAULT = 30000;
    /** DBCP2 ({@code maxWaitMillis}) default for {@link #DB_POOL_MAX_WAIT}. */
    static final int DB_POOL_MAX_WAIT_DBCP2_DEFAULT = 5000;
    /**
     * SQL query executed just before a connection is given to the caller to verify the connection is still alive. Also known as {@code connectionTestQuery} in
     * HikariCP.
     *
     * <p>
     * <b>HikariCP:</b> Hikari recommends to only set this for legacy JDBC drivers that do not support the JDBC 4 {@code Connection.isValid()} API. If your
     * driver is JDBC 4 compliant, leave this unset and HikariCP will use {@code isValid()} automatically. HikariCP will log an error at startup if the driver
     * is not JDBC 4 compliant. Default: none.
     * </p>
     */
    public static final String DB_POOL_VALIDATION_QUERY = "db.validation.query";
    /**
     * Semicolon-delimited SQL statements executed when a connection is first created. Literal semicolons within a statement must be escaped as {@code ;;}.
     * HikariCP supports only a single statement; subsequent statements are ignored with a warning.
     */
    public static final String DB_POOL_INIT_SQL = "db.init.sql";
    /**
     * Semicolon-delimited {@code key=value} pairs passed as JDBC connection properties. A literal {@code =} inside a value must be escaped as {@code ==}.
     */
    public static final String DB_POOL_CONNECTION_PROPERTIES = "db.connection.properties";
    public static final String DB_DELIMITED_IDENTIFIER_MODE = "db.delimited.identifier.mode";
    // -------------------------------------------------------------------------
    // DBCP2-only properties
    // -------------------------------------------------------------------------
    /** Number of connections created when the pool starts. */
    public static final String DB_POOL_INITIAL_SIZE = "db.pool.initial.size";
    static final int DB_POOL_INITIAL_SIZE_DEFAULT = 2;
    /** Maximum number of idle connections allowed in the pool. */
    public static final String DB_POOL_MAX_IDLE = "db.pool.max.idle";
    static final int DB_POOL_MAX_IDLE_DEFAULT = 8;
    /** Minimum time (ms) a connection may sit idle before it is eligible for eviction. */
    public static final String DB_POOL_MIN_EVICTABLE_IDLE_TIME_MILLIS = "db.pool.min.evictable.idle.millis";
    static final int DB_POOL_MIN_EVICTABLE_IDLE_TIME_MILLIS_DEFAULT = 60000;
    /** Whether to validate connections when they are borrowed from the pool. */
    public static final String DB_POOL_TEST_ON_BORROW = "db.test.on.borrow";
    static final boolean DB_POOL_TEST_ON_BORROW_DEFAULT = true;
    /** Whether to validate connections when they are returned to the pool. */
    public static final String DB_POOL_TEST_ON_RETURN = "db.test.on.return";
    static final boolean DB_POOL_TEST_ON_RETURN_DEFAULT = false;
    /** Whether to validate idle connections during the eviction run. */
    public static final String DB_POOL_TEST_WHILE_IDLE = "db.test.while.idle";
    static final boolean DB_POOL_TEST_WHILE_IDLE_DEFAULT = false;
    // -------------------------------------------------------------------------
    // HikariCP-only properties
    // -------------------------------------------------------------------------
    /**
     * Maximum lifetime (ms) of a connection in the pool (HikariCP only — {@code maxLifetime}).
     *
     * <p>
     * An in-use connection will never be retired; it is removed only after it is closed and returned to the pool. On a connection-by-connection basis, minor
     * negative attenuation is applied to avoid mass-extinction in the pool. It is strongly recommended to set this value, and it should be at least several
     * seconds shorter than any database or infrastructure imposed connection time limit. A value of {@code 0} indicates no maximum lifetime (infinite
     * lifetime), subject to {@link #DB_POOL_IDLE_TIMEOUT}. The minimum allowed value is 30000 ms (30 seconds). Default: 1800000 ms (30 minutes).
     * </p>
     */
    public static final String DB_POOL_MAX_LIFETIME = "db.pool.max.lifetime.millis";
    /**
     * Maximum time (ms) a connection is allowed to sit idle in the pool (HikariCP only — {@code idleTimeout}).
     *
     * <p>
     * This setting only applies when {@link #DB_POOL_MIN_IDLE} is less than {@link #DB_POOL_MAX_ACTIVE}. Idle connections will not be retired once the pool
     * reaches the minimum idle count. Whether a connection is retired as idle is subject to a maximum variation of +30 seconds and an average variation of +15
     * seconds — a connection will never be retired before this timeout elapses. A value of {@code 0} means idle connections are never removed from the pool.
     * The minimum allowed value is 10000 ms (10 seconds). Default: 600000 ms (10 minutes).
     * </p>
     */
    public static final String DB_POOL_IDLE_TIMEOUT = "db.pool.idle.timeout.millis";
    /** Interval (ms) between keep-alive pings sent to idle connections. */
    public static final String DB_POOL_KEEPALIVE_TIME = "db.pool.keepalive.time";
    /**
     * Amount of time (ms) a connection can be out of the pool before a message is logged indicating a possible connection leak (HikariCP only —
     * {@code leakDetectionThreshold}).
     *
     * <p>
     * A value of {@code 0} disables leak detection. The minimum allowed value for enabling leak detection is 2000 ms (2 seconds). Default: 0 (disabled).
     * </p>
     */
    public static final String DB_POOL_LEAK_DETECTION_THRESHOLD = "db.pool.leak.detection.threshold";
    // -------------------------------------------------------------------------
    // All property keys
    // -------------------------------------------------------------------------
    public static final String[] ALL_PROPS = new String[] {
            // Shared
            DB_POOL_URL,
            DB_POOL_DRIVER,
            DB_POOL_USER,
            DB_POOL_PASSWORD,
            DB_POOL_TYPE,
            DB_POOL_MAX_ACTIVE,
            DB_POOL_MIN_IDLE,
            DB_POOL_MAX_WAIT,
            DB_POOL_VALIDATION_QUERY,
            DB_POOL_INIT_SQL,
            DB_POOL_CONNECTION_PROPERTIES,
            DB_DELIMITED_IDENTIFIER_MODE,
            // DBCP2 only
            DB_POOL_INITIAL_SIZE,
            DB_POOL_MAX_IDLE,
            DB_POOL_MIN_EVICTABLE_IDLE_TIME_MILLIS,
            DB_POOL_TEST_ON_BORROW,
            DB_POOL_TEST_ON_RETURN,
            DB_POOL_TEST_WHILE_IDLE,
            // Hikari only
            DB_POOL_MAX_LIFETIME,
            DB_POOL_IDLE_TIMEOUT,
            DB_POOL_KEEPALIVE_TIME,
            DB_POOL_LEAK_DETECTION_THRESHOLD
    };
}
