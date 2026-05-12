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
package org.jumpmind.symmetric.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.jupiter.api.Test;

class RegistrationRequestTest {
    private static final String TEST_CLIENT_GROUP_NAME = "client";
    private static final String TEST_CLIENT_EXTERNAL_ID = "client-001";

    @Test
    void incrementsAttemptCounts() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        prior.setAttemptCount(4);
        request.incrementAttemptsAndSetLatestMessage(prior);
        assertEquals(5, request.getAttemptCount());
    }

    @Test
    void currentErrorMessageKeptWhenBothSet() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        request.setErrorMessage("current error");
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        prior.setErrorMessage("prior error");
        request.incrementAttemptsAndSetLatestMessage(prior);
        assertEquals("current error", request.getErrorMessage());
    }

    @Test
    void priorErrorMessageUsedWhenCurrentIsNull() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        prior.setErrorMessage("prior error");
        request.incrementAttemptsAndSetLatestMessage(prior);
        assertEquals("prior error", request.getErrorMessage());
    }

    @Test
    void priorErrorMessageUsedWhenCurrentIsEmpty() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        request.setErrorMessage("");
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        prior.setErrorMessage("prior error");
        request.incrementAttemptsAndSetLatestMessage(prior);
        assertEquals("prior error", request.getErrorMessage());
    }

    @Test
    void errorMessageStaysNullWhenBothNull() {
        RegistrationRequest request = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        RegistrationRequest prior = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        request.incrementAttemptsAndSetLatestMessage(prior);
        assertNull(request.getErrorMessage());
    }

    private RegistrationRequest buildRequest(String groupName, String externalId) {
        RegistrationRequest req = new RegistrationRequest();
        req.setNodeGroupId(groupName);
        req.setExternalId(externalId);
        return req;
    }
}
