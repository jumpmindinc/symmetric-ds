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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.JdbcDatabasePlatformFactory;
import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * SYM-7916, end to end. {@link JdbcSqlTemplateObjectExistsTest} covers {@code doesObjectAlreadyExist} and {@code isTolerateObjectAlreadyExists} in isolation;
 * this drives a real duplicate-object failure through {@link JdbcSqlTemplate#execute} itself, the branch neither of those unit tests reaches, against a real H2
 * database rather than a mock. Same pattern as {@link SqlScriptTest}.
 */
public class JdbcSqlTemplateObjectExistsIntegrationTest {
    private SingleConnectionDataSource ds;

    @AfterEach
    void tearDown() {
        if (ds != null) {
            ds.destroy();
        }
    }

    @Test
    void toleratedByDefaultTheDuplicateCreateIsSkippedAndTheScriptCompletes() throws Exception {
        IDatabasePlatform platform = platform("jstoleron");
        // H2's own duplicate-table message ("Table \"T\" already exists") is exactly the generic fragment
        // this classifier matches, so this exercises the real driver's wording, not a fabricated one.
        new SqlScript("CREATE TABLE t (id int);\nCREATE TABLE t (id int);\n", platform.getSqlTemplate(), true, null).execute();
        assertEquals(0, new JdbcTemplate(ds).queryForObject("select count(*) from t", Integer.class));
    }

    @Test
    void withToleranceOffTheDuplicateCreateStillFailsTheScript() throws Exception {
        IDatabasePlatform platform = platform("jstoleroff");
        TypedProperties properties = new TypedProperties();
        properties.put(SqlConstants.TOLERATE_OBJECT_ALREADY_EXISTS_ON_DDL, "false");
        ((JdbcSqlTemplate) platform.getSqlTemplate()).getSettings().setProperties(properties);
        SqlScript script = new SqlScript("CREATE TABLE t (id int);\nCREATE TABLE t (id int);\n", platform.getSqlTemplate(), true, null);
        assertThrows(Exception.class, script::execute,
                "restoring the pre-SYM-7916 behavior (parameter off) must still fail the batch on a duplicate create");
    }

    @Test
    void toleranceNeverMasksAnUnrelatedFailure() throws Exception {
        IDatabasePlatform platform = platform("jsunrelated");
        // Tolerance is on (default); a syntax error must still fail the script. This is the case Pavel's
        // review was protecting: the tolerance must never become a general "ignore DDL errors" switch.
        SqlScript script = new SqlScript("CREATE TALBE typo (id int);\n", platform.getSqlTemplate(), true, null);
        assertThrows(Exception.class, script::execute, "a genuine SQL error must not be swallowed by the tolerance");
    }

    private IDatabasePlatform platform(String dbName) throws Exception {
        Class.forName("org.h2.Driver");
        Connection c = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1");
        ds = new SingleConnectionDataSource(c, true);
        return JdbcDatabasePlatformFactory.getInstance().create(ds, new SqlTemplateSettings(), true, false);
    }
}
