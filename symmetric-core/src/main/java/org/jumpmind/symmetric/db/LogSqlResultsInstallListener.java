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
package org.jumpmind.symmetric.db;

import java.util.List;

import org.jumpmind.db.sql.LogSqlResultsListener;
import org.jumpmind.symmetric.ext.IDatabaseInstallStatementListener;

public class LogSqlResultsInstallListener extends LogSqlResultsListener {
    protected String engineName;
    protected int totalStatements;
    protected List<IDatabaseInstallStatementListener> listeners;

    public LogSqlResultsInstallListener(String engineName, int totalStatements, List<IDatabaseInstallStatementListener> listeners) {
        this.engineName = engineName;
        this.totalStatements = totalStatements;
        this.listeners = listeners;
    }

    @Override
    public void sqlApplied(String sql, int rowsUpdated, int rowsRetrieved, int lineNumber) {
        for (IDatabaseInstallStatementListener listener : listeners) {
            listener.sqlApplied(engineName, sql, lineNumber + 1, totalStatements);
        }
    }
}
