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
package org.jumpmind.db.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

import org.jumpmind.db.platform.h2.H2DdlBuilder;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource; 

public class AbstractDatabasePlatformTest {
    @Test
    public void testParseDate() {
        {
            assertNull(testDatabasePlatform.parseDate(Types.DATE, null, false));
            assertEquals(new Date(Timestamp.valueOf("2015-11-03 00:00:00").getTime()),
                    testDatabasePlatform.parseDate(Types.DATE, "2015-11-03", false));
            assertEquals(Timestamp.valueOf("2015-11-03 01:35:03.714566"),
                    testDatabasePlatform.parseDate(Types.TIMESTAMP, "2015-11-03 01:35:03.714566", false));
            assertEquals(Timestamp.valueOf("2015-11-03 01:35:03.714566"),
                    testDatabasePlatform.parseDate(Types.TIMESTAMP, "2015-11-03 01:35:03.714566 -05:00", false));
            assertEquals(Timestamp.valueOf("2015-11-03 01:35:03.714566"),
                    testDatabasePlatform.parseDate(Types.TIMESTAMP, "2015-11-03 01:35:03.714566", false));
            assertEquals(Timestamp.valueOf("1970-01-01 01:35:03.714566"),
                    testDatabasePlatform.parseDate(Types.TIMESTAMP, "01:35:03.714566", false));
            assertEquals(Timestamp.valueOf("2015-11-03 01:35:03.714566"),
                    testDatabasePlatform.parseDate(Types.TIME, "2015-11-03 01:35:03.714566", false));
            assertEquals(Timestamp.valueOf("1970-01-01 01:35:03.714566"),
                    testDatabasePlatform.parseDate(Types.TIME, "01:35:03.714566", false));
            // TODO: build out more tests for date/time combinations.
        }
    }

    @Test
    public void testParseTimeZone() {
        assertEquals(-18000000, testDatabasePlatform.getTimeZone("EST").getRawOffset());
        assertEquals(-18000000, testDatabasePlatform.getTimeZone("-05:00").getRawOffset());
    }

    @Test
    public void testParseQualifiedTableName() {
        assertEquals("SIMPLE_TABLE_NAME", testDatabasePlatform.parseQualifiedTableName("SIMPLE_TABLE_NAME").get("table"));
        assertEquals(1, testDatabasePlatform.parseQualifiedTableName("SIMPLE_TABLE_NAME").size());
        assertEquals("\"QUOTED_TABLE_NAME\"", testDatabasePlatform.parseQualifiedTableName("\"QUOTED_TABLE_NAME\"").get("table"));
        assertEquals(1, testDatabasePlatform.parseQualifiedTableName("\"QUOTED_TABLE_NAME\"").size());
        assertEquals("TABLE", testDatabasePlatform.parseQualifiedTableName("SCHEMA.TABLE").get("table"));
        assertEquals("SCHEMA", testDatabasePlatform.parseQualifiedTableName("SCHEMA.TABLE").get("schema"));
        assertEquals(2, testDatabasePlatform.parseQualifiedTableName("SCHEMA.TABLE").size());
        assertEquals("CATALOG", testDatabasePlatform.parseQualifiedTableName("CATALOG.SCHEMA.TABLE").get("catalog"));
        assertEquals("TABLE", testDatabasePlatform.parseQualifiedTableName("CATALOG.SCHEMA.TABLE").get("table"));
        assertEquals("SCHEMA", testDatabasePlatform.parseQualifiedTableName("CATALOG.SCHEMA.TABLE").get("schema"));
        assertEquals(3, testDatabasePlatform.parseQualifiedTableName("CATALOG.SCHEMA.TABLE").size());
        assertEquals("\"CATALOG\"", testDatabasePlatform.parseQualifiedTableName("\"CATALOG\".\"SCHEMA\".\"TABLE\"").get("catalog"));
        assertEquals("\"TABLE\"", testDatabasePlatform.parseQualifiedTableName("\"CATALOG\".\"SCHEMA\".\"TABLE\"").get("table"));
        assertEquals("\"SCHEMA\"", testDatabasePlatform.parseQualifiedTableName("\"CATALOG\".\"SCHEMA\".\"TABLE\"").get("schema"));
        assertEquals(3, testDatabasePlatform.parseQualifiedTableName("\"CATALOG\".\"SCHEMA\".\"TABLE\"").size());
    }

    @ParameterizedTest
    @CsvSource({ "1,true", "true,true", "0,false" })
    void testParseBoolean(String textValue, String expectedValue) {
        Boolean expectedResult = Boolean.valueOf(expectedValue);
        Object result = testDatabasePlatform.parseBoolean(textValue); // null value is not applicable because of check in getObjectValue
        assertEquals(expectedResult, result);
    }

    private AbstractDatabasePlatform testDatabasePlatform = new AbstractDatabasePlatform(new SqlTemplateSettings()) {
        @Override
        public String getName() {
            return "Test";
        }

        @Override
        public String getDefaultSchema() {
            return "default Schema.";
        }

        @Override
        public String getDefaultCatalog() {
            return "";
        }

        @Override
        public <T> T getDataSource() {
            return null;
        }

        @Override
        public ISqlTemplate getSqlTemplate() {
            return null;
        }

        @Override
        public ISqlTemplate getSqlTemplateDirty() {
            return null;
        }

        @Override
        public IDdlBuilder getDdlBuilder() {
            return new H2DdlBuilder();
        }
    };
}
