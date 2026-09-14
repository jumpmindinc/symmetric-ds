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
package org.jumpmind.vaadin.ui.sqlexplorer;

import java.io.Serializable;
import java.util.Date;

public class SqlHistory implements Serializable, Comparable<SqlHistory> {
    private static final long serialVersionUID = 1L;
    private String sqlStatement;
    private Date lastExecuteTime;
    private long lastExecuteDuration;
    private String lastExecuteUserId;
    private long executeCount;

    public String getSqlStatement() {
        return sqlStatement;
    }

    public void setSqlStatement(String sqlStatement) {
        this.sqlStatement = sqlStatement;
    }

    public Date getLastExecuteTime() {
        return lastExecuteTime;
    }

    public void setLastExecuteTime(Date lastExecuteTime) {
        this.lastExecuteTime = lastExecuteTime;
    }

    public long getLastExecuteDuration() {
        return lastExecuteDuration;
    }

    public void setLastExecuteDuration(long lastExecuteDuration) {
        this.lastExecuteDuration = lastExecuteDuration;
    }

    public String getLastExecuteUserId() {
        return lastExecuteUserId;
    }

    public void setLastExecuteUserId(String lastExecuteUserId) {
        this.lastExecuteUserId = lastExecuteUserId;
    }

    public long getExecuteCount() {
        return executeCount;
    }

    public void setExecuteCount(long executeCount) {
        this.executeCount = executeCount;
    }

    @Override
    public int compareTo(SqlHistory o) {
        return -lastExecuteTime.compareTo(o.lastExecuteTime);
    }
}
