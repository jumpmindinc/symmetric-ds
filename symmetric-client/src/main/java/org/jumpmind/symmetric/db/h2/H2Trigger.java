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
package org.jumpmind.symmetric.db.h2;

import java.sql.Connection;
import java.sql.SQLException;

import org.jumpmind.symmetric.db.AbstractEmbeddedTrigger;

public class H2Trigger extends AbstractEmbeddedTrigger implements org.h2.api.Trigger {
    static String startupTime = Long.toString(System.currentTimeMillis());
    public static final String TRANSACTION_FUNCTION = "TRANSACTION_ID()";

    /**
     * This method is called by the database engine once when initializing the trigger.
     * 
     * @param conn
     *            a connection to the database
     * @param schemaName
     *            the name of the schema
     * @param triggerName
     *            the name of the trigger used in the CREATE TRIGGER statement
     * @param tableName
     *            the name of the table
     * @param before
     *            whether the fire method is called before or after the operation is performed
     * @param type
     *            the operation type: INSERT, UPDATE, or DELETE
     */
    public void init(Connection conn, String schemaName, String triggerName, String tableName, boolean before, int type)
            throws SQLException {
        this.init(conn, triggerName, schemaName, tableName);
    }

    public void close() throws SQLException {
    }

    public void remove() throws SQLException {
    }

    @Override
    protected String fillVirtualTableSql(String sql, Object[] oldRow, Object[] newRow)
            throws SQLException {
        sql = sql.replace(TRANSACTION_FUNCTION, TRANSACTION_FUNCTION + " || '-" + startupTime + "'");
        return super.fillVirtualTableSql(sql, oldRow, newRow);
    }
}