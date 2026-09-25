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
package org.jumpmind.symmetric.io.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DmlWeightTest {
    @Test
    void testConstructor_withNoArguments() {
        assertWeights(new DmlWeight(), 0, 0, 0);
    }

    @Test
    void testConstructor_withWeights() {
        assertWeights(new DmlWeight(1, 2, 3), 1, 2, 3);
    }

    @Test
    void testConstructor_withCsv() {
        assertWeights(new DmlWeight("1,2,3"), 1, 2, 3);
    }

    @Test
    void testConstructor_withCsvMissingDeleteWeight() {
        assertWeights(new DmlWeight("1,2"), 1, 2, 0);
    }

    @Test
    void testConstructor_withCsvHoldingOnlyInsertWeight() {
        assertWeights(new DmlWeight("1"), 1, 0, 0);
    }

    @Test
    void testConstructor_withCsvHoldingExtraWeights() {
        assertWeights(new DmlWeight("1,2,3,4"), 1, 2, 3);
    }

    @Test
    void testConstructor_withNullCsv() {
        assertWeights(new DmlWeight(null), 0, 0, 0);
    }

    @Test
    void testConstructor_throwsWhenCsvIsNotNumeric() {
        assertThrows(NumberFormatException.class, () -> new DmlWeight("one,2,3"));
    }

    @Test
    void testSetters() {
        DmlWeight weight = new DmlWeight();
        weight.setInsertWeight(4);
        weight.setUpdateWeight(5);
        weight.setDeleteWeight(6);
        assertWeights(weight, 4, 5, 6);
    }

    private void assertWeights(DmlWeight weight, int insertWeight, int updateWeight, int deleteWeight) {
        assertEquals(insertWeight, weight.getInsertWeight());
        assertEquals(updateWeight, weight.getUpdateWeight());
        assertEquals(deleteWeight, weight.getDeleteWeight());
    }
}
