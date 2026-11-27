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
package org.jumpmind.db.platform.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.jumpmind.db.DbTestUtils;
import org.jumpmind.db.model.Transaction;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostgreSqlTransactionsIntegrationTest {
    private static final String TABLE_NAME = "test_blocked_transactions";
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_INTERVAL_MS = 100;
    private static IDatabasePlatform platform;

    @BeforeAll
    static void setUp() throws Exception {
        platform = DbTestUtils.createDatabasePlatform(DbTestUtils.ROOT);
        if (isPostgreSqlWithBlockingPids()) {
            ISqlTemplate template = platform.getSqlTemplate();
            template.update("drop table if exists " + TABLE_NAME);
            template.update("create table " + TABLE_NAME + " (id integer primary key, name varchar(20))");
            template.update("insert into " + TABLE_NAME + " values (1, 'one'), (2, 'two')");
        }
    }

    @AfterAll
    static void tearDown() {
        if (isPostgreSqlWithBlockingPids()) {
            platform.getSqlTemplate().update("drop table if exists " + TABLE_NAME);
        }
    }

    @BeforeEach
    void checkDatabaseType() {
        assumeTrue(isPostgreSqlWithBlockingPids());
    }

    @Test
    void testGetTransactions_sessionsReadingSameTableAreNotBlocked() throws Exception {
        try (Connection first = openTransaction(); Connection second = openTransaction()) {
            int firstPid = getBackendPid(first);
            int secondPid = getBackendPid(second);
            execute(first, "select count(*) from " + TABLE_NAME);
            execute(second, "select count(*) from " + TABLE_NAME);
            List<Transaction> transactions = transactionsOf(platform.getTransactions(), firstPid, secondPid);
            assertEquals(2, transactions.size());
            for (Transaction transaction : transactions) {
                assertEquals("", transaction.getBlockingId(), "session " + transaction.getId() + " reported as blocked");
            }
            first.rollback();
            second.rollback();
        }
    }

    @Test
    void testGetTransactions_rowLockWaiterIsBlockedByHolderOnly() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection holder = openTransaction(); Connection waiter = openTransaction()) {
            int holderPid = getBackendPid(holder);
            int waiterPid = getBackendPid(waiter);
            execute(holder, "update " + TABLE_NAME + " set name = 'holder' where id = 1");
            execute(waiter, "set local lock_timeout = '" + LOCK_WAIT_TIMEOUT.multipliedBy(2).getSeconds() + "s'");
            Future<?> waiterUpdate = executor.submit(() -> {
                execute(waiter, "update " + TABLE_NAME + " set name = 'waiter' where id = 1");
                return null;
            });
            waitUntilWaitingOnLock(waiterPid);
            List<Transaction> blocked = transactionsOf(platform.getTransactions(), holderPid, waiterPid).stream()
                    .filter(transaction -> !transaction.getBlockingId().isEmpty()).collect(Collectors.toList());
            holder.rollback();
            waiterUpdate.get();
            waiter.rollback();
            assertEquals(1, blocked.size());
            assertEquals(String.valueOf(waiterPid), blocked.get(0).getId());
            assertEquals(String.valueOf(holderPid), blocked.get(0).getBlockingId());
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean isPostgreSqlWithBlockingPids() {
        return platform instanceof PostgreSqlDatabasePlatform && platform.isBlockedTransactionDetectionSupported();
    }

    private Connection openTransaction() throws SQLException {
        DataSource dataSource = platform.getDataSource();
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    private int getBackendPid(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("select pg_backend_pid()")) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void waitUntilWaitingOnLock(int pid) throws InterruptedException {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT.toMillis();
        String sql = "select count(*) from pg_stat_activity where pid = ? and wait_event_type = 'Lock'";
        while (platform.getSqlTemplate().queryForInt(sql, pid) == 0) {
            assertTrue(System.currentTimeMillis() < deadline, "session " + pid + " did not start waiting on a lock");
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    private List<Transaction> transactionsOf(List<Transaction> transactions, int... pids) {
        List<String> ids = Arrays.stream(pids).mapToObj(String::valueOf).collect(Collectors.toList());
        return transactions.stream().filter(transaction -> ids.contains(transaction.getId())).collect(Collectors.toList());
    }
}
