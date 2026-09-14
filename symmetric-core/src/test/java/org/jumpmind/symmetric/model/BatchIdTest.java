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
package org.jumpmind.symmetric.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BatchIdTest {
    @Test
    void testToString_toStringFormatsAsNodeDashBatch() {
        BatchId b = new BatchId(123, "node1");
        assertEquals("node1-123", b.toString());
    }

    @Test
    void testGetters_constructorSetsFields() {
        BatchId b = new BatchId(123, "node1");
        assertEquals(123, b.getBatchId());
        assertEquals("node1", b.getNodeId());
    }

    @Test
    void testGetters_settersUpdateFields() {
        BatchId b = new BatchId();
        b.setBatchId(7);
        b.setNodeId("node2");
        assertEquals(7, b.getBatchId());
        assertEquals("node2", b.getNodeId());
    }

    @Test
    void testEquals_equalsTrueForSameInstance() {
        BatchId b = new BatchId(1, "b");
        assertEquals(b, b);
    }

    @Test
    void testEquals_equalsTrueForSameValues() {
        BatchId b = new BatchId(1, "b");
        BatchId a = new BatchId(1, "b");
        assertEquals(a, b);
    }

    @Test
    void testEquals_equalsFalseForNull() {
        BatchId b = new BatchId(1, "b");
        assertNotEquals(null, b);
    }

    @Test
    void testEquals_equalsFalseForDifferentType() {
        assertNotEquals("not a BatchId", new BatchId(1, "a"));
    }

    @Test
    void testEquals_equalsFalseForDifferentBatchId() {
        BatchId b = new BatchId(1, "b");
        BatchId a = new BatchId(2, "b");
        assertNotEquals(b, a);
    }

    @Test
    void testEquals_equalsFalseForDifferentNodeId() {
        BatchId b = new BatchId(1, "b");
        BatchId a = new BatchId(1, "a");
        assertNotEquals(b, a);
    }

    @Test
    void testEquals_equalsTrueWhenBothNodeIdsNull() {
        BatchId b = new BatchId(1, null);
        BatchId a = new BatchId(1, null);
        assertEquals(a, b);
    }

    @Test
    void testEquals_equalsFalseWhenThisNodeIdsNull() {
        BatchId b = new BatchId(1, null);
        BatchId a = new BatchId(1, "a");
        assertNotEquals(b, a);
    }

    @Test
    void testHashCode_equalObjectsHaveEqualHashCodes() {
        BatchId b = new BatchId(1, "b");
        BatchId a = new BatchId(1, "b");
        assertEquals(a.hashCode(), b.hashCode());
    }
}
