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

import org.slf4j.LoggerFactory;

public final class DataSourceUtils {
    private DataSourceUtils() {
    }

    /**
     * Closes a {@link DataSource} if it implements {@link AutoCloseable}. Safe to call with {@code null}. Does nothing for DataSources that do not implement
     * {@link AutoCloseable} (e.g. JNDI / container-managed sources).
     */
    public static void closeQuietly(DataSource ds) {
        if (ds instanceof AutoCloseable) {
            try {
                ((AutoCloseable) ds).close();
            } catch (Exception e) {
                LoggerFactory.getLogger(DataSourceUtils.class)
                        .debug("Failed to close data source", e);
            }
        }
    }
}
