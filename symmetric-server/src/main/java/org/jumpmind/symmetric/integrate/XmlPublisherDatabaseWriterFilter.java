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

import java.util.List;
import java.util.Set;

import org.jdom2.Element;
import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.ext.INodeGroupExtensionPoint;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;

/**
 * This is an optional {@link IDatabaseWriterFilter} that is capable of translating table data to XML and publishing it for consumption by the enterprise. It
 * uses JDOM internally to create an XML representation of SymmetricDS data.
 * <p>
 * This filter is typically configured as a Spring bean. The table names that should be published are identified by
 * {@link #setRelationNamesToPublishAsGroup(Set)}. Rows from tables can be grouped together (which get synchronized in the same batch) by identifying columns
 * that are the same that act as a 'key' by setting {@link #setGroupByColumnNames(List)}
 * <p>
 * The {@link IPublisher} is typically configured and injected onto this bean as well. Provided is a {@link SimpleJmsPublisher}.
 * <p>
 * An example of the XML that is published is as follows:
 * 
 * <pre>
 * &lt;batch id="2TEST2" nodeid="00001" time="12345678910"&gt;
 *   &lt;row entity="TABLE_NAME" dml="I"&gt;
 *     &lt;data key="id1"&gt;2&lt;/data&gt;
 *     &lt;data key="id2"&gt;TEST&lt;/data&gt;
 *     &lt;data key="id3"&gt;2&lt;/data&gt;
 *     &lt;data key="data1"&gt;Me&lt;/data&gt;
 *     &lt;data key="data2" nil="true"/&gt;
 *   &lt;/row&gt;
 * &lt;/batch&gt;
 * </pre>
 */
public class XmlPublisherDatabaseWriterFilter extends AbstractXmlPublisherExtensionPoint implements
        IPublisherFilter, INodeGroupExtensionPoint {
    public static final String PUBLISH_ON_COMPLETE = "COMPLETE";
    public static final String PUBLISH_ON_COMMIT = "COMMIT";
    protected boolean loadDataInTargetDatabase = true;
    protected String publishOn = PUBLISH_ON_COMPLETE;

    public boolean beforeWrite(
            DataContext context, Table table, CsvData data) {
        if (relationNamesToPublishAsGroup == null
                || relationNamesToPublishAsGroup.contains(table.getName())) {
            String[] rowData = data.getParsedData(CsvData.ROW_DATA);
            if (data.getDataEventType() == DataEventType.DELETE) {
                rowData = data.getParsedData(CsvData.OLD_DATA);
            }
            Element xml = getXmlFromCache(context, context.getBatch().getBinaryEncoding(),
                    table.getColumnNames(),
                    rowData, table.getPrimaryKeyColumnNames(),
                    data.getParsedData(CsvData.PK_DATA));
            if (xml != null) {
                toXmlElement(data.getDataEventType(), xml, table.getCatalog(), table.getSchema(),
                        table.getName(), table.getColumnNames(),
                        rowData, table.getPrimaryKeyColumnNames(), data.getParsedData(CsvData.PK_DATA));
            }
        }
        return loadDataInTargetDatabase;
    }

    public void setLoadDataInTargetDatabase(boolean loadDataInTargetDatabase) {
        this.loadDataInTargetDatabase = loadDataInTargetDatabase;
    }

    public void setPublishOn(String publishOn) {
        this.publishOn = publishOn;
    }

    public void afterWrite(
            DataContext context, Table table, CsvData data) {
    }

    public boolean handlesMissingTable(
            DataContext context, Table table) {
        return false;
    }

    public void earlyCommit(
            DataContext context) {
    }

    public void batchComplete(
            DataContext context) {
        if (PUBLISH_ON_COMPLETE.equals(publishOn)) {
            publish(context);
        }
    }

    public void batchCommitted(
            DataContext context) {
        if (PUBLISH_ON_COMMIT.equals(publishOn)) {
            publish(context);
        }
    }

    private void publish(DataContext context) {
        if (doesXmlExistToPublish(context)) {
            finalizeXmlAndPublish(context);
        }
    }

    public void batchRolledback(
            DataContext context) {
    }
}
