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
package org.jumpmind.symmetric.io.data.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataProcessor;
import org.junit.jupiter.api.Test;

public class XmlDataReaderTest {
    @Test
    public void testNilDataElement() {
        XmlDataReader reader = new XmlDataReader(getClass().getResourceAsStream("xmldatareadertest1.xml"));
        TestableDataWriter writer = new TestableDataWriter();
        DataProcessor processor = new DataProcessor(reader, writer, "test");
        processor.process();
        List<CsvData> dataRead = writer.getDatas();
        assertEquals(4, dataRead.size());
        Map<String, String> data1 = dataRead.get(1).toColumnNameValuePairs(writer.getLastTableRead().getColumnNames(), CsvData.ROW_DATA);
        assertEquals("1", data1.get("id"));
        assertEquals("A", data1.get("my_value"));
        Map<String, String> data2 = dataRead.get(2).toColumnNameValuePairs(writer.getLastTableRead().getColumnNames(), CsvData.ROW_DATA);
        assertEquals("2", data2.get("id"));
        assertNull(data2.get("my_value"));
    }
}
