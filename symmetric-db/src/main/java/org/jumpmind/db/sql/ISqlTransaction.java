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
package org.jumpmind.db.sql;

import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Table;

public interface ISqlTransaction {
    public void addSqlTransactionListener(ISqlTransactionListener listener);

    public boolean isInBatchMode();

    public void setInBatchMode(boolean batchMode);

    public <T> T queryForObject(String sql, Class<T> clazz, Object... args);

    public Row queryForRow(String sql, Object... args);

    public int queryForInt(String sql, Object... args);

    public long queryForLong(String sql, Object... args);

    public int execute(String sql);

    public int prepareAndExecute(String sql, Object[] args, int[] types);

    public int prepareAndExecute(String sql, Object... args);

    public int prepareAndExecute(String sql, Map<String, Object> args);

    public <T> List<T> query(String sql, ISqlRowMapper<T> mapper, Map<String, Object> namedParams);

    public <T> List<T> query(String sql, ISqlRowMapper<T> mapper, Object[] args, int[] types);

    public void commit();

    public void rollback();

    public void close();

    /**
     * Each time the SQL changes it needs to be submitted for preparation
     */
    public void prepare(String sql);

    public <T> int addRow(T marker, Object[] values, int[] types);

    public int flush();

    public <T> List<T> getUnflushedMarkers(boolean clear);

    /**
     * Indicate that the current session is to allow updates to columns that have been marked as auto increment. This is specific to SQL Server.
     */
    public void allowInsertIntoAutoIncrementColumns(boolean value, Table table, String quote, String catalogSeparator, String schemaSeparator);

    public boolean isAllowInsertIntoAutoIncrement();

    public long insertWithGeneratedKey(String sql, String column, String sequenceName,
            Object[] args, int[] types);

    public void clearBatch();
}
