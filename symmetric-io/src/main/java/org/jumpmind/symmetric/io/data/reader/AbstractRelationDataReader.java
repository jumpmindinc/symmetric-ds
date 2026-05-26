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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.IDataReader;
import org.jumpmind.util.Statistics;

/**
 * A data reader that knows it will be reading a single batch and a single relation.
 */
abstract public class AbstractRelationDataReader extends AbstractDataReader implements IDataReader {
    public static final String CTX_LINE_NUMBER = AbstractRelationDataReader.class.getSimpleName()
            + ".lineNumber";
    protected Reader reader;
    protected Statistics statistics = new Statistics();
    protected DataContext context;
    protected Batch batch;
    protected Relation relation;
    protected int lineNumber = 0;
    protected boolean readDataBeforeRelation = false;
    protected boolean readingBatch = false;
    protected boolean readingRelation = false;

    public AbstractRelationDataReader(Batch batch, String catalogName, String schemaName,
            String relationName, StringBuilder input) {
        this(batch, catalogName, schemaName, relationName, new BufferedReader(new StringReader(
                input.toString())));
    }

    public AbstractRelationDataReader(Batch batch, String catalogName, String schemaName,
            String relationName, InputStream is) {
        this(batch, catalogName, schemaName, relationName, toReader(is));
    }

    public AbstractRelationDataReader(Batch batch, String catalogName, String schemaName,
            String relationName, String input) {
        this(batch, catalogName, schemaName, relationName, new BufferedReader(new StringReader(input)));
    }

    public AbstractRelationDataReader(Batch batch, String catalogName, String schemaName,
            String relationName, File file) {
        this(batch, catalogName, schemaName, toRelationName(relationName, file), toReader(file));
    }

    public AbstractRelationDataReader(Batch batch, String catalogName, String schemaName,
            String relationName, Reader reader) {
        this.reader = reader;
        this.batch = batch;
        if (StringUtils.isNotBlank(relationName)) {
            this.relation = new Table(catalogName, schemaName, relationName);
        }
    }

    public AbstractRelationDataReader(BinaryEncoding binaryEncoding, String catalogName,
            String schemaName, String relationName, Reader reader) {
        this(toBatch(binaryEncoding), catalogName, schemaName, relationName, reader);
    }

    public AbstractRelationDataReader(BinaryEncoding binaryEncoding, String catalogName,
            String schemaName, String relationName, InputStream is) {
        this(toBatch(binaryEncoding), catalogName, schemaName, relationName, is);
    }

    protected static String toRelationName(String relationName, File file) {
        if (StringUtils.isBlank(relationName)) {
            relationName = file.getName();
            if (relationName.lastIndexOf(".") > 0) {
                relationName = relationName.substring(0, relationName.lastIndexOf("."));
            }
        }
        return relationName;
    }

    public void open(DataContext context) {
        this.lineNumber = 0;
        this.context = context;
        this.init();
    }

    abstract protected void init();

    abstract protected CsvData readNext();

    abstract protected void finish();

    protected CsvData buildCsvData(String[] tokens, DataEventType dml) {
        statistics.increment(DataReaderStatistics.READ_BYTE_COUNT, logDebugAndCountBytes(tokens));
        return new CsvData(dml, tokens);
    }

    public CsvData nextData() {
        if (readDataBeforeRelation || readingRelation) {
            CsvData data = readNext();
            if (data != null) {
                lineNumber++;
                context.put(CTX_LINE_NUMBER, lineNumber);
                return data;
            } else {
                batch.setComplete(true);
            }
        }
        return null;
    }

    public Batch nextBatch() {
        if (!readingBatch) {
            readingBatch = true;
            return batch;
        } else {
            return null;
        }
    }

    public Relation nextRelation() {
        if (!readingRelation) {
            readingRelation = true;
            return relation;
        } else {
            return null;
        }
    }

    public void close() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
        }
        finish();
    }

    public Map<Batch, Statistics> getStatistics() {
        Map<Batch, Statistics> map = HashMap.newHashMap(1);
        map.put(batch, statistics);
        return map;
    }
}
