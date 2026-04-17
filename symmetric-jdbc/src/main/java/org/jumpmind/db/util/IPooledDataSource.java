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

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Common abstraction over connection pool implementations (DBCP2, HikariCP) that exposes pool metrics without requiring callers to cast to a pool-specific
 * type.
 */
public interface IPooledDataSource {
    int getNumActive();

    int getNumIdle();

    int getMaxTotal();

    String getUsername();

    /**
     * Returns an {@link IPooledDataSource} view of the given {@link DataSource}, or {@code null} if the pool implementation is not recognized.
     */
    static IPooledDataSource of(DataSource ds) {
        if (ds instanceof IPooledDataSource) {
            return (IPooledDataSource) ds;
        }
        if (ds instanceof HikariDataSource) {
            HikariDataSource hikari = (HikariDataSource) ds;
            return new IPooledDataSource() {
                @Override
                public int getNumActive() {
                    return hikari.getHikariPoolMXBean() != null ? hikari.getHikariPoolMXBean().getActiveConnections() : 0;
                }

                @Override
                public int getNumIdle() {
                    return hikari.getHikariPoolMXBean() != null ? hikari.getHikariPoolMXBean().getIdleConnections() : 0;
                }

                @Override
                public int getMaxTotal() {
                    return hikari.getMaximumPoolSize();
                }

                @Override
                public String getUsername() {
                    return hikari.getUsername();
                }
            };
        }
        return null;
    }
}
