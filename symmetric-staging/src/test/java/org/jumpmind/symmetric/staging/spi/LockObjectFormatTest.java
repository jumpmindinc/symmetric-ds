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
package org.jumpmind.symmetric.staging.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LockObjectFormatTest {
    @Test
    void roundTrip_preservesAllFields() {
        byte[] encoded = LockObjectFormat.encode("node-42", "3.18.5", 1748470800000L);
        LockObjectFormat.LockOwner owner = LockObjectFormat.decode(encoded);
        assertEquals("node-42", owner.getHostname());
        assertEquals("3.18.5", owner.getSymVersion());
        assertEquals(1748470800000L, owner.getAcquiredAtMs());
    }

    @Test
    void differentTimestamps_produceDifferentBodies() {
        byte[] one = LockObjectFormat.encode("host", "1.0", 100L);
        byte[] two = LockObjectFormat.encode("host", "1.0", 200L);
        assertNotEquals(new String(one), new String(two));
    }

    @Test
    void decode_returnsNullForMalformedBody() {
        assertNull(LockObjectFormat.decode("not-a-lock".getBytes()));
        assertNull(LockObjectFormat.decode(new byte[0]));
        assertNull(LockObjectFormat.decode(null));
    }

    @Test
    void encode_rejectsDelimiterInHostname() {
        assertThrows(IllegalArgumentException.class,
                () -> LockObjectFormat.encode("bad|host", "1.0", 0L));
    }

    @Test
    void isExpired_comparesAgeWithTtl() {
        byte[] encoded = LockObjectFormat.encode("h", "v", 1000L);
        LockObjectFormat.LockOwner owner = LockObjectFormat.decode(encoded);
        assertTrue(owner.isExpired(500L, 2000L));
        assertFalse(owner.isExpired(2000L, 2000L));
    }
}
