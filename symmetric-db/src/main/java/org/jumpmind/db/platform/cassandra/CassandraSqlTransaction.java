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
package org.jumpmind.db.platform.cassandra;

import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.ISqlTransactionListener;
import org.jumpmind.db.sql.Row;

public class CassandraSqlTransaction implements ISqlTransaction {
    @Override
    public void addSqlTransactionListener(ISqlTransactionListener listener) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean isInBatchMode() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setInBatchMode(boolean batchMode) {
        // TODO Auto-generated method stub
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> clazz, Object... args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Row queryForRow(String sql, Object... args) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int queryForInt(String sql, Object... args) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long queryForLong(String sql, Object... args) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int execute(String sql) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int prepareAndExecute(String sql, Object[] args, int[] types) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int prepareAndExecute(String sql, Object... args) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int prepareAndExecute(String sql, Map<String, Object> args) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public <T> List<T> query(String sql, ISqlRowMapper<T> mapper, Map<String, Object> namedParams) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> List<T> query(String sql, ISqlRowMapper<T> mapper, Object[] args, int[] types) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void commit() {
        // TODO Auto-generated method stub
    }

    @Override
    public void rollback() {
        // TODO Auto-generated method stub
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
    }

    @Override
    public void prepare(String sql) {
        // TODO Auto-generated method stub
    }

    @Override
    public <T> int addRow(T marker, Object[] values, int[] types) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int flush() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public <T> List<T> getUnflushedMarkers(boolean clear) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void allowInsertIntoAutoIncrementColumns(boolean value, Table table, String quote, String catalogSeparator,
            String schemaSeparator) {
        // TODO Auto-generated method stub
    }

    @Override
    public long insertWithGeneratedKey(String sql, String column, String sequenceName, Object[] args, int[] types) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isAllowInsertIntoAutoIncrement() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void clearBatch() {
        // TODO Auto-generated method stub
    }
}
