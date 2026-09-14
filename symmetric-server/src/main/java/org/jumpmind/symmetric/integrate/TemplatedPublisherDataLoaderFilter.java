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

import java.text.ParseException;
import java.util.Map;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;

/**
 * A convenience class that allows the end user to template a message using SymmetricDS filter data.
 * </p>
 * You may use %COLUMN% formatted tokens in your template data which will be replaced by data coming in through the filter. The following tokens are also
 * supported:
 * <ol>
 * <li>%DMLTYPE% - evaluates to INSERT, UPDATE or DELETE</li>
 * <li>%TIMESTAMP% - evaluates to ms value returned by System.currentTimeInMillis()</li>
 * </ol>
 * </p>
 * If you have special formatting needs, implement the {@link IFormat} interface and map your formatter to the column you want to 'massage.'
 */
public class TemplatedPublisherDataLoaderFilter extends AbstractTextPublisherDataLoaderFilter {
    private String headerTableTemplate;
    private String footerTableTemplate;
    private String contentTableTemplate;
    private Map<String, IFormat> columnNameToDataFormatter;
    private boolean processDelete = true;
    private boolean processInsert = true;
    private boolean processUpdate = true;
    private IDatabaseWriterFilter dataFilter;

    @Override
    protected String addTextElement(
            DataContext context, Table table,
            CsvData data) {
        if (this.dataFilter == null
                || this.dataFilter.beforeWrite(context, table, data)) {
            DataEventType eventType = data.getDataEventType();
            String template = null;
            if ((processInsert && eventType == DataEventType.INSERT)
                    || (processUpdate && eventType == DataEventType.UPDATE)
                    || (processDelete && eventType == DataEventType.DELETE)) {
                template = contentTableTemplate;
                if (template != null) {
                    template = fillOutTemplate(table, data, template, context);
                }
            }
            return template;
        } else {
            return null;
        }
    }

    @Override
    protected String addTextFooter(DataContext context) {
        return footerTableTemplate;
    }

    @Override
    protected String addTextHeader(DataContext context) {
        return headerTableTemplate;
    }

    protected String fillOutTemplate(Table table, CsvData data, String template,
            DataContext context) {
        DataEventType eventType = data.getDataEventType();
        String[] colNames = null;
        String[] colValues = null;
        if (eventType == DataEventType.DELETE) {
            colNames = table.getPrimaryKeyColumnNames();
            colValues = data.getParsedData(CsvData.PK_DATA);
        } else {
            colNames = table.getColumnNames();
            colValues = data.getParsedData(CsvData.ROW_DATA);
        }
        for (int i = 0; i < colValues.length; i++) {
            String col = colNames[i];
            template = replace(template, col, format(col, colValues[i]));
        }
        template = template.replace("DMLTYPE", eventType.name());
        template = template.replace("TIMESTAMP", Long.toString(System.currentTimeMillis()));
        return template;
    }

    protected String format(String col, String data) {
        if (columnNameToDataFormatter != null) {
            IFormat formatter = columnNameToDataFormatter.get(col);
            if (formatter != null) {
                try {
                    data = formatter.format(data);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return data;
    }

    protected String replace(String template, String token, String value) {
        if (value == null) {
            value = "";
        }
        if (template != null) {
            template = template.replace("%" + token + "%", value);
        }
        return template;
    }

    public void setColumnNameToDataFormatter(Map<String, IFormat> columnNameToDataFormatter) {
        this.columnNameToDataFormatter = columnNameToDataFormatter;
    }

    public void setProcessDelete(boolean processDeletes) {
        this.processDelete = processDeletes;
    }

    public void setProcessInsert(boolean processInserts) {
        this.processInsert = processInserts;
    }

    public void setProcessUpdate(boolean processUpdates) {
        this.processUpdate = processUpdates;
    }

    public void setHeaderTableTemplate(String headerTableTemplate) {
        this.headerTableTemplate = headerTableTemplate;
    }

    public void setFooterTableTemplate(String footerTableTemplate) {
        this.footerTableTemplate = footerTableTemplate;
    }

    public void setContentTableTemplate(String contentTableTemplate) {
        this.contentTableTemplate = contentTableTemplate;
    }

    public interface IFormat {
        public String format(String data) throws ParseException;
    }

    public void setDataFilter(IDatabaseWriterFilter dataFilter) {
        this.dataFilter = dataFilter;
    }
}