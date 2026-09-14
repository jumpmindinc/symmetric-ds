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

import static org.junit.Assert.*;

import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.Test;

public class CsvDataTest {
    @Test
    public void testGetCsvData() {
        final String TEST = "\"This is a test\", laughs Kunal.\n\r";
        CsvData data = new CsvData(DataEventType.INSERT, new String[] { TEST });
        String rowData = data.getCsvData(CsvData.ROW_DATA);
        CsvData newData = new CsvData();
        newData.putCsvData(CsvData.ROW_DATA, rowData);
        String result = newData.getParsedData(CsvData.ROW_DATA)[0];
        assertEquals(TEST, result);
    }
}
