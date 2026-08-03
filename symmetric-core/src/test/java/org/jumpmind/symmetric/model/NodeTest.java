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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NodeTest {
    @Test
    void testIsVersionGreaterThan() {
        Node test = new Node();
        test.setSymmetricVersion("1.5.0");
        assertTrue(test.isVersionGreaterThanOrEqualTo(1, 3, 0));
        assertFalse(test.isVersionGreaterThanOrEqualTo(2, 0, 0));
        assertFalse(test.isVersionGreaterThanOrEqualTo(2, 0, 0));
        assertTrue(test.isVersionGreaterThanOrEqualTo(1, 4, 9, 1));
        assertTrue(test.isVersionGreaterThanOrEqualTo(1, 5, 0));
        assertFalse(test.isVersionGreaterThanOrEqualTo(1, 5, 1));
        test.setSymmetricVersion("1.5.0-SNAPSHOT");
        assertTrue(test.isVersionGreaterThanOrEqualTo(1, 3, 0));
        assertFalse(test.isVersionGreaterThanOrEqualTo(2, 0, 0));
        assertTrue(test.isVersionGreaterThanOrEqualTo(1, 5, 0));
        test.setSymmetricVersion("development");
        assertTrue(test.isVersionGreaterThanOrEqualTo(1, 3, 0));
        assertTrue(test.isVersionGreaterThanOrEqualTo(2, 0, 0));
    }

    @Test
    void testEquals_sameNodeId_returnsTrue() {
        Node a = new Node("00000", "http://host/sync/00000", "1.0.0");
        Node b = new Node("00000", "http://otherhost/sync/00000", "2.0.0");
        assertEquals(a, b);
    }

    @Test
    void testEquals_differentNodeId_returnsFalse() {
        Node a = new Node("00000", "http://host/sync/00000", "1.0.0");
        Node b = new Node("00001", "http://host/sync/00000", "1.0.0");
        assertNotEquals(a, b);
    }

    @Test
    void testEquals_null_returnsFalse() {
        Node a = new Node("00000", "http://host/sync/00000", "1.0.0");
        assertNotEquals(a, null);
    }

    @Test
    void testEquals_thisNodeIdNull_returnsFalse() {
        Node a = new Node();
        Node b = new Node("00000", "http://host/sync/00000", "1.0.0");
        assertNotEquals(a, b);
    }

    @Test
    void testEquals_bothNodeIdNull_returnsFalse() {
        Node a = new Node();
        Node b = new Node();
        assertNotEquals(a, b);
    }

    @Test
    void testEquals_otherNodeIdNull_returnsFalse() {
        Node a = new Node("00000", "http://host/sync/00000", "1.0.0");
        Node b = new Node();
        assertNotEquals(a, b);
    }

    @Test
    void testEquals_sameReference_returnsTrue() {
        Node a = new Node("00000", "http://host/sync/00000", "1.0.0");
        assertEquals(a, a);
    }

    @Test
    void testEquals_ignoresFieldsOtherThanNodeId() {
        Node a = new Node("00000", "http://host/sync/00000", "1.0.0");
        a.setNodeGroupId("groupA");
        a.setDatabaseType("mysql");
        Node b = new Node("00000", "http://otherhost/sync/00000", "9.9.9");
        b.setNodeGroupId("groupB");
        b.setDatabaseType("postgres");
        assertEquals(a, b);
    }
}