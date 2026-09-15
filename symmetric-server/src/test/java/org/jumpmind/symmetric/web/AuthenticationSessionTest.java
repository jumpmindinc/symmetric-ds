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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationSessionTest {
    private AuthenticationSession session;

    @BeforeEach
    void setUp() {
        session = new AuthenticationSession("session-1");
    }

    @Test
    void testGetId() {
        assertEquals("session-1", session.getId());
    }

    @Test
    void testSetId() {
        session.setId("session-2");
        assertEquals("session-2", session.getId());
    }

    @Test
    void testGetCreationTime_isSetOnConstruction() {
        assertTrue(session.getCreationTime() > 0);
    }

    @Test
    void testSetCreationTime() {
        session.setCreationTime(1700000000000L);
        assertEquals(1700000000000L, session.getCreationTime());
    }

    @Test
    void testSetAttribute_storesValue() {
        session.setAttribute("nodeId", "store-1");
        assertEquals("store-1", session.getAttribute("nodeId"));
    }

    @Test
    void testSetAttribute_returnsPreviousValue() {
        session.setAttribute("nodeId", "store-1");
        assertEquals("store-1", session.setAttribute("nodeId", "store-2"));
    }

    @Test
    void testGetAttribute_withUnknownName() {
        assertNull(session.getAttribute("missing"));
    }

    @Test
    void testAttributesAreBackedByTheMap() {
        session.setAttribute("nodeId", "store-1");
        assertEquals("store-1", session.get("nodeId"));
    }

    @Test
    void testEquals_withSameId() {
        assertEquals(new AuthenticationSession("session-1"), session);
    }

    @Test
    void testEquals_withDifferentId() {
        assertNotEquals(new AuthenticationSession("session-2"), session);
    }

    @Test
    void testEquals_withOtherType() {
        assertNotEquals("session-1", session);
    }

    @Test
    void testEquals_withNull() {
        assertNotEquals(null, session);
    }

    @Test
    void testHashCode_matchesIdHashCode() {
        assertEquals("session-1".hashCode(), session.hashCode());
    }

    @Test
    void testHashCode_ignoresAttributes() {
        session.setAttribute("nodeId", "store-1");
        assertEquals(new AuthenticationSession("session-1").hashCode(), session.hashCode());
    }
}
