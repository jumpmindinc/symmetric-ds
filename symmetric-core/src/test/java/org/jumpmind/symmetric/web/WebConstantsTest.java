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
}
