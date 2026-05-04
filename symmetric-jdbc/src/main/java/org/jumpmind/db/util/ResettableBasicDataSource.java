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

import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;

/**
 * A subclass of {@link BasicDataSource} which allows for a data source to be closed (all underlying connections are closed) and then allows new connections to
 * be created.
 */
public class ResettableBasicDataSource extends BasicDataSource implements IPooledDataSource {
    protected boolean closed;

    public ResettableBasicDataSource() {
        setAccessToUnderlyingConnectionAllowed(true);
    }

    @Override
    public synchronized void close() {
        try {
            closed = true;
            super.close();
        } catch (SQLException e) {
        }
    }

    @Override
    protected DataSource createDataSource() throws SQLException {
        if (closed) {
            closed = false;
            super.start();
        }
        return super.createDataSource();
    }
}
