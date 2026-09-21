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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ListSqlStatementSourceTest {
    @Test
    void testConstructor_withVarargsPopulatesStatements() {
        ListSqlStatementSource source = new ListSqlStatementSource("sql1", "sql2");
        assertEquals("sql1", source.readSqlStatement());
        assertEquals("sql2", source.readSqlStatement());
    }

    @Test
    void testConstructor_withListPopulatesStatements() {
        List<String> statements = new ArrayList<>();
        statements.add("sql1");
        statements.add("sql2");
        ListSqlStatementSource source = new ListSqlStatementSource(statements);
        assertEquals("sql1", source.readSqlStatement());
        assertEquals("sql2", source.readSqlStatement());
    }

    @Test
    void testConstructor_withListCopiesDefensively() {
        List<String> statements = new ArrayList<>();
        statements.add("sql1");
        ListSqlStatementSource source = new ListSqlStatementSource(statements);
        statements.clear();
        assertEquals("sql1", source.readSqlStatement());
    }

    @Test
    void testReadSqlStatement_removesEachStatementInOrder() {
        ListSqlStatementSource source = new ListSqlStatementSource("sql1", "sql2");
        source.readSqlStatement();
        assertEquals("sql2", source.readSqlStatement());
        assertNull(source.readSqlStatement());
    }

    @Test
    void testReadSqlStatement_returnsNullWhenEmpty() {
        ListSqlStatementSource source = new ListSqlStatementSource();
        assertNull(source.readSqlStatement());
    }
}
