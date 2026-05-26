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

import java.nio.charset.Charset;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.Row;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.Batch.BatchType;

/**
 * Convert a source relation's rows to {@link CsvData}
 */
public class RelationExtractDataReaderSource implements IExtractDataReaderSource {
    protected IDatabasePlatform platform;
    protected String whereClause;
    protected Batch batch;
    protected Relation relation;
    protected ISqlReadCursor<CsvData> cursor;
    protected boolean streamLobs;

    public RelationExtractDataReaderSource(IDatabasePlatform platform, String catalogName,
            String schemaName, String tableName, String whereClause, boolean streamLobs, String sourceNodeId, String targetNodeId) {
        this.platform = platform;
        this.relation = platform.getRelationFromCache(catalogName, schemaName, tableName, true);
        if (relation == null) {
            throw new IllegalStateException(String.format("Could not find table %s",
                    Relation.getFullyQualifiedName(catalogName, schemaName, tableName)));
        }
        this.whereClause = whereClause;
        this.streamLobs = streamLobs;
        this.batch = new Batch(BatchType.EXTRACT, -1, "default", BinaryEncoding.BASE64, sourceNodeId, targetNodeId, false);
    }

    public Batch getBatch() {
        return this.batch;
    }

    public Relation getTargetRelation() {
        return this.relation;
    }

    public Relation getSourceRelation() {
        return this.relation;
    }

    public CsvData next() {
        CsvData data = null;
        if (cursor == null) {
            startNewCursor();
        }
        if (cursor != null) {
            data = cursor.next();
            if (data == null) {
                closeCursor();
            }
        }
        return data;
    }

    protected void startNewCursor() {
        DatabaseInfo dbInfo = platform.getDatabaseInfo();
        String sql = String.format("select * from %s %s", relation.getQualifiedName(dbInfo.getDelimiterToken(),
                dbInfo.getCatalogSeparator(), dbInfo.getSchemaSeparator()),
                StringUtils.isNotBlank(whereClause) ? " where " + whereClause : "");
        this.cursor = platform.getSqlTemplate().queryForCursor(sql, new ISqlRowMapper<CsvData>() {
            public CsvData mapRow(Row row) {
                return new CsvData(DataEventType.INSERT, toStringData(row, relation.getPrimaryKeyColumns()), toStringData(row, relation.getColumns()));
            }
        });
    }

    protected String[] toStringData(Row row, Column[] columns) {
        String[] stringValues = new String[columns.length];
        for (int i = 0; i < columns.length; i++) {
            Object value = row.get(columns[i].getName());
            if (value instanceof byte[]) {
                stringValues[i] = new String(Base64.encodeBase64((byte[]) value), Charset.defaultCharset());
            } else if (value != null) {
                stringValues[i] = value.toString();
            }
        }
        return stringValues;
    }

    public boolean requiresLobsSelectedFromSource(CsvData data) {
        return streamLobs;
    }

    public void close() {
        closeCursor();
    }

    protected void closeCursor() {
        if (this.cursor != null) {
            this.cursor.close();
            this.cursor = null;
        }
    }
}
