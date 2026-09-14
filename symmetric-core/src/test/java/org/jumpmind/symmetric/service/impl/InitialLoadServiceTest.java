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
package org.jumpmind.symmetric.service.impl;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.TableReloadRequest;
import org.jumpmind.symmetric.model.TableReloadStatus;
import org.jumpmind.symmetric.observability.interfaces.IEngineMetricsService;
import org.jumpmind.symmetric.observability.interfaces.ISymDoubleGauge;
import org.jumpmind.symmetric.observability.interfaces.ISymLongGauge;
import org.jumpmind.symmetric.observability.interfaces.SymMetricConstants;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InitialLoadServiceTest {
    private static final int MAX_LOAD_COUNT = 6;
    private ISymmetricEngine engine;
    private IParameterService parameterService;
    private IDataService dataService;
    private IEngineMetricsService metricsService;
    private ISymLongGauge activeGauge;
    private ISymDoubleGauge utilGauge;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        parameterService = mock(IParameterService.class);
        dataService = mock(IDataService.class);
        metricsService = mock(IEngineMetricsService.class);
        activeGauge = mock(ISymLongGauge.class);
        utilGauge = mock(ISymDoubleGauge.class);
        ISymmetricDialect dialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(dialect.getPlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getDataService()).thenReturn(dataService);
        when(engine.getMetricsService()).thenReturn(metricsService);
        when(engine.getStatisticManager()).thenReturn(mock(IStatisticManager.class));
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(parameterService.getInt(ParameterConstants.INITIAL_LOAD_EXTRACT_THREAD_COUNT_PER_SERVER, 20)).thenReturn(MAX_LOAD_COUNT);
        when(metricsService.getLongGauge(SymMetricConstants.METRIC_ID_LOADS_OUTGOING)).thenReturn(activeGauge);
        when(metricsService.getDoubleGauge(SymMetricConstants.METRIC_ID_LOADS_OUTGOING_UTILIZATION)).thenReturn(utilGauge);
    }

    @Test
    void processTableRequestLoads_noActiveLoads_setsGaugesToZero() {
        when(dataService.getTableReloadRequestToProcess(anyString())).thenReturn(List.of());
        when(dataService.getActiveTableReloadStatus()).thenReturn(List.of());
        InitialLoadService service = new InitialLoadService(engine);
        service.processTableRequestLoads(new Node(), mock(ProcessInfo.class));
        verify(activeGauge).setValue(0L);
        verify(utilGauge).setValue(0.0);
    }

    @Test
    void processTableRequestLoads_withActiveLoads_setsGaugesToActiveCount() {
        List<TableReloadStatus> active = List.of(mock(TableReloadStatus.class), mock(TableReloadStatus.class));
        when(dataService.getTableReloadRequestToProcess(anyString())).thenReturn(List.of());
        when(dataService.getActiveTableReloadStatus()).thenReturn(active);
        InitialLoadService service = new InitialLoadService(engine);
        service.processTableRequestLoads(new Node(), mock(ProcessInfo.class));
        verify(activeGauge).setValue(2L);
        verify(utilGauge).setValue(2 * 100.0 / MAX_LOAD_COUNT);
    }

    @Test
    void processTableRequestLoads_nullMetricsService_doesNotThrow() {
        when(engine.getMetricsService()).thenReturn(null);
        when(dataService.getTableReloadRequestToProcess(anyString())).thenReturn(List.of());
        when(dataService.getActiveTableReloadStatus()).thenReturn(List.of());
        InitialLoadService service = new InitialLoadService(engine);
        service.processTableRequestLoads(new Node(), mock(ProcessInfo.class));
        verify(activeGauge, never()).setValue(anyLong());
    }

    @Test
    void processTableRequestLoads_gaugeNotRegistered_doesNotThrow() {
        when(metricsService.getLongGauge(anyString())).thenReturn(null);
        when(metricsService.getDoubleGauge(anyString())).thenReturn(null);
        when(dataService.getTableReloadRequestToProcess(anyString())).thenReturn(List.of());
        when(dataService.getActiveTableReloadStatus()).thenReturn(List.of());
        InitialLoadService service = new InitialLoadService(engine);
        service.processTableRequestLoads(new Node(), mock(ProcessInfo.class));
        verify(activeGauge, never()).setValue(anyLong());
    }

    @Test
    void processTableRequestLoads_pendingLoadsWithMaxReached_gaugesReflectActiveCount() {
        List<TableReloadStatus> active = List.of(
                mock(TableReloadStatus.class), mock(TableReloadStatus.class),
                mock(TableReloadStatus.class), mock(TableReloadStatus.class),
                mock(TableReloadStatus.class), mock(TableReloadStatus.class));
        List<TableReloadRequest> pending = List.of(mock(TableReloadRequest.class));
        when(dataService.getTableReloadRequestToProcess(anyString())).thenReturn(pending);
        when(dataService.getActiveTableReloadStatus()).thenReturn(active);
        when(parameterService.is(anyString(), anyBoolean())).thenReturn(false);
        InitialLoadService service = new InitialLoadService(engine);
        service.processTableRequestLoads(new Node(), mock(ProcessInfo.class));
        verify(activeGauge).setValue(6L);
        verify(utilGauge).setValue(100.0);
    }
}
