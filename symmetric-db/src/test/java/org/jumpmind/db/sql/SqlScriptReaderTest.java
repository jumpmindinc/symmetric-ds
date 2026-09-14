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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.InputStreamReader;

import org.junit.jupiter.api.Test;

public class SqlScriptReaderTest {
    @Test
    public void testReadScript2() throws Exception {
        SqlScriptReader reader = new SqlScriptReader(new InputStreamReader(getClass().getResourceAsStream("/test-script-2.sql")));
        int count = 0;
        while (reader.readSqlStatement() != null) {
            count++;
        }
        assertEquals(36, count);
    }

    @Test
    public void testReadScriptAndRemoveComments() throws Exception {
        SqlScriptReader reader = new SqlScriptReader(new InputStreamReader(getClass().getResourceAsStream("/test-script-1.sql")));
        reader.setStripOutComments(true);
        String nextStatement = reader.readSqlStatement();
        assertEquals("select * from \n  TEST where\n  \n  id = 'someid'", nextStatement);
        assertEquals("select * from test", reader.readSqlStatement());
        assertEquals("insert into test (one, two, three) values('1','1','2')", reader.readSqlStatement());
        for (int i = 0; i < 4; i++) {
            assertEquals("delete from test where one='1'", reader.readSqlStatement());
        }
        nextStatement = reader.readSqlStatement();
        assertEquals("update sym_node set sync_url='http://localhost:8080/test' where node_id='test'", nextStatement);
        nextStatement = reader.readSqlStatement();
        assertEquals("update something set oops=';' where whoops='test'", nextStatement);
        nextStatement = reader.readSqlStatement();
        assertEquals("update test set one = '''', two='\\\\##--''' where one is null", nextStatement);
        assertEquals("update test\n  set one = '1', two = '2'\n  where one = 'one'", reader.readSqlStatement());
        assertEquals("create table \"TE--ST\" (\"ID##2\" VARCHAR(100))", reader.readSqlStatement());
        nextStatement = reader.readSqlStatement();
        assertEquals("insert into test (col) values('import org.x.Test;\n" +
                "import com.y.Testy;\n" +
                "\n" +
                "class A {\n" +
                "  int x, y = 0;\n" +
                "}\n" +
                "')", nextStatement);
        assertEquals("insert into test (col) values('<!--\n" +
                "<test></test>\n" +
                "-->')", reader.readSqlStatement());
        assertEquals("insert into test (col1,col2) values('<!--', \n" +
                "'<test></test>')", reader.readSqlStatement());
        assertEquals("select col1 , col2  from rubixcube", reader.readSqlStatement());
        reader.setStripOutComments(true);
        nextStatement = reader.readSqlStatement();
        assertEquals("select col1 , col2  from rubixcube", nextStatement);
        nextStatement = reader.readSqlStatement();
        assertEquals("insert into test (col1, col2) ('test', '\n" +
                "''test'';\n" +
                "')", nextStatement);
        assertNull(reader.readSqlStatement());
        reader.close();
    }

    @Test
    public void testReadScriptAndPreserveComments() throws Exception {
        SqlScriptReader reader = new SqlScriptReader(new InputStreamReader(getClass().getResourceAsStream("/test-script-1.sql")));
        reader.setStripOutComments(false);
        String nextStatement = reader.readSqlStatement();
        assertEquals(972, nextStatement.indexOf("select * from"));
        assertEquals("select * from test", reader.readSqlStatement());
        assertEquals("insert into test (one, two, three) values('1','1','2')", reader.readSqlStatement());
        assertEquals(20, reader.readSqlStatement().indexOf("delete from test where one='1'"));
        for (int i = 0; i < 3; i++) {
            assertEquals(0, reader.readSqlStatement().indexOf("delete from test where one='1'"));
        }
        nextStatement = reader.readSqlStatement();
        assertEquals("update sym_node set sync_url='http://localhost:8080/test' where node_id='test'", nextStatement);
        nextStatement = reader.readSqlStatement();
        assertEquals("update something set oops=';' where whoops='test'", nextStatement);
        nextStatement = reader.readSqlStatement();
        assertEquals("update test set one = '''', two='\\\\##--''' where one is null", nextStatement);
        assertEquals("update test\n  set one = '1', two = '2'\n  where one = 'one'", reader.readSqlStatement());
        assertEquals(16, reader.readSqlStatement().indexOf("create table \"TE--ST\" (\"ID##2\" VARCHAR(100))"));
        nextStatement = reader.readSqlStatement();
        assertEquals("insert into test (col) values('import org.x.Test;\n" +
                "import com.y.Testy;\n" +
                "\n" +
                "class A {\n" +
                "  int x, y = 0;\n" +
                "}\n" +
                "')", nextStatement);
        assertEquals("insert into test (col) values('<!--\n" +
                "<test></test>\n" +
                "-->')", reader.readSqlStatement());
        assertEquals("insert into test (col1,col2) values('<!--', -- test\n'<test></test>')", reader.readSqlStatement());
        assertEquals("select col1 /* test */, col2 /* col2 */ from rubixcube", reader.readSqlStatement());
        reader.setStripOutComments(true);
        nextStatement = reader.readSqlStatement();
        assertEquals("select col1 , col2  from rubixcube", nextStatement);
        nextStatement = reader.readSqlStatement();
        assertEquals("insert into test (col1, col2) ('test', '\n" +
                "''test'';\n" +
                "')", nextStatement);
        assertNull(reader.readSqlStatement());
        reader.close();
    }
}
