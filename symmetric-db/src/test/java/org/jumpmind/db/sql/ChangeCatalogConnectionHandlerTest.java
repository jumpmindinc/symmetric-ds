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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class ChangeCatalogConnectionHandlerTest {
    @Test
    void testBefore_withCatalogChangesConnection() throws SQLException {
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.getCatalog()).thenReturn("cat1");
        ChangeCatalogConnectionHandler handler = new ChangeCatalogConnectionHandler("cat2");
        handler.before(connectionMock);
        verify(connectionMock).setCatalog("cat2");
    }

    @Test
    void testBefore_withNullCatalogDoesNothing() throws SQLException {
        Connection connectionMock = mock(Connection.class);
        ChangeCatalogConnectionHandler handler = new ChangeCatalogConnectionHandler(null);
        handler.before(connectionMock);
        verify(connectionMock, never()).getCatalog();
        verify(connectionMock, never()).setCatalog(ArgumentMatchers.anyString());
    }

    @Test
    void testBefore_whenSetCatalogThrows_restoresPreviousAndThrowsSqlException() throws SQLException {
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.getCatalog()).thenReturn("cat1");
        doThrow(new SQLException("boom")).when(connectionMock).setCatalog("cat2");
        ChangeCatalogConnectionHandler handler = new ChangeCatalogConnectionHandler("cat2");
        assertThrows(SqlException.class, () -> handler.before(connectionMock));
        verify(connectionMock).setCatalog("cat1");
    }

    @Test
    void testAfter_restoresPreviousCatalog() throws SQLException {
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.getCatalog()).thenReturn("cat1");
        ChangeCatalogConnectionHandler handler = new ChangeCatalogConnectionHandler("cat2");
        handler.before(connectionMock);
        handler.after(connectionMock);
        verify(connectionMock).setCatalog("cat1");
    }

    @Test
    void testAfter_withNoPreviousCatalogDoesNothing() throws SQLException {
        Connection connectionMock = mock(Connection.class);
        ChangeCatalogConnectionHandler handler = new ChangeCatalogConnectionHandler("cat2");
        handler.after(connectionMock);
        verify(connectionMock, never()).setCatalog(ArgumentMatchers.anyString());
    }

    @Test
    void testAfter_whenSetCatalogThrows_swallowsException() throws SQLException {
        Connection connectionMock = mock(Connection.class);
        when(connectionMock.getCatalog()).thenReturn("cat1");
        doThrow(new SQLException("boom")).when(connectionMock).setCatalog("cat1");
        ChangeCatalogConnectionHandler handler = new ChangeCatalogConnectionHandler("cat2");
        handler.before(connectionMock);
        assertDoesNotThrow(() -> handler.after(connectionMock));
    }
}
