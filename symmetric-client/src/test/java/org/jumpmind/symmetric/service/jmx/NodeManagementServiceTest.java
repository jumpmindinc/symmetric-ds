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
package org.jumpmind.symmetric.service.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.util.ResettableBasicDataSource;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

class NodeManagementServiceTest {
    private ISymmetricEngine engine;
    private NodeManagementService service;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        service = new NodeManagementService();
        service.setSymmetricEngine(engine);
    }

    @Test
    void getDatabaseConnectionsActiveReturnsDbcp2ActiveCount() {
        ResettableBasicDataSource ds = mock(ResettableBasicDataSource.class);
        when(ds.getNumActive()).thenReturn(3);
        when(engine.getDataSource()).thenReturn(ds);
        assertEquals(3, service.getDatabaseConnectionsActive());
    }

    @Test
    void getDatabaseConnectionsActiveReturnsHikariActiveCount() {
        HikariDataSource ds = mock(HikariDataSource.class);
        HikariPoolMXBean poolMXBean = mock(HikariPoolMXBean.class);
        when(ds.getHikariPoolMXBean()).thenReturn(poolMXBean);
        when(poolMXBean.getActiveConnections()).thenReturn(5);
        when(engine.getDataSource()).thenReturn(ds);
        assertEquals(5, service.getDatabaseConnectionsActive());
    }

    @Test
    void getDatabaseConnectionsActiveReturnsNegativeOneForUnknownDataSource() {
        when(engine.getDataSource()).thenReturn(mock(javax.sql.DataSource.class));
        assertEquals(-1, service.getDatabaseConnectionsActive());
    }

    @Test
    void getDatabaseConnectionsMaxReturnsDbcp2MaxTotal() {
        ResettableBasicDataSource ds = mock(ResettableBasicDataSource.class);
        when(ds.getMaxTotal()).thenReturn(10);
        when(engine.getDataSource()).thenReturn(ds);
        assertEquals(10, service.getDatabaseConnectionsMax());
    }

    @Test
    void getDatabaseConnectionsMaxReturnsHikariMaxPoolSize() {
        HikariDataSource ds = mock(HikariDataSource.class);
        when(ds.getMaximumPoolSize()).thenReturn(20);
        when(engine.getDataSource()).thenReturn(ds);
        assertEquals(20, service.getDatabaseConnectionsMax());
    }

    @Test
    void getDatabaseConnectionsMaxReturnsNegativeOneForUnknownDataSource() {
        when(engine.getDataSource()).thenReturn(mock(javax.sql.DataSource.class));
        assertEquals(-1, service.getDatabaseConnectionsMax());
    }
}
