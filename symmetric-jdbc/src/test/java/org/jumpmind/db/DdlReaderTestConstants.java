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
package org.jumpmind.db;

final public class DdlReaderTestConstants {
    private DdlReaderTestConstants() {
    }

    public final static String CATALOG = "testCatalog";
    public final static String SCHEMA = "testSchema";
    public final static String TABLE = "testTable";
    public final static String STATEMENT2QUERY = "SELECT a.INDEX_NAME, a.INDEX_TYPE, a.UNIQUENESS, b.COLUMN_NAME, b.COLUMN_POSITION, c.COLUMN_EXPRESSION FROM ALL_INDEXES a JOIN ALL_IND_COLUMNS b ON a.table_name = b.table_name AND a.INDEX_NAME=b.INDEX_NAME AND a.TABLE_OWNER = b.TABLE_OWNER LEFT JOIN ALL_IND_EXPRESSIONS c ON a.table_name = c.table_name AND a.INDEX_NAME=c.INDEX_NAME AND a.TABLE_OWNER = c.TABLE_OWNER WHERE a.TABLE_NAME = ? AND a.GENERATED='N' AND a.TABLE_TYPE='TABLE' AND a.TABLE_OWNER = ?";
    public final static String STATEMENT1QUERY = "SELECT CONSTRAINT_NAME FROM ALL_CONSTRAINTS c WHERE c.TABLE_NAME = ? AND CONSTRAINT_TYPE = 'P' AND c.OWNER = ?";
    public final static String STATEMENT3QUERY = "SELECT * FROM user_triggers WHERE trigger_name = ?";
    public final static String STATEMENT4QUERY = "SELECT * FROM user_sequences WHERE sequence_name = ?";
    public final static String TABLE_NAME = "TABLE_NAME";
    public final static String TABLE_TYPE = "TABLE_TYPE";
    public final static String OWNER = "OWNER";
    public final static String TABLE_CAT = "TABLE_CAT";
    public final static String TABLE_SCHEM = "TABLE_SCHEM";
    public final static String REMARKS = "REMARKS";
    public final static String COLUMN_DEF = "COLUMN_DEF";
    public final static String COLUMN_DEFAULT = "COLUMN_DEFAULT";
    public final static String COLUMN_NAME = "COLUMN_NAME";
    public final static String TYPE_NAME = "TYPE_NAME";
    public final static String DATA_TYPE = "DATA_TYPE";
    public final static String IS_NULLABLE = "IS_NULLABLE";
    public final static String PK_NAME = "PK_NAME";
    public final static String TESTNAME = "testName";
    public final static String TESTNAMECAPS = "TESTNAME";
    public final static String TABLE_TYPE_TEST_VALUE = "testType";
    public final static String TABLE_CAT_TEST_VALUE = "testCat";
    public final static String TABLE_SCHEMA_TEST_VALUE = "testSchem";
    public final static String REMARKS_TEST_VALUE = "testRemark";
    public final static String COLUMN_DEF_TEST_VALUE = "testDef";
    public final static String COLUMN_DEFAULT_TEST_VALUE = "testDefault";
    public final static String COLUMN_NAME_TEST_VALUE = "testColumnName";
    public final static String TYPE_NAME_TEST_VALUE = "VARCHAR";
    public final static String DATA_TYPE_TEST_VALUE = "VARCHAR";
    public final static String TRIGGER_NAME = "TRIGGER_NAME";
    public final static String ORACLESQL1 = "SELECT TRIGGER_NAME, OWNER, TABLE_NAME, STATUS, TRIGGERING_EVENT FROM ALL_TRIGGERS WHERE TABLE_NAME=? and OWNER=?";
    public final static String POSTGRESQL1 = "SELECT trigger_name, trigger_schema, trigger_catalog, event_manipulation AS trigger_type, event_object_table AS table_name,trig.*, pgproc.prosrc FROM INFORMATION_SCHEMA.TRIGGERS AS trig INNER JOIN pg_catalog.pg_trigger AS pgtrig ON pgtrig.tgname=trig.trigger_name INNER JOIN pg_catalog.pg_proc AS pgproc ON pgproc.oid=pgtrig.tgfoid WHERE event_object_table=? AND event_object_schema=?;";
    public final static String sql2 = "select TEXT from all_source where OWNER=? AND NAME=? order by LINE";
}