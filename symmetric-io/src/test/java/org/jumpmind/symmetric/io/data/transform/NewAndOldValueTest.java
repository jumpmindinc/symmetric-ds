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
package org.jumpmind.symmetric.io.data.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.Test;

class NewAndOldValueTest {
    @Test
    void testNoArgConstructor_hasNullNewAndOldValue() {
        NewAndOldValue value = new NewAndOldValue();
        assertNull(value.getNewValue());
        assertNull(value.getOldValue());
    }

    @Test
    void testTwoArgConstructor_setsNewAndOldValue() {
        NewAndOldValue value = new NewAndOldValue("new", "old");
        assertEquals("new", value.getNewValue());
        assertEquals("old", value.getOldValue());
    }

    @Test
    void testSettersOverrideValues() {
        NewAndOldValue value = new NewAndOldValue("new", "old");
        value.setNewValue("newer");
        value.setOldValue("older");
        assertEquals("newer", value.getNewValue());
        assertEquals("older", value.getOldValue());
    }

    @Test
    void testContextConstructor_withDeleteAndOldSourceValues_setsOldValueOnly() {
        TransformColumn column = new TransformColumn("source", "target", false);
        Map<String, String> oldSourceValues = new HashMap<String, String>();
        oldSourceValues.put("source", "old_val");
        TransformedData data = new TransformedData(null, DataEventType.DELETE, null, oldSourceValues, null);
        NewAndOldValue value = new NewAndOldValue(column, data, "the_value");
        assertEquals("the_value", value.getOldValue());
        assertNull(value.getNewValue());
    }

    @Test
    void testContextConstructor_withDeleteAndNoOldSourceValues_setsNewValueOnly() {
        TransformColumn column = new TransformColumn("source", "target", false);
        TransformedData data = new TransformedData(null, DataEventType.DELETE, null, null, null);
        NewAndOldValue value = new NewAndOldValue(column, data, "the_value");
        assertEquals("the_value", value.getNewValue());
        assertNull(value.getOldValue());
    }

    @Test
    void testContextConstructor_withUpdateAndPkColumn_setsBothNewAndOldValue() {
        TransformColumn column = new TransformColumn("source", "target", true);
        TransformedData data = new TransformedData(null, DataEventType.UPDATE, null, null, null);
        NewAndOldValue value = new NewAndOldValue(column, data, "the_value");
        assertEquals("the_value", value.getNewValue());
        assertEquals("the_value", value.getOldValue());
    }

    @Test
    void testContextConstructor_withUpdateAndNonPkColumn_setsNewValueOnly() {
        TransformColumn column = new TransformColumn("source", "target", false);
        TransformedData data = new TransformedData(null, DataEventType.UPDATE, null, null, null);
        NewAndOldValue value = new NewAndOldValue(column, data, "the_value");
        assertEquals("the_value", value.getNewValue());
        assertNull(value.getOldValue());
    }

    @Test
    void testContextConstructor_withInsert_setsNewValueOnly() {
        TransformColumn column = new TransformColumn("source", "target", true);
        TransformedData data = new TransformedData(null, DataEventType.INSERT, null, null, null);
        NewAndOldValue value = new NewAndOldValue(column, data, "the_value");
        assertEquals("the_value", value.getNewValue());
        assertNull(value.getOldValue());
    }
}
