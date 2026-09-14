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
package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.ProtocolException;

import org.junit.jupiter.api.Test;

class DataUtilsTest {
    @Test
    void testColumnNamesMatchValues_throwsOnMismatch() {
        Data data = mock(Data.class);
        when(data.getTableName()).thenReturn("mytable");
        DataMetaData dmd = mock(DataMetaData.class);
        when(dmd.getData()).thenReturn(data);
        String[] columnNames = new String[] { "a", "b" };
        Object[] values = new Object[] { "1" };
        assertThrows(ProtocolException.class, () -> DataUtils.testColumnNamesMatchValues(
                dmd, columnNames, values));
    }

    @Test
    void testColumnNamesMatchValues_passesWhenEqual() {
        DataMetaData dmd = mock(DataMetaData.class);
        String[] columnNames = new String[] { "a", "b" };
        Object[] values = new Object[] { "1", "2" };
        assertDoesNotThrow(() -> DataUtils.testColumnNamesMatchValues(
                dmd, columnNames, values));
    }

    @Test
    void testGetDataAsString_mapsColumnsToValues() {
        TriggerHistory hist = mock(TriggerHistory.class);
        when(hist.getParsedColumnNames()).thenReturn(new String[] { "id", "name" });
        DataMetaData dmd = mock(DataMetaData.class);
        when(dmd.getTriggerHistory()).thenReturn(hist);
        Map<String, String> result = DataUtils.getDataAsString(
                null, dmd, new String[] { "1", "alice" });
        assertEquals("1", result.get("id"));
        assertEquals("alice", result.get("name"));
    }

    @Test
    void testGetNullData_putsNullForEachColumn() {
        TriggerHistory hist = mock(TriggerHistory.class);
        when(hist.getParsedColumnNames()).thenReturn(new String[] { "id", "name" });
        DataMetaData dmd = mock(DataMetaData.class);
        when(dmd.getTriggerHistory()).thenReturn(hist);
        Map<String, Object> result = DataUtils.getNullData(null, dmd);
        assertTrue(result.containsKey("id"));
        assertNull(result.get("id"));
        assertTrue(result.containsKey("name"));
        assertNull(result.get("name"));
    }

    @Test
    void testGetNullData_appliesPrefix() {
        TriggerHistory hist = mock(TriggerHistory.class);
        when(hist.getParsedColumnNames()).thenReturn(new String[] { "id", "name" });
        DataMetaData dmd = mock(DataMetaData.class);
        when(dmd.getTriggerHistory()).thenReturn(hist);
        Map<String, Object> result = DataUtils.getNullData("OLD_", dmd);
        assertTrue(result.containsKey("OLD_id"));
        assertTrue(result.containsKey("OLD_name"));
        assertNull(result.get("OLD_id"));
    }

    @Test
    void testGetPkDataAsString_mapsPkColumnsToValues() {
        TriggerHistory hist = mock(TriggerHistory.class);
        when(hist.getParsedPkColumnNames()).thenReturn(new String[] { "id" });
        Data data = mock(Data.class);
        when(data.toParsedPkData()).thenReturn(new String[] { "42" });
        DataMetaData dmd = mock(DataMetaData.class);
        when(dmd.getTriggerHistory()).thenReturn(hist);
        when(dmd.getData()).thenReturn(data);
        Map<String, String> result = DataUtils.getPkDataAsString(dmd);
        assertEquals("42", result.get("id"));
    }

    @Test
    void testGetPkDataAsString_emptyWhenPkDataNull() {
        TriggerHistory hist = mock(TriggerHistory.class);
        when(hist.getParsedPkColumnNames()).thenReturn(new String[] { "id" });
        Data data = mock(Data.class);
        when(data.toParsedPkData()).thenReturn(null); // no PK data
        DataMetaData dmd = mock(DataMetaData.class);
        when(dmd.getTriggerHistory()).thenReturn(hist);
        when(dmd.getData()).thenReturn(data);
        assertTrue(DataUtils.getPkDataAsString(dmd).isEmpty());
    }

    @Test
    void testGetDataMap_insertMapsNewDataAndNullOldData() {
        Data data = mock(Data.class);
        when(data.getDataEventType()).thenReturn(DataEventType.INSERT);
        when(data.toParsedRowData()).thenReturn(new String[] { "1", "alice" });
        TriggerHistory hist = mock(TriggerHistory.class);
        when(hist.getParsedColumnNames()).thenReturn(new String[] { "id", "name" });
        DataMetaData dmd = mock(DataMetaData.class);
        when(dmd.getData()).thenReturn(data);
        when(dmd.getTriggerHistory()).thenReturn(hist);
        when(dmd.getRelation()).thenReturn(null);
        Map<String, String> result = DataUtils.getDataMap(dmd);
        assertEquals("1", result.get("id"));
        assertEquals("alice", result.get("name"));
    }

    @Test
    void testGetDataMap_updateMapsNewAndOldData() {
        Data data = mock(Data.class);
        when(data.getDataEventType()).thenReturn(DataEventType.UPDATE);
        when(data.toParsedRowData()).thenReturn(new String[] { "1", "alice" });
        when(data.toParsedOldData()).thenReturn(new String[] { "0", "bob" });
        TriggerHistory hist = mock(TriggerHistory.class);
        when(hist.getParsedColumnNames()).thenReturn(new String[] { "id", "name" });
        DataMetaData dmd = mock(DataMetaData.class);
        when(dmd.getData()).thenReturn(data);
        when(dmd.getTriggerHistory()).thenReturn(hist);
        when(dmd.getRelation()).thenReturn(null);
        Map<String, String> result = DataUtils.getDataMap(dmd);
        assertEquals("1", result.get("id"));
        assertEquals("alice", result.get("name"));
        assertEquals("0", result.get("OLD_id"));
        assertEquals("bob", result.get("OLD_name"));
    }

    @Test
    void testGetDataMap_deleteMapsOldData() {
        Data data = mock(Data.class);
        when(data.getDataEventType()).thenReturn(DataEventType.DELETE);
        when(data.toParsedOldData()).thenReturn(new String[] { "1", "alice" });
        TriggerHistory hist = mock(TriggerHistory.class);
        when(hist.getParsedColumnNames()).thenReturn(new String[] { "id", "name" });
        DataMetaData dmd = mock(DataMetaData.class);
        when(dmd.getData()).thenReturn(data);
        when(dmd.getTriggerHistory()).thenReturn(hist);
        when(dmd.getRelation()).thenReturn(null);
        Map<String, String> result = DataUtils.getDataMap(dmd);
        assertEquals("1", result.get("id"));
        assertEquals("1", result.get("OLD_id"));
        assertEquals("alice", result.get("OLD_name"));
    }
}
