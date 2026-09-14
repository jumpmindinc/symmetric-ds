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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClarionDateTimeColumnTransformTest {
    private ClarionDateTimeColumnTransform clarionDateTimeColumnTransform;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        clarionDateTimeColumnTransform = new ClarionDateTimeColumnTransform();
        transformColumn = new TransformColumn();
        transformedData = new TransformedData(null, DataEventType.INSERT, null, null, null);
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("clarionDateTime", clarionDateTimeColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform_returnsTrue() {
        assertTrue(clarionDateTimeColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform_returnsTrue() {
        assertTrue(clarionDateTimeColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testConvertClarionDate_withDateOnly_returnsMidnight() {
        assertEquals("1800-12-28 00:00:00.0", clarionDateTimeColumnTransform.convertClarionDate("0", null));
    }

    @Test
    void testConvertClarionDate_withDateAndTime_addsMilliseconds() {
        assertEquals("1800-12-28 00:00:01.0", clarionDateTimeColumnTransform.convertClarionDate("0", "101"));
    }

    @Test
    void testConvertClarionDate_withFutureDate_addsDays() {
        assertEquals("1800-12-30 00:00:00.0", clarionDateTimeColumnTransform.convertClarionDate("2", null));
    }

    @Test
    void testConvertClarionDate_withNullDate_returnsNull() {
        assertNull(clarionDateTimeColumnTransform.convertClarionDate(null, null));
    }

    @Test
    void testConvertClarionDate_withBlankDate_returnsNull() {
        assertNull(clarionDateTimeColumnTransform.convertClarionDate("", null));
    }

    @Test
    void testTransform_readsTimeColumnFromSourceValues() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("TIME_COL");
        sourceValues.put("TIME_COL", "101");
        NewAndOldValue result = clarionDateTimeColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "0", null);
        assertEquals("1800-12-28 00:00:01.0", result.getNewValue());
    }

    @Test
    void testTransform_withNoTimeColumnConfigured_usesDateOnly() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression(null);
        NewAndOldValue result = clarionDateTimeColumnTransform.transform(null, null, transformColumn, transformedData, sourceValues, "0", null);
        assertEquals("1800-12-28 00:00:00.0", result.getNewValue());
    }
}
