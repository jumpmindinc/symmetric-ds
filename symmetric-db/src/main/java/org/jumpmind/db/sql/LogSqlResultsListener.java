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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogSqlResultsListener implements ISqlResultsListener {
    private static final Logger log = LoggerFactory.getLogger(LogSqlResultsListener.class);

    public LogSqlResultsListener() {
    }

    public void sqlBefore(String sql, int lineNumber) {
        log.info("Executing DDL: {}", sql);
    }

    public void sqlErrored(String sql, SqlException ex, int lineNumber, boolean dropStatement,
            boolean sequenceCreate) {
        if (dropStatement || sequenceCreate) {
            log.info("DDL failed: {}", sql);
        } else {
            log.warn("DDL failed: {}", sql);
        }
    }

    public void sqlApplied(String sql, int rowsUpdated, int rowsRetrieved, int lineNumber) {
    }
}
