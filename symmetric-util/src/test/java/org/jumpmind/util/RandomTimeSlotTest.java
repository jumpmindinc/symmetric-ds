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
package org.jumpmind.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RandomTimeSlotTest {
    @Test
    void testGetRandomValueSeededByExternalId_alwaysWithinBounds() {
        // nextInt(maxValue) is in [0, maxValue), and 0 is remapped to 1,
        // so every result is >= 1 and < maxValue. True for any RNG provider.
        RandomTimeSlot slot = new RandomTimeSlot("node-001", 100);
        for (int i = 0; i < 1000; i++) {
            int value = slot.getRandomValueSeededByExternalId();
            assertTrue(value >= 1 && value < 100, "out of range: " + value);
        }
    }

    @Test
    void testGetRandomValueSeededByExternalId_maxValueOfOneReturnsOne() {
        // nextInt(1) is always 0, which is remapped to 1.
        RandomTimeSlot slot = new RandomTimeSlot("node-001", 1);
        assertEquals(1, slot.getRandomValueSeededByExternalId());
    }

    @Test
    void testGetRandomValueSeededByExternalId_maxValueOfTwoReturnsOne() {
        // nextInt(2) is 0 or 1; both collapse to 1 (0 -> 1, 1 stays 1).
        RandomTimeSlot slot = new RandomTimeSlot("node-001", 2);
        assertEquals(1, slot.getRandomValueSeededByExternalId());
    }

    @Test
    void testGetRandomValueSeededByExternalId_noArgConstructorThrows() {
        // The no-arg constructor never sets maxValue (stays -1), so nextInt(-1) throws.
        RandomTimeSlot slot = new RandomTimeSlot();
        assertThrows(IllegalArgumentException.class, slot::getRandomValueSeededByExternalId);
    }

    @Test
    void testGetRandomValueSeededByExternalId_nullExternalIdThrows() {
        // A null externalId skips RNG creation, so the method dereferences a null SecureRandom.
        RandomTimeSlot slot = new RandomTimeSlot(null, 100);
        assertThrows(NullPointerException.class, slot::getRandomValueSeededByExternalId);
    }
}
