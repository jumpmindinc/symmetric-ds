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

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.jumpmind.db.sql.SqlTemplateSettings.JdbcLobHandling;

public class SymmetricLobHandler {
    protected JdbcLobHandling lobHandling;

    public SymmetricLobHandler() {
        this(JdbcLobHandling.PLAIN);
    }

    public SymmetricLobHandler(JdbcLobHandling lobHandling) {
        super();
        this.lobHandling = lobHandling == null ? JdbcLobHandling.PLAIN : lobHandling;
    }

    public String getClobAsString(ResultSet rs, int columnIndex, int jdbcTypeCode,
            String jdbcTypeName) throws SQLException {
        return rs.getString(columnIndex);
    }

    public byte[] getBlobAsBytes(ResultSet rs, int columnIndex, int jdbcTypeCode, String jdbcTypeName)
            throws SQLException {
        return rs.getBytes(columnIndex);
    }

    public boolean needsAutoCommitFalseForBlob(int jdbcTypeCode, String jdbcTypeName) {
        return false;
    }

    public void setBlobAsBytes(PreparedStatement ps, int i, byte[] bytes) throws SQLException {
        switch (lobHandling) {
            case CREATETEMPORARYLOB:
                Blob blob = ps.getConnection().createBlob();
                blob.setBytes(1, bytes);
                ps.setBlob(i, blob);
                break;
            case STREAMLOB:
                ps.setBlob(i, new ByteArrayInputStream(bytes), bytes.length);
                break;
            case PLAIN:
            default:
                ps.setBytes(i, bytes);
        }
    }

    public void setClobAsString(PreparedStatement ps, int i, String string) throws SQLException {
        switch (lobHandling) {
            case CREATETEMPORARYLOB:
                Clob clob = ps.getConnection().createClob();
                clob.setString(1, string);
                ps.setClob(i, clob);
                break;
            case STREAMLOB:
                ps.setClob(i, new StringReader(string), string.length());
                break;
            case PLAIN:
            default:
                ps.setString(i, string);
        }
    }
}
