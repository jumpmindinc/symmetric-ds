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
package org.jumpmind.vaadin.ui.common;

import java.io.ByteArrayInputStream;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;

public class CsvExport<T> {
    protected IDataProvider<T> gridData = null;
    protected String fileName;
    protected String title;
    protected StringBuilder cellData;
    protected final String csvMimeContentType = "text/csv";
    final Logger log = LoggerFactory.getLogger(getClass());

    public CsvExport(final IDataProvider<T> gridData) {
        this(gridData, null, null);
    }

    public CsvExport(final IDataProvider<T> gridData, String fileName) {
        this(gridData, fileName, null);
    }

    public CsvExport(final IDataProvider<T> gridData, String fileName, String title) {
        this.gridData = gridData;
        init(fileName, title);
    }

    public void init(String fileName, String title) {
        if (fileName == null || fileName.isEmpty() || !fileName.endsWith(".csv")) {
            this.fileName = "GridExport.csv";
        } else {
            this.fileName = fileName;
        }
        if (title == null || title.isEmpty()) {
            this.title = "";
        } else {
            this.title = title;
        }
        this.cellData = new StringBuilder();
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DownloadHandler getDownloadHandler() {
        convertToCsv();
        return DownloadHandler.fromInputStream(event -> {
            byte[] data = cellData.toString().getBytes();
            return new DownloadResponse(new ByteArrayInputStream(data), fileName, null, data.length);
        });
    }

    public void convertToCsv() {
        addTitle();
        addHeaders();
        Iterator<T> iterator = gridData.getRowItems().iterator();
        while (iterator.hasNext()) {
            T rowItem = iterator.next();
            Iterator<?> columnIterator = gridData.getColumns().iterator();
            while (columnIterator.hasNext()) {
                Object col = columnIterator.next();
                Object obj = gridData.getCellValue(rowItem, col);
                String value = "";
                if (obj != null) {
                    value = "\"" + obj.toString().replace("\"", "\"\"") + "\"";
                }
                if (columnIterator.hasNext()) {
                    cellData.append(value + ",");
                } else {
                    cellData.append(value + "\n");
                }
            }
        }
    }

    public void addTitle() {
        if (this.title != null && !this.title.equals("")) {
            cellData.append(title + ",\n");
        }
    }

    public void addHeaders() {
        Iterator<?> iterator = gridData.getColumns().iterator();
        while (iterator.hasNext()) {
            Object col = iterator.next();
            String value = gridData.getKeyValue(col);
            if (iterator.hasNext()) {
                cellData.append(value + ",");
            } else {
                cellData.append(value + "\n");
            }
        }
    }
}