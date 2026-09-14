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
package org.jumpmind.symmetric.db.hsqldb;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Vector;

import org.hsqldb.DatabaseManager;
import org.hsqldb.Trigger;
import org.jumpmind.db.sql.SqlUtils;
import org.jumpmind.symmetric.db.AbstractEmbeddedTrigger;

public class HsqlDbTrigger extends AbstractEmbeddedTrigger implements Trigger {
    protected String triggerName;

    public void fire(int type, String triggerName, String tableName, Object[] oldRow,
            Object[] newRow) {
        Connection conn = findConnection(triggerName);
        if (conn != null) {
            try {
                init(conn, triggerName, null, tableName);
                fire(conn, oldRow, newRow);
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                try {
                    conn.close();
                } catch (SQLException ex) {
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected Connection findConnection(String triggerName) {
        Connection c = null;
        Vector<String> uris = DatabaseManager.getDatabaseURIs();
        for (String uri : uris) {
            Connection testCon = null;
            try {
                testCon = DriverManager.getConnection("jdbc:hsqldb:" + uri, new Properties());
                if (uris.size() > 1) {
                    Statement stmt = testCon.createStatement();
                    ResultSet rs = stmt.executeQuery(String.format("select count(*) from %s%s",
                            SqlUtils.sanitizeIdentifier(triggerName), TEMPLATE_TABLE_SUFFIX));
                    rs.close();
                    stmt.close();
                }
                c = testCon;
                break;
            } catch (SQLException e) {
                try {
                    if (testCon != null) {
                        testCon.close();
                    }
                } catch (SQLException e1) {
                }
            }
        }
        return c;
    }

    @Override
    protected Object appendVirtualTableStringValue(Object value, StringBuilder out) {
        if (value != null && (value.getClass().getName().equals("org.hsqldb.types.Binary")
                || value.getClass().getName().equals("org.hsqldb.types.BinaryData"))) {
            out.append("'");
            Method getBytes;
            try {
                getBytes = value.getClass().getMethod("getBytes");
                value = HsqlDbFunctions.encodeBase64((byte[]) getBytes.invoke(value));
                out.append(escapeString(value));
                out.append("'");
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            return super.appendVirtualTableStringValue(value, out);
        }
        return value;
    }
}