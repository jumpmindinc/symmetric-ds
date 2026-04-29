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
package org.jumpmind.symmetric.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeBatchStatusGaugeTest {
    private ISymLongGauge batchesGauge;
    private ISymLongGauge rowsGauge;
    private NodeBatchStatusGauge gauge;

    @BeforeEach
    void setUp() {
        batchesGauge = mock(ISymLongGauge.class);
        rowsGauge = mock(ISymLongGauge.class);
        gauge = new NodeBatchStatusGauge("node-1", "OK", batchesGauge, rowsGauge);
    }

    @Test
    void getNodeId_returnsConstructorValue() {
        assertEquals("node-1", gauge.getNodeId());
    }

    @Test
    void getBatchStatus_returnsConstructorValue() {
        assertEquals("OK", gauge.getBatchStatus());
    }

    @Test
    void getBatchesGauge_returnsConstructorValue() {
        assertSame(batchesGauge, gauge.getBatchesGauge());
    }

    @Test
    void getRowsGauge_returnsConstructorValue() {
        assertSame(rowsGauge, gauge.getRowsGauge());
    }

    @Test
    void getBatchCount_delegatesToBatchesGauge() {
        when(batchesGauge.getValue()).thenReturn(7L);
        assertEquals(7L, gauge.getBatchCount());
    }

    @Test
    void getRowCount_delegatesToRowsGauge() {
        when(rowsGauge.getValue()).thenReturn(250L);
        assertEquals(250L, gauge.getRowCount());
    }

    @Test
    void getBatchCount_afterGaugeSetToZero_returnsZero() {
        when(batchesGauge.getValue()).thenReturn(0L);
        assertEquals(0L, gauge.getBatchCount());
    }

    @Test
    void getRowCount_afterGaugeSetToZero_returnsZero() {
        when(rowsGauge.getValue()).thenReturn(0L);
        assertEquals(0L, gauge.getRowCount());
    }
}
