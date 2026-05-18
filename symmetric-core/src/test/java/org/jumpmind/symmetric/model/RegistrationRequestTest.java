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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;

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

    @Test
    void incompleteStatusListQuotedWithSingleQuote() {
        assertEquals("'RQ','ER','RJ'", RegistrationRequest.getIncompleteStatusListQuoted("'"));
    }

    @Test
    void incompleteStatusListQuotedWithDoubleQuote() {
        assertEquals("\"RQ\",\"ER\",\"RJ\"", RegistrationRequest.getIncompleteStatusListQuoted("\""));
    }

    @Test
    void incompleteStatusListQuotedWithEmptyQuote() {
        assertEquals("RQ,ER,RJ", RegistrationRequest.getIncompleteStatusListQuoted(""));
    }

    @Test
    void setRegisteredNodeIdStoresValue() {
        RegistrationRequest req = new RegistrationRequest();
        req.setRegisteredNodeId("node-42");
        assertEquals("node-42", req.getRegisteredNodeId());
    }

    @Test
    void setCreateTimeStoresValue() {
        RegistrationRequest req = new RegistrationRequest();
        Date date = new Date(1000L);
        req.setCreateTime(date);
        assertEquals(date, req.getCreateTime());
    }

    @Test
    void setLastUpdateByStoresValue() {
        RegistrationRequest req = new RegistrationRequest();
        req.setLastUpdateBy("admin");
        assertEquals("admin", req.getLastUpdateBy());
    }

    @Test
    void hashCodeDiffersWhenCreateTimeDiffers() {
        RegistrationRequest a = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        a.setCreateTime(new Date(1000L));
        RegistrationRequest b = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        b.setCreateTime(new Date(2000L));
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void hashCodeSameWhenCreateTimeNull() {
        RegistrationRequest a = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        a.setCreateTime(null);
        RegistrationRequest b = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        b.setCreateTime(null);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equalsReturnsTrueForSameInstance() {
        RegistrationRequest req = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        assertEquals(req, req);
    }

    @Test
    void equalsReturnsFalseForNull() {
        RegistrationRequest req = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        assertNotEquals(null, req);
    }

    @Test
    void equalsReturnsFalseForDifferentClass() {
        RegistrationRequest req = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        assertNotEquals(req, "not a RegistrationRequest");
    }

    @Test
    void equalsReturnsFalseWhenCreateTimeDiffers() {
        RegistrationRequest a = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        a.setCreateTime(new Date(1000L));
        RegistrationRequest b = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        b.setCreateTime(new Date(2000L));
        assertNotEquals(a, b);
    }

    @Test
    void equalsReturnsFalseWhenThisCreateTimeNullOtherNot() {
        RegistrationRequest a = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        a.setCreateTime(null);
        RegistrationRequest b = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        b.setCreateTime(new Date(1000L));
        assertNotEquals(a, b);
    }

    @Test
    void equalsReturnsFalseWhenExternalIdNullInThisButNotOther() {
        RegistrationRequest a = buildRequest(TEST_CLIENT_GROUP_NAME, null);
        a.setCreateTime(new Date(1000L));
        RegistrationRequest b = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        b.setCreateTime(new Date(1000L));
        assertNotEquals(a, b);
    }

    @Test
    void equalsReturnsFalseWhenExternalIdDiffers() {
        RegistrationRequest a = buildRequest(TEST_CLIENT_GROUP_NAME, "ext-1");
        a.setCreateTime(new Date(1000L));
        RegistrationRequest b = buildRequest(TEST_CLIENT_GROUP_NAME, "ext-2");
        b.setCreateTime(new Date(1000L));
        assertNotEquals(a, b);
    }

    @Test
    void equalsReturnsTrueWhenAllFieldsMatch() {
        Date date = new Date(1000L);
        RegistrationRequest a = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        a.setCreateTime(date);
        RegistrationRequest b = buildRequest(TEST_CLIENT_GROUP_NAME, TEST_CLIENT_EXTERNAL_ID);
        b.setCreateTime(date);
        assertEquals(a, b);
    }

    private RegistrationRequest buildRequest(String groupName, String externalId) {
        RegistrationRequest req = new RegistrationRequest();
        req.setNodeGroupId(groupName);
        req.setExternalId(externalId);
        return req;
    }
}
