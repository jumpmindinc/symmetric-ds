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
package org.jumpmind.symmetric.io.data.reader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.io.data.CsvUtils;

/**
 * Read CSV formatted data for a single table. Requires that the column names be the header of the CSV.
 */
public class CsvDquoteDataReader extends CsvTableDataReader {
    public CsvDquoteDataReader(BinaryEncoding binaryEncoding, String catalogName, String schemaName,
            String tableName, InputStream is) {
        super(binaryEncoding, catalogName, schemaName, tableName, is);
    }

    public CsvDquoteDataReader(BinaryEncoding binaryEncoding, String catalogName, String schemaName,
            String tableName, Reader reader) {
        super(binaryEncoding, catalogName, schemaName, tableName, reader);
    }

    @Override
    protected void init() {
        try {
            this.csvReader = CsvUtils.getCsvReaderDquote(reader);
            this.csvReader.setUseComments(true);
            this.csvReader.readHeaders();
            String[] columnNames = this.csvReader.getHeaders();
            for (String columnName : columnNames) {
                relation.addColumn(new Column(columnName));
            }
        } catch (IOException e) {
            throw new IoException(e);
        }
    }
}
