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
package org.jumpmind.symmetric.io.data.writer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.DmlStatement.DmlType;
import org.jumpmind.symmetric.io.data.CsvData;

/**
 * Writer for ClickHouse, which always reports an update count of 0 for UPDATE and DELETE even though the change is applied. The default writer would treat
 * every update and delete as a conflict, so this writer assumes they succeeded. Because of that, a missing row is never detected and conflict detection does
 * not work on this target. Insert counts are accurate.
 * <p>
 * Rows are looked up by primary key only. Conflict detection would add the old values of columns to the WHERE clause, and since a missing match is never
 * reported, an update whose old values differ from the target would be lost without a trace.
 * <p>
 * Dates and times are sent as text so they are stored exactly as the source has them, with no time zone conversion.
 * <p>
 * ClickHouse cannot update a column of the sorting key (error 420), so an update that changes the primary key is rejected with a clear error instead of being
 * sent to the server. Supporting it is a larger change and is not implemented.
 * <p>
 * It also turns the reload "delete from table" into a TRUNCATE, because ClickHouse requires a WHERE clause on DELETE.
 */
public class ClickHouseDatabaseWriter extends DynamicDefaultDatabaseWriter {
    private static final String IDENTIFIER = "(?:\"[^\"]+\"|`[^`]+`|[\\w$]+)";
    private static final Pattern DELETE_ALL_PATTERN = Pattern.compile("^\\s*delete\\s+from\\s+(" + IDENTIFIER + "(?:\\." + IDENTIFIER + ")?)\\s*;?\\s*$",
            Pattern.CASE_INSENSITIVE);
    /** Names, in lower case, of the columns in the ORDER BY of the target table. */
    private Set<String> sortingKeyColumns = new HashSet<String>();

    public ClickHouseDatabaseWriter(IDatabasePlatform symmetricPlatform, IDatabasePlatform targetPlatform, String prefix,
            IDatabaseWriterConflictResolver conflictResolver, DatabaseWriterSettings settings) {
        super(symmetricPlatform, targetPlatform, prefix, conflictResolver, settings);
        // The driver converts a Timestamp using the JVM time zone and the server stores it as UTC, which shifts values.
        // Sending dates and times as text lets ClickHouse store them as they are.
        if (settings != null) {
            settings.setTreatDateTimeFieldsAsVarchar(true);
        }
    }

    @Override
    protected Table lookupTableAtTarget(Table sourceTable) {
        Table table = super.lookupTableAtTarget(sourceTable);
        // The loader treats every column as a key when a table has none, but only a real sorting key restricts updates
        sortingKeyColumns = new HashSet<String>();
        if (table != null && !isSymmetricTable(table)) {
            Table existing = (Table) getPlatform(sourceTable).getRelationFromCache(sourceTable.getCatalog(), sourceTable.getSchema(), sourceTable.getName(),
                    false);
            if (existing != null) {
                for (String name : existing.getPrimaryKeyColumnNames()) {
                    sortingKeyColumns.add(name.toLowerCase());
                }
            }
        }
        return table;
    }

    @Override
    protected LoadStatus update(CsvData data, boolean applyChangesOnly, boolean useConflictDetection) {
        if (!isSymmetricTable(targetTable) && !sortingKeyColumns.isEmpty()) {
            String[] oldKey = getOldKey(data);
            List<String> changed = changedSortingKeyColumns(sourceTable.getPrimaryKeyColumnNames(), oldKey, getRowData(data, CsvData.ROW_DATA),
                    sourceTable.getColumnNames(), sortingKeyColumns);
            if (!changed.isEmpty()) {
                throw new UnsupportedOperationException("ClickHouse does not support updating the primary key. The update to table " + targetTable.getName()
                        + " changes the key column(s) " + String.join(", ", changed));
            }
        }
        // The loader cannot tell whether a row matched, so a lookup on old values could fail without anyone noticing. Rows are found by primary key only.
        return super.update(data, applyChangesOnly, false);
    }

    @Override
    protected LoadStatus delete(CsvData data, boolean useConflictDetection) {
        return super.delete(data, false);
    }

    /**
     * The key of the row before the update, in the order of the source primary key, or null when it cannot be known.
     */
    private String[] getOldKey(CsvData data) {
        String[] keyNames = sourceTable.getPrimaryKeyColumnNames();
        String[] pkData = data.getParsedData(CsvData.PK_DATA);
        if (pkData != null && pkData.length == keyNames.length) {
            return pkData;
        }
        String[] oldData = data.getParsedData(CsvData.OLD_DATA);
        String[] columnNames = sourceTable.getColumnNames();
        if (oldData != null && oldData.length == columnNames.length) {
            String[] oldKey = new String[keyNames.length];
            for (int i = 0; i < keyNames.length; i++) {
                int index = ArrayUtils.indexOf(columnNames, keyNames[i]);
                if (index < 0) {
                    return null;
                }
                oldKey[i] = oldData[index];
            }
            return oldKey;
        }
        return null;
    }

    static boolean sortingKeyChanged(String[] keyNames, String[] oldKey, String[] newRow, String[] columnNames, Set<String> sortingKey) {
        return !changedSortingKeyColumns(keyNames, oldKey, newRow, columnNames, sortingKey).isEmpty();
    }

    /**
     * The names of the primary key columns that are part of the sorting key and have a different value in the new row than in the old key.
     */
    static List<String> changedSortingKeyColumns(String[] keyNames, String[] oldKey, String[] newRow, String[] columnNames, Set<String> sortingKey) {
        List<String> changed = new ArrayList<String>();
        if (keyNames == null || oldKey == null || newRow == null || keyNames.length != oldKey.length) {
            return changed;
        }
        for (int i = 0; i < keyNames.length; i++) {
            if (sortingKey.contains(keyNames[i].toLowerCase())) {
                int index = ArrayUtils.indexOf(columnNames, keyNames[i]);
                if (index >= 0 && index < newRow.length && !Objects.equals(oldKey[i], newRow[index])) {
                    changed.add(keyNames[i]);
                }
            }
        }
        return changed;
    }

    @Override
    protected int execute(CsvData data, String[] values) {
        int count = super.execute(data, values);
        if (isSymmetricTable(targetTable) || currentDmlStatement == null) {
            return count;
        }
        return reportedCount(currentDmlStatement.getDmlType(), count);
    }

    /**
     * ClickHouse returns 0 for every UPDATE and DELETE, so they are reported as one row. Inserts keep the count the driver returned.
     */
    static int reportedCount(DmlType type, int count) {
        return type == DmlType.UPDATE || type == DmlType.DELETE ? 1 : count;
    }

    @Override
    protected String preprocessSqlStatement(String sql) {
        sql = super.preprocessSqlStatement(sql);
        return isSymmetricTable(targetTable) ? sql : toTruncate(sql);
    }

    static String toTruncate(String sql) {
        Matcher matcher = DELETE_ALL_PATTERN.matcher(sql);
        return matcher.matches() ? "truncate table " + matcher.group(1) : sql;
    }
}
