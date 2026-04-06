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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

class DataSourceUtilsTest {

    @Test
    void closeQuietlyDoesNothingForNull() {
        assertDoesNotThrow(() -> DataSourceUtils.closeQuietly(null));
    }

    @Test
    void closeQuietlyClosesAutoCloseableDataSource() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        DataSource ds = new CloseableDataSource(() -> closed.set(true));
        DataSourceUtils.closeQuietly(ds);
        assertTrue(closed.get(), "close() should have been called");
    }

    @Test
    void closeQuietlyDoesNothingForNonCloseableDataSource() {
        DataSource ds = new NonCloseableDataSource();
        assertDoesNotThrow(() -> DataSourceUtils.closeQuietly(ds));
    }

    @Test
    void closeQuietlySwallowsExceptionFromClose() {
        DataSource ds = new ThrowingCloseableDataSource();
        assertDoesNotThrow(() -> DataSourceUtils.closeQuietly(ds));
    }

    private static class CloseableDataSource extends StubDataSource implements AutoCloseable {
        private final Runnable onClose;

        CloseableDataSource(Runnable onClose) {
            this.onClose = onClose;
        }

        @Override
        public void close() {
            onClose.run();
        }
    }

    private static class ThrowingCloseableDataSource extends StubDataSource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            throw new Exception("simulated close failure");
        }
    }

    private static class NonCloseableDataSource extends StubDataSource {
    }

    private static class StubDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
    }
}
