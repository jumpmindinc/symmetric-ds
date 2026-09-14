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

import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

import org.jumpmind.util.FormatUtils;
import org.junit.jupiter.api.Test;

public class LogSqlBuilderTest {
    @Test
    public void testNoPlachodlers() {
        final String SQL = "select * from sym_data where data_id = 234234;";
        LogSqlBuilder builder = new LogSqlBuilder();
        String result = builder.buildDynamicSqlForLog(SQL, null, null);
        assertEquals(SQL, result);
    }

    @Test
    public void testSinglePlachodler() {
        final String SQL = "select * from sym_data where data_id = ?";
        LogSqlBuilder builder = new LogSqlBuilder();
        String result = builder.buildDynamicSqlForLog(SQL, new Object[] { Integer.valueOf(234234) }, new int[] { Types.BIGINT });
        System.out.println(result);
        assertEquals("select * from sym_data where data_id = 234234", result);
    }

    @Test
    public void testSinglePlachodlerNoTypes() {
        final String SQL = "select * from sym_data where data_id = ?";
        LogSqlBuilder builder = new LogSqlBuilder();
        String result = builder.buildDynamicSqlForLog(SQL, new Object[] { Integer.valueOf(234234) }, null);
        System.out.println(result);
        assertEquals("select * from sym_data where data_id = 234234", result);
    }

    @Test
    public void testSinglePlachodlerNoTypesString() {
        final String SQL = "select * from sym_data where data_id = ?";
        LogSqlBuilder builder = new LogSqlBuilder();
        String result = builder.buildDynamicSqlForLog(SQL, new Object[] { "234234" }, null);
        System.out.println(result);
        assertEquals("select * from sym_data where data_id = '234234'", result);
    }

    @Test
    public void testMultiPlaceholders() {
        final String SQL = "select * from sym_data where data_id between ? and ?";
        LogSqlBuilder builder = new LogSqlBuilder();
        String result = builder.buildDynamicSqlForLog(SQL, new Object[] { 12, 21 }, new int[] { Types.BIGINT, Types.BIGINT });
        System.out.println(result);
        assertEquals("select * from sym_data where data_id between 12 and 21", result);
    }

    @Test
    public void testTypes() {
        final String SQL = "update sym_data set data = ? where data_id =? and create_time > ? and create_time <= ? and table_name = ? and time>=? and blob_colum = ?";
        LogSqlBuilder builder = new LogSqlBuilder();
        Date date = FormatUtils.parseDate("2016-04-20 17:12:57", FormatUtils.TIMESTAMP_PATTERNS);
        Timestamp ts = new Timestamp(date.getTime());
        Time time = new Time(date.getTime());
        String result = builder.buildDynamicSqlForLog(SQL, new Object[] { "\"002\",\"hostname\"", 21, date, ts, "sym_node_host", time, new byte[] { 0, 2, 8 } },
                new int[] { Types.CLOB, Types.BIGINT, Types.TIMESTAMP, Types.TIMESTAMP, Types.VARCHAR, Types.TIME, Types.BINARY });
        System.out.println(result);
        assertEquals(
                "update sym_data set data = '\"002\",\"hostname\"' where data_id =21 and create_time > {ts '2016-04-20 17:12:57.000'} and create_time <= {ts '2016-04-20 17:12:57.000000000'} and table_name = 'sym_node_host' and time>={t '17:12:57.000000000'} and blob_colum = '000208'",
                result);
    }

    @Test
    public void testEscapes() {
        final String SQL = "update sym_data set table_name = ?";
        LogSqlBuilder builder = new LogSqlBuilder();
        String result = builder.buildDynamicSqlForLog(SQL, new Object[] { "'?\\/" }, new int[] { Types.CLOB });
        System.out.println(result);
        assertEquals("update sym_data set table_name = '''?\\/'", result);
    }
}
