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
package org.jumpmind.symmetric.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.jumpmind.security.ISecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusterEngineStateMessageTest {
    @BeforeEach
    public void setUp() {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClusterPeerSecureMessage.setSecurityService(mockSecurityService);
    }

    private ClusterEngineStateMessage msg(String state, String engineName) {
        return new ClusterEngineStateMessage(state, engineName, "server1", "inst1", "1.0");
    }

    @Test
    public void getEngineState_returnsConstructedState() {
        assertEquals(ClusterEngineStateMessage.ENGINE_ONLINE, msg(ClusterEngineStateMessage.ENGINE_ONLINE, "eng1").getEngineState());
    }

    @Test
    public void getEngineName_returnsConstructedName() {
        assertEquals("my-engine", msg(ClusterEngineStateMessage.ENGINE_ONLINE, "my-engine").getEngineName());
    }

    @Test
    public void getEventType_matchesEngineState() {
        ClusterEngineStateMessage m = msg(ClusterEngineStateMessage.ENGINE_STARTING, "eng1");
        assertEquals(m.getEngineState(), m.getEventType());
    }

    @Test
    public void allStateConstants_roundtripThroughMessage() {
        String[] states = {
                ClusterEngineStateMessage.ENGINE_STARTING,
                ClusterEngineStateMessage.ENGINE_UPGRADING_DB,
                ClusterEngineStateMessage.ENGINE_ONLINE,
                ClusterEngineStateMessage.ENGINE_OFFLINE
        };
        for (String state : states) {
            ClusterEngineStateMessage m = msg(state, "eng");
            assertEquals(state, m.getEngineState());
            assertEquals(state, m.getEventType());
        }
    }

    @Test
    public void getServerId_returnsConstructedServerId() {
        assertEquals("server1", msg(ClusterEngineStateMessage.ENGINE_ONLINE, "eng").getServerId());
    }

    @Test
    public void getVersion_returnsConstructedVersion() {
        assertEquals("1.0", msg(ClusterEngineStateMessage.ENGINE_ONLINE, "eng").getVersion());
    }

    @Test
    public void getTimestamp_isRecentEpochMs() {
        long before = System.currentTimeMillis();
        ClusterEngineStateMessage m = msg(ClusterEngineStateMessage.ENGINE_ONLINE, "eng");
        long after = System.currentTimeMillis();
        assertTrue(m.getTimestamp() >= before);
        assertTrue(m.getTimestamp() <= after);
    }

    @Test
    public void getTimestampAsDate_returnsIsoString() {
        String ts = msg(ClusterEngineStateMessage.ENGINE_ONLINE, "eng").getTimestampAsDate();
        assertNotNull(ts);
        assertTrue(ts.contains("T"));
    }

    @Test
    public void getVersionNo_returnsPositiveInt() {
        assertTrue(msg(ClusterEngineStateMessage.ENGINE_ONLINE, "eng").getVersionNo() > 0);
    }

    @Test
    public void isStale_freshMessage_returnsFalse() {
        ClusterEngineStateMessage m = msg(ClusterEngineStateMessage.ENGINE_ONLINE, "eng");
        assertFalse(m.isStale(System.currentTimeMillis(), 9000L));
    }

    @Test
    public void isStale_oldMessage_returnsTrue() {
        ClusterEngineStateMessage m = msg(ClusterEngineStateMessage.ENGINE_ONLINE, "eng");
        long now = m.getTimestamp() + 10_000L;
        assertTrue(m.isStale(now, 9000L));
    }

    @Test
    public void isHeaderChecksumValid_freshMessage_returnsTrue() {
        assertTrue(msg(ClusterEngineStateMessage.ENGINE_ONLINE, "eng").isHeaderChecksumValid());
    }

    @Test
    public void parsePayload_twoPartPayload_setsStateAndName() throws Exception {
        ClusterEngineStateMessage m = msg(ClusterEngineStateMessage.ENGINE_ONLINE, "original");
        Method parsePayload = ClusterEngineStateMessage.class.getDeclaredMethod("parsePayload", String.class);
        parsePayload.setAccessible(true);
        parsePayload.invoke(m, ClusterEngineStateMessage.ENGINE_STARTING + "|new-engine");
        assertEquals(ClusterEngineStateMessage.ENGINE_STARTING, m.getEngineState());
        assertEquals("new-engine", m.getEngineName());
    }

    @Test
    public void parsePayload_singlePartPayload_setsStateAndEmptyName() throws Exception {
        ClusterEngineStateMessage m = msg(ClusterEngineStateMessage.ENGINE_ONLINE, "original");
        Method parsePayload = ClusterEngineStateMessage.class.getDeclaredMethod("parsePayload", String.class);
        parsePayload.setAccessible(true);
        parsePayload.invoke(m, ClusterEngineStateMessage.ENGINE_OFFLINE);
        assertEquals(ClusterEngineStateMessage.ENGINE_OFFLINE, m.getEngineState());
        assertEquals("", m.getEngineName());
    }

    @Test
    public void parsePayload_nameWithPipe_preservesFullName() throws Exception {
        ClusterEngineStateMessage m = msg(ClusterEngineStateMessage.ENGINE_ONLINE, "original");
        Method parsePayload = ClusterEngineStateMessage.class.getDeclaredMethod("parsePayload", String.class);
        parsePayload.setAccessible(true);
        parsePayload.invoke(m, ClusterEngineStateMessage.ENGINE_ONLINE + "|engine|with|pipes");
        assertEquals(ClusterEngineStateMessage.ENGINE_ONLINE, m.getEngineState());
        assertEquals("engine|with|pipes", m.getEngineName());
    }
}
