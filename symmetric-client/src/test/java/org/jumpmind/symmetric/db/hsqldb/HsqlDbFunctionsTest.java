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
package org.jumpmind.symmetric.db.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class HsqlDbFunctionsTest {
    @Test
    void testGetSession_withUnknownKey() {
        assertNull(HsqlDbFunctions.getSession("never_set_key"));
    }

    @Test
    void testSetSession_storesValue() {
        HsqlDbFunctions.setSession("stores_value", "node-001");
        assertEquals("node-001", HsqlDbFunctions.getSession("stores_value"));
    }

    @Test
    void testSetSession_overwritesValue() {
        HsqlDbFunctions.setSession("overwrites_value", "first");
        HsqlDbFunctions.setSession("overwrites_value", "second");
        assertEquals("second", HsqlDbFunctions.getSession("overwrites_value"));
    }

    @Test
    void testSetSession_withNullClearsValue() {
        HsqlDbFunctions.setSession("clears_value", "node-001");
        HsqlDbFunctions.setSession("clears_value", null);
        assertNull(HsqlDbFunctions.getSession("clears_value"));
    }

    @Test
    void testSetSession_withNullOnUnusedKey() {
        HsqlDbFunctions.setSession("null_on_unused", null);
        assertNull(HsqlDbFunctions.getSession("null_on_unused"));
    }

    @Test
    void testSetSession_isolatesValuePerThread() throws InterruptedException {
        HsqlDbFunctions.setSession("per_thread", "main-thread-value");
        AtomicReference<String> seenByOtherThread = new AtomicReference<>("unset");
        Thread other = new Thread(() -> seenByOtherThread.set(HsqlDbFunctions.getSession("per_thread")));
        other.start();
        other.join();
        assertNull(seenByOtherThread.get());
        assertEquals("main-thread-value", HsqlDbFunctions.getSession("per_thread"));
    }

    @Test
    void testSetSession_keepsKeysIndependent() {
        HsqlDbFunctions.setSession("independent_a", "a");
        HsqlDbFunctions.setSession("independent_b", "b");
        assertEquals("a", HsqlDbFunctions.getSession("independent_a"));
        assertEquals("b", HsqlDbFunctions.getSession("independent_b"));
    }
}
