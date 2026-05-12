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
package org.jumpmind.symmetric.service.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.RegistrationRequest;
import org.jumpmind.symmetric.model.RegistrationRequest.RegistrationStatus;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.IDataLoaderService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IOutgoingBatchService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.statistic.IStatisticManager;
import org.jumpmind.symmetric.transport.ITransportManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationServiceTest {
    private static final String TEST_CLIENT_GROUP_NAME = "client";
    private static final String TEST_CLIENT_EXTERNAL_ID = "client-001";

    private RegistrationService service;
    private ISqlTemplate sqlTemplate;

    @BeforeEach
    void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);

        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(parameterService.getTablePrefix()).thenReturn("");
        when(parameterService.getExternalId()).thenReturn("test");
        when(parameterService.getInt(ParameterConstants.REGISTRATION_MAX_TIME_BETWEEN_RETRIES, 30)).thenReturn(30);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getSqlReplacementTokens()).thenReturn(new HashMap<>());
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        when(platform.getSqlTemplateDirty()).thenReturn(sqlTemplate);
        when(platform.scrubSql(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(engine.getNodeService()).thenReturn(mock(INodeService.class));
        when(engine.getDataExtractorService()).thenReturn(mock(IDataExtractorService.class));
        when(engine.getDataService()).thenReturn(mock(IDataService.class));
        when(engine.getDataLoaderService()).thenReturn(mock(IDataLoaderService.class));
        when(engine.getTransportManager()).thenReturn(mock(ITransportManager.class));
        when(engine.getStatisticManager()).thenReturn(mock(IStatisticManager.class));
        when(engine.getConfigurationService()).thenReturn(mock(IConfigurationService.class));
        when(engine.getOutgoingBatchService()).thenReturn(mock(IOutgoingBatchService.class));
        when(engine.getExtensionService()).thenReturn(mock(IExtensionService.class));
        when(sqlTemplate.update(anyString(), any(Object[].class), any(int[].class))).thenReturn(1);

        service = new RegistrationService(engine);
    }

    @Test
    void nullPriorRequestReturnsZeroWithNoUpdate() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, null);
        assertEquals(0, result);
        verify(sqlTemplate, never()).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void sameStatusCombinesAttemptCountsAndCallsUpdate() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        prior.setAttemptCount(3);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(4, request.getAttemptCount());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void rejectedPriorWithPendingCurrentSetsStatusToRejectedAndUpdates() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.RJ, request.getStatus());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void errorPriorWithOkCurrentSetsStatusToErrorAndUpdates() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.ER);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.ER, request.getStatus());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void pendingPriorWithOkCurrentMergesWithoutOverwritingStatus() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RQ);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.OK, request.getStatus());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void rejectedPriorWithOkCurrentMergesWithoutOverwritingStatus() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(1, result);
        assertEquals(RegistrationStatus.OK, request.getStatus());
        verify(sqlTemplate).update(anyString(), any(Object[].class), any(int[].class));
    }

    @Test
    void okPriorWithRejectedCurrentNoMerge() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.RJ);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID, RegistrationStatus.OK);
        int result = service.reconcileRegistrationRequestWithPriorEntry(request, prior);
        assertEquals(0, result);
        verify(sqlTemplate, never()).update(anyString(), any(Object[].class), any(int[].class));
    }

    private RegistrationRequest buildRequest(String groupName, String externalId, RegistrationStatus status) {
        RegistrationRequest req = new RegistrationRequest();
        req.setNodeGroupId(groupName);
        req.setExternalId(externalId);
        req.setHostName("localhost");
        req.setStatus(status);
        return req;
    }
}
