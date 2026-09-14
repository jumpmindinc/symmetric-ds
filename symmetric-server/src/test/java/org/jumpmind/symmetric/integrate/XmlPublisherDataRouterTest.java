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
package org.jumpmind.symmetric.integrate;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.AbstractSymmetricDialect;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.route.SimpleRouterContext;
import org.jumpmind.util.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class XmlPublisherDataRouterTest {
    private static final String TABLE_NAME = "TEST_XML_PUBLISHER";
    private static final String INSERT_XML = "<batch xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"1\" binary=\"BASE64\" time=\"test\">" +
            "<row entity=\"" + TABLE_NAME + "\" dml=\"I\"><data key=\"ID\">1</data><data key=\"DATA\">new inserted data</data></row></batch>";
    private static final String UPDATE_XML = "<batch xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"2\" binary=\"BASE64\" time=\"test\">" +
            "<row entity=\"" + TABLE_NAME + "\" dml=\"U\"><data key=\"ID\">2</data><data key=\"DATA\">updated data</data></row></batch>";
    private static final String DELETE_XML = "<batch xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"3\" binary=\"BASE64\" time=\"test\">" +
            "<row entity=\"" + TABLE_NAME + "\" dml=\"D\"><data key=\"ID\">3</data><data key=\"DATA\">old deleted data</data></row></batch>";
    private XmlPublisherDataRouter router;
    private SimpleRouterContext context;
    private Table table;
    private Output output;

    public XmlPublisherDataRouterTest() {
    }

    @BeforeEach
    public void setUp() {
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        when(engine.getSymmetricDialect()).thenReturn(mock(AbstractSymmetricDialect.class));
        when(engine.getSymmetricDialect().getBinaryEncoding()).thenReturn(BinaryEncoding.BASE64);
        table = Table.buildTable(TABLE_NAME, new String[] { "ID" }, new String[] { "ID", "DATA" });
        context = new SimpleRouterContext();
        router = new XmlPublisherDataRouter();
        router.setSymmetricEngine(engine);
        router.setTimeStringGenerator(new XmlPublisherDatabaseWriterFilter.ITimeGenerator() {
            public String getTime() {
                return "test";
            }
        });
        List<String> groupByColumnNames = new ArrayList<String>();
        groupByColumnNames.add("ID");
        router.setGroupByColumnNames(groupByColumnNames);
        output = new Output();
        router.setPublisher(output);
    }

    @Test
    public void testPublishInsertAsXml() {
        Data data = new Data();
        data.setDataEventType(DataEventType.INSERT);
        data.setRowData("1,new inserted data");
        data.setTriggerHistory(new TriggerHistory(TABLE_NAME, "ID", "ID,DATA"));
        data.setTableName(TABLE_NAME);
        router.routeToNodes(context, new DataMetaData(data, table, null, null), null, false, false, null);
        router.contextCommitted(context);
        assertEquals(INSERT_XML, output.toString().trim());
    }

    @Test
    public void testPublishUpdateAsXml() {
        Data data = new Data();
        data.setDataEventType(DataEventType.UPDATE);
        data.setRowData("2,updated data");
        data.setTriggerHistory(new TriggerHistory(TABLE_NAME, "ID", "ID,DATA"));
        data.setTableName(TABLE_NAME);
        router.routeToNodes(context, new DataMetaData(data, table, null, null), null, false, false, null);
        router.contextCommitted(context);
        assertEquals(UPDATE_XML, output.toString().trim());
    }

    @Test
    public void testPublishDeleteAsXml() {
        Data data = new Data();
        data.setDataEventType(DataEventType.DELETE);
        data.setOldData("3,old deleted data");
        data.setTriggerHistory(new TriggerHistory(TABLE_NAME, "ID", "ID,DATA"));
        data.setTableName(TABLE_NAME);
        router.routeToNodes(context, new DataMetaData(data, table, null, null), null, false, false, null);
        router.contextCommitted(context);
        assertEquals(DELETE_XML, output.toString().trim());
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
