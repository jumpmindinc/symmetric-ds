/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.io.Reader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.jumpmind.db.sql.SqlTemplateSettings.JdbcLobHandling;
import org.junit.jupiter.api.Test;

class SymmetricLobHandlerTest {
    private static final byte[] BYTES = "hello".getBytes();
    private static final String STRING = "hello";

    @Test
    void testSetBlobAsBytesPlainUsesSetBytes() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        new SymmetricLobHandler(JdbcLobHandling.PLAIN).setBlobAsBytes(ps, 1, BYTES);
        verify(ps).setBytes(1, BYTES);
        verify(ps, never()).setBlob(anyInt(), any(Blob.class));
        verify(ps, never()).setBlob(anyInt(), any(InputStream.class), anyLong());
    }

    @Test
    void testSetBlobAsBytesCreateTemporaryLobCopiesIntoConnectionBlob() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection con = mock(Connection.class);
        Blob blob = mock(Blob.class);
        when(ps.getConnection()).thenReturn(con);
        when(con.createBlob()).thenReturn(blob);
        new SymmetricLobHandler(JdbcLobHandling.CREATETEMPORARYLOB).setBlobAsBytes(ps, 1, BYTES);
        verify(blob).setBytes(1, BYTES);
        verify(ps).setBlob(1, blob);
        verify(ps, never()).setBytes(anyInt(), any(byte[].class));
    }

    @Test
    void testSetBlobAsBytesStreamLobUsesStreamingSetBlob() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        new SymmetricLobHandler(JdbcLobHandling.STREAMLOB).setBlobAsBytes(ps, 1, BYTES);
        verify(ps).setBlob(eq(1), any(InputStream.class), eq((long) BYTES.length));
        verify(ps, never()).setBytes(anyInt(), any(byte[].class));
        verify(ps, never()).setBlob(anyInt(), any(Blob.class));
    }

    @Test
    void testSetClobAsStringPlainUsesSetString() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        new SymmetricLobHandler(JdbcLobHandling.PLAIN).setClobAsString(ps, 1, STRING);
        verify(ps).setString(1, STRING);
        verify(ps, never()).setClob(anyInt(), any(Clob.class));
        verify(ps, never()).setClob(anyInt(), any(Reader.class), anyLong());
    }

    @Test
    void testSetClobAsStringCreateTemporaryLobCopiesIntoConnectionClob() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        Connection con = mock(Connection.class);
        Clob clob = mock(Clob.class);
        when(ps.getConnection()).thenReturn(con);
        when(con.createClob()).thenReturn(clob);
        new SymmetricLobHandler(JdbcLobHandling.CREATETEMPORARYLOB).setClobAsString(ps, 1, STRING);
        verify(clob).setString(1, STRING);
        verify(ps).setClob(1, clob);
        verify(ps, never()).setString(anyInt(), any(String.class));
    }

    @Test
    void testSetClobAsStringStreamLobUsesStreamingSetClob() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        new SymmetricLobHandler(JdbcLobHandling.STREAMLOB).setClobAsString(ps, 1, STRING);
        verify(ps).setClob(eq(1), any(Reader.class), eq((long) STRING.length()));
        verify(ps, never()).setString(anyInt(), any(String.class));
        verify(ps, never()).setClob(anyInt(), any(Clob.class));
    }

    @Test
    void testReadPathIsModeIndependent() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBytes(1)).thenReturn(BYTES);
        when(rs.getString(1)).thenReturn(STRING);
        for (JdbcLobHandling mode : JdbcLobHandling.values()) {
            SymmetricLobHandler handler = new SymmetricLobHandler(mode);
            assertArrayEquals(BYTES, handler.getBlobAsBytes(rs, 1, 0, null));
            assertEquals(STRING, handler.getClobAsString(rs, 1, 0, null));
        }
        verify(rs, times(JdbcLobHandling.values().length)).getBytes(1);
        verify(rs, times(JdbcLobHandling.values().length)).getString(1);
    }
}
