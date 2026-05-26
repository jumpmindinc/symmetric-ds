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
package org.jumpmind.symmetric.integrate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.util.Context;
import static org.junit.Assert.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class XmlPublisherFilterTest {
    private static final String TABLE_TEST = "TEST_XML_PUBLISHER";
    private static final String TEST_SIMPLE_TRANSFORM_RESULTS = "<batch xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"12\" binary=\"BASE64\" nodeid=\"54321\" batchid=\"1111\" time=\"test\"><row entity=\"TEST_XML_PUBLISHER\" dml=\"I\"><data key=\"ID1\">1</data><data key=\"ID2\">2</data><data key=\"DATA1\">test embedding an &amp;</data><data key=\"DATA2\">3</data><data key=\"DATA3\" xsi:nil=\"true\" /></row></batch>";
    private DataContext context;
    private Table table;

    public XmlPublisherFilterTest() {
    }

    @BeforeEach
    public void setUp() {
        context = new DataContext(new Batch(BatchType.LOAD, 1111, "default", BinaryEncoding.BASE64, "54321", "00000", false));
        table = Table.buildTable(TABLE_TEST, new String[] { "ID1", "ID2" }, new String[] { "ID1", "ID2", "DATA1", "DATA2", "DATA3" });
    }

    @Test
    public void testSimpleTransform() {
        XmlPublisherDatabaseWriterFilter filter = new XmlPublisherDatabaseWriterFilter();
        filter.setTimeStringGenerator(new XmlPublisherDatabaseWriterFilter.ITimeGenerator() {
            public String getTime() {
                return "test";
            }
        });
        HashSet<String> tableNames = new HashSet<String>();
        tableNames.add(TABLE_TEST);
        filter.setRelationNamesToPublishAsGroup(tableNames);
        List<String> columns = new ArrayList<String>();
        columns.add("ID1");
        columns.add("ID2");
        filter.setGroupByColumnNames(columns);
        Output output = new Output();
        filter.setPublisher(output);
        String[][] datas = { { "1", "1", "The Angry Brown", "3", "2008-10-24 00:00:00.0" },
                { "1", "2", "test embedding an &", "3", null } };
        for (String[] strings : datas) {
            CsvData data = new CsvData(DataEventType.INSERT, strings);
            filter.beforeWrite(context, table, data);
            filter.batchComplete(context);
        }
        assertEquals(TEST_SIMPLE_TRANSFORM_RESULTS, output.toString().trim());
    }

    static class Output implements IPublisher {
        private String output;

        public void publish(Context context, String text) {
            this.output = text;
        }

        @Override
        public String toString() {
            return output;
        }
    }
}