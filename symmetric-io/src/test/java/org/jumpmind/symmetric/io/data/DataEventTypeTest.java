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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.junit.jupiter.api.Test;

class DataEventTypeTest {
    @Test
    void testGetCode() {
        assertEquals("I", DataEventType.INSERT.getCode());
        assertEquals("U", DataEventType.UPDATE.getCode());
        assertEquals("D", DataEventType.DELETE.getCode());
        assertEquals("R", DataEventType.RELOAD.getCode());
        assertEquals("S", DataEventType.SQL.getCode());
        assertEquals("C", DataEventType.CREATE.getCode());
        assertEquals("B", DataEventType.BSH.getCode());
        assertEquals("U", DataEventType.UNKNOWN.getCode());
    }

    @Test
    void testIsDml() {
        assertTrue(DataEventType.INSERT.isDml());
        assertTrue(DataEventType.UPDATE.isDml());
        assertTrue(DataEventType.DELETE.isDml());
    }

    @Test
    void testIsDml_withNonDmlTypes() {
        assertFalse(DataEventType.RELOAD.isDml());
        assertFalse(DataEventType.SQL.isDml());
        assertFalse(DataEventType.CREATE.isDml());
        assertFalse(DataEventType.BSH.isDml());
        assertFalse(DataEventType.UNKNOWN.isDml());
    }

    @Test
    void testGetDmlType() {
        assertEquals(DmlType.INSERT, DataEventType.INSERT.getDmlType());
        assertEquals(DmlType.UPDATE, DataEventType.UPDATE.getDmlType());
        assertEquals(DmlType.DELETE, DataEventType.DELETE.getDmlType());
    }

    @Test
    void testGetDmlType_withNonDmlTypes() {
        assertEquals(DmlType.UNKNOWN, DataEventType.RELOAD.getDmlType());
        assertEquals(DmlType.UNKNOWN, DataEventType.SQL.getDmlType());
        assertEquals(DmlType.UNKNOWN, DataEventType.CREATE.getDmlType());
        assertEquals(DmlType.UNKNOWN, DataEventType.BSH.getDmlType());
        assertEquals(DmlType.UNKNOWN, DataEventType.UNKNOWN.getDmlType());
    }

    @Test
    void testGetEventType() {
        assertEquals(DataEventType.INSERT, DataEventType.getEventType("I"));
        assertEquals(DataEventType.DELETE, DataEventType.getEventType("D"));
        assertEquals(DataEventType.RELOAD, DataEventType.getEventType("R"));
        assertEquals(DataEventType.SQL, DataEventType.getEventType("S"));
        assertEquals(DataEventType.CREATE, DataEventType.getEventType("C"));
        assertEquals(DataEventType.BSH, DataEventType.getEventType("B"));
    }

    @Test
    void testGetEventType_withUpdateCode() {
        // Defect pinned, not endorsed: UPDATE and UNKNOWN share the code "U", so UNKNOWN is unreachable here
        assertEquals(DataEventType.UPDATE, DataEventType.getEventType("U"));
    }

    @Test
    void testGetEventType_throwsWhenCodeIsUnrecognized() {
        assertThrows(IllegalStateException.class, () -> DataEventType.getEventType("X"));
    }

    @Test
    void testGetEventType_throwsWhenCodeIsNull() {
        assertThrows(NullPointerException.class, () -> DataEventType.getEventType(null));
    }
}
