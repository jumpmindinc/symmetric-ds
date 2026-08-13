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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * SYM-7916. Classification of "object already exists" DDL failures.
 * <p>
 * The reach of the fix rests entirely on these message fragments for platforms whose DDL builders are not in this repository (SQL Server, Oracle), so the exact
 * vendor wording is pinned here. Error codes are per-platform and cannot be shared defaults, since the same number means different things on different vendors.
 */
class JdbcSqlTemplateObjectExistsTest {
    private JdbcSqlTemplate template() {
        return new JdbcSqlTemplate(null, new SqlTemplateSettings(), null, new DatabaseInfo());
    }

    private static SQLException sqlException(String message, String sqlState, int errorCode) {
        return new SQLException(message, sqlState, errorCode);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                    "SQL Server 1913 index    | The operation failed because an index or statistics with name 'f0101_12' already exists on table 'DB.dbo.f0101'. | S0001 | 1913",
                    "SQL Server 2714 table    | There is already an object named 'f42119' in the database.                                                       | S0001 | 2714",
                    "Oracle ORA-00955         | ORA-00955: name is already used by an existing object                                                            | 42000 |  955",
                    "MySQL duplicate key name | Duplicate key name 'f42119_18'                                                                                   | 42000 | 1061",
                    "case insensitive         | INDEX ALREADY EXISTS                                                                                             | S0001 | 1913",
            })
    void vendorAlreadyExistsMessagesAreRecognised(String label, String message, String sqlState, int errorCode) {
        assertTrue(template().doesObjectAlreadyExist(sqlException(message, sqlState, errorCode)), label);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                    "primary key violation | Violation of PRIMARY KEY constraint 'PK_f42119'. Cannot insert duplicate key. | 23000 | 2627",
                    "missing object        | Invalid object name 'dbo.f42119'.                                            | S0002 |  208",
                    "timeout               | Timeout expired                                                              | HYT00 |    0",
            })
    void unrelatedFailuresAreNotClassifiedAsAlreadyExisting(String label, String message, String sqlState, int errorCode) {
        // The tolerance must not swallow a genuine problem.
        assertFalse(template().doesObjectAlreadyExist(sqlException(message, sqlState, errorCode)), label);
    }

    @Test
    void aPlatformErrorCodeStillMatchesWithoutAMessageMatch() {
        // Server-localized messages defeat message matching, which is why a platform that depends on this should
        // populate the error-code list as well.
        JdbcSqlTemplate template = template();
        template.objectAlreadyExistsCodes = new int[] { 1913 };
        assertTrue(template.doesObjectAlreadyExist(
                sqlException("Die Operation ist fehlgeschlagen: Index vorhanden", "S0001", 1913)));
    }

    @Test
    void nonSqlExceptionsAreNotClassified() {
        assertFalse(template().doesObjectAlreadyExist(new RuntimeException("already exists")));
    }

    @ParameterizedTest(name = "property={0} -> tolerate={1}")
    @CsvSource({ ", true", "true, true", "false, false" })
    void toleranceDefaultsOnAndIsControlledByTheParameter(String value, boolean expected) {
        JdbcSqlTemplate template = template();
        if (value != null) {
            TypedProperties properties = new TypedProperties();
            properties.put(SqlConstants.TOLERATE_OBJECT_ALREADY_EXISTS_ON_DDL, value);
            template.getSettings().setProperties(properties);
        }
        assertTrue(expected == template.isTolerateObjectAlreadyExists(),
                "value=" + value + " expected tolerate=" + expected);
    }
}
