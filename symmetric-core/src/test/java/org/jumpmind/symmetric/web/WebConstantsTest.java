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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WebConstantsTest {
    @Test
    void getHttpMessage_serviceNotReady_returnsMessage() {
        assertEquals("Service is not ready", WebConstants.getHttpMessage(WebConstants.SC_SERVICE_NOT_READY));
    }

    @Test
    void getHttpMessage_serviceBusy_returnsMessage() {
        assertEquals("Service is busy", WebConstants.getHttpMessage(WebConstants.SC_SERVICE_BUSY));
    }

    @Test
    void getHttpMessage_missingReservation_returnsMessage() {
        assertEquals("Missing reservation", WebConstants.getHttpMessage(WebConstants.SC_NO_RESERVATION));
    }

    @Test
    void getHttpMessage_duplicateConnection_returnsMessage() {
        assertEquals("Duplicate connection", WebConstants.getHttpMessage(WebConstants.SC_ALREADY_CONNECTED));
    }

    @Test
    void getHttpMessage_unknownCode_returnsNull() {
        assertNull(WebConstants.getHttpMessage(999));
    }

    @Test
    void testGetHttpMessage_registrationNotOpen() {
        assertEquals("Registration is not open", WebConstants.getHttpMessage(WebConstants.REGISTRATION_NOT_OPEN));
    }

    @Test
    void testGetHttpMessage_registrationRequired() {
        assertEquals("Registration is required", WebConstants.getHttpMessage(WebConstants.REGISTRATION_REQUIRED));
    }

    @Test
    void testGetHttpMessage_registrationPending() {
        assertEquals("Registration is pending", WebConstants.getHttpMessage(WebConstants.REGISTRATION_PENDING));
    }

    @Test
    void testGetHttpMessage_initialLoadPending() {
        assertEquals("Initial load is pending", WebConstants.getHttpMessage(WebConstants.INITIAL_LOAD_PENDING));
    }

    @Test
    void testGetHttpMessage_syncDisabled() {
        assertEquals("Sync is disabled", WebConstants.getHttpMessage(WebConstants.SYNC_DISABLED));
    }

    @Test
    void testGetHttpMessage_forbidden() {
        assertEquals("Bad node password", WebConstants.getHttpMessage(WebConstants.SC_FORBIDDEN));
    }

    @Test
    void testGetHttpMessage_authExpired() {
        assertEquals("Session expired", WebConstants.getHttpMessage(WebConstants.SC_AUTH_EXPIRED));
    }

    @Test
    void testGetHttpMessage_serviceUnavailable() {
        assertEquals("Service is unavailable", WebConstants.getHttpMessage(WebConstants.SC_SERVICE_UNAVAILABLE));
    }
}
