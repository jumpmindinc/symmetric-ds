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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.jumpmind.security.ISecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class ClusterPeerSecureMessageTest {
    @BeforeEach
    public void setUp() {
        ISecurityService mockSecurityService = mock(ISecurityService.class);
        when(mockSecurityService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(mockSecurityService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        ClusterPeerSecureMessage.setSecurityService(mockSecurityService);
    }

    private ClusterPeerStatusMessage heartbeat() {
        return new ClusterPeerStatusMessage(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "server1", "inst1", "1.0");
    }

    private ClusterPeerStatusMessage serializeRoundTrip(ClusterPeerStatusMessage msg) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new ObjectOutputStream(baos).writeObject(msg);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        return (ClusterPeerStatusMessage) ois.readObject();
    }

    @Test
    public void getVersionNo_returnsExpectedConstant() {
        assertEquals(20260611, heartbeat().getVersionNo());
    }

    @Test
    public void isHeaderChecksumValid_freshMessage_returnsTrue() {
        assertTrue(heartbeat().isHeaderChecksumValid());
    }

    @Test
    public void isHeaderChecksumValid_tamperedChecksum_returnsFalse() throws Exception {
        ClusterPeerStatusMessage msg = heartbeat();
        Field f = ClusterPeerSecureMessage.class.getDeclaredField("headerChecksum");
        f.setAccessible(true);
        f.set(msg, "tampered-checksum");
        assertFalse(msg.isHeaderChecksumValid());
    }

    @Test
    public void ensureDecrypted_deserializedMessage_lazyDecryptsPayload() throws Exception {
        ClusterPeerStatusMessage deserialized = serializeRoundTrip(heartbeat());
        assertEquals(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, deserialized.getEventType());
        assertEquals("inst1", deserialized.getInstanceId());
    }

    @Test
    public void decryptPayload_securityServiceNull_throwsIllegalStateException() throws Exception {
        ClusterPeerStatusMessage deserialized = serializeRoundTrip(heartbeat());
        ClusterPeerSecureMessage.setSecurityService(null);
        try {
            deserialized.getEventType();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    public void computeChecksum_noSuchAlgorithm_throwsRuntimeException() {
        try (MockedStatic<MessageDigest> mocked = mockStatic(MessageDigest.class)) {
            mocked.when(() -> MessageDigest.getInstance(anyString()))
                    .thenThrow(new NoSuchAlgorithmException("mocked"));
            try {
                new ClusterPeerStatusMessage(ClusterPeerStatusMessage.EVENT_PEER_HEARTBEAT, "s1", "i1", "1.0");
                fail("Expected RuntimeException");
            } catch (RuntimeException ex) {
                assertTrue(ex.getCause() instanceof NoSuchAlgorithmException);
            }
        }
    }
}
