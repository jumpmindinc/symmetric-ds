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

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;

public class DatabaseWriterFilterAdapter implements IDatabaseWriterFilter {
    public boolean beforeWrite(DataContext context, Table table, CsvData data) {
        return true;
    }

    public void afterWrite(DataContext context, Table table, CsvData data) {
    }

    public boolean handlesMissingTable(DataContext context, Table table) {
        return false;
    }

    public void earlyCommit(DataContext context) {
    }

    public void batchComplete(DataContext context) {
    }

    public void batchCommitted(DataContext context) {
    }

    public void batchRolledback(DataContext context) {
    }
}
