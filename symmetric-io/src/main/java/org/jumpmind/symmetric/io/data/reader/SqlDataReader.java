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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.jumpmind.db.sql.SqlScriptReader;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;

/**
 * Reads a SQL script and passes each SQL statement through the reader as a {@link CsvData} event.
 */
public class SqlDataReader extends AbstractRelationDataReader {
    protected SqlScriptReader sqlScriptReader;

    public SqlDataReader(InputStream is) {
        super(BinaryEncoding.HEX, null, null, null, is);
    }

    public SqlDataReader(Reader reader) {
        super(BinaryEncoding.HEX, null, null, null, reader);
    }

    @Override
    protected void init() {
        /*
         * Tables are really relevant as we aren't going to parse each SQL statement.
         */
        this.readDataBeforeRelation = true;
        this.sqlScriptReader = new SqlScriptReader(reader);
    }

    @Override
    protected CsvData readNext() {
        String sql = sqlScriptReader.readSqlStatement();
        if (sql != null) {
            return new CsvData(DataEventType.SQL, new String[] { sql });
        } else {
            return null;
        }
    }

    @Override
    protected void finish() {
        try {
            this.sqlScriptReader.close();
        } catch (IOException e) {
        }
    }
}
