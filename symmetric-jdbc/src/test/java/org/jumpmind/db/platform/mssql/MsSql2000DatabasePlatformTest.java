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
package org.jumpmind.db.platform.mssql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jumpmind.db.mock.MockDbDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link MsSql2000DatabasePlatform}
 */
@DisplayName("MsSql2000DatabasePlatform")
class MsSql2000DatabasePlatformTest {
    private static final int MSSQL_2000_VERSION = 8;
    private MsSql2000DatabasePlatform platform;

    @BeforeEach
    void setUp() {
        MockDbDataSource mockDataSource = new MockDbDataSource(MSSQL_2000_VERSION);
        platform = new MsSql2000DatabasePlatform(mockDataSource, mockDataSource.getSqlTemplateSettings());
    }

    @Test
    @DisplayName("formatTimeValue returns null for null input")
    void testFormatTimeValueWithNull() {
        assertNull(platform.formatTimeValue(null));
    }

    @Test
    @DisplayName("formatTimeValue handles simple time without milliseconds")
    void testFormatTimeValueSimpleTime() {
        assertEquals("02:30:00", platform.formatTimeValue("02:30:00"));
    }

    @Test
    @DisplayName("formatTimeValue strips milliseconds from time")
    void testFormatTimeValueStripsMilliseconds() {
        assertEquals("18:00:00", platform.formatTimeValue("18:00:00.000"));
    }

    @Test
    @DisplayName("formatTimeValue extracts time from datetime and strips milliseconds")
    void testFormatTimeValueExtractsTimeFromDatetime() {
        assertEquals("12:45:00", platform.formatTimeValue("1970-01-01 12:45:00.000"));
    }

    @Test
    @DisplayName("formatTimeValue handles time with only date prefix and no milliseconds")
    void testFormatTimeValueDatePrefixNoMilliseconds() {
        assertEquals("15:30:00", platform.formatTimeValue("2000-06-15 15:30:00"));
    }

    @Test
    @DisplayName("formatTimeValue handles edge case with decimal point at different positions")
    void testFormatTimeValueDecimalEdgeCases() {
        assertEquals("09:15:30", platform.formatTimeValue("09:15:30.1"));
        assertEquals("09:15:30", platform.formatTimeValue("09:15:30.12"));
        assertEquals("09:15:30", platform.formatTimeValue("09:15:30.123456789"));
    }

    @ParameterizedTest
    @DisplayName("formatTimeValue handles various time formats")
    @CsvSource({
            "02:30:00, 02:30:00",
            "18:00:00.000, 18:00:00",
            "1970-01-01 12:45:00.000, 12:45:00",
            "23:59:59, 23:59:59",
            "00:00:00, 00:00:00",
            "12:00:00.123, 12:00:00",
            "2023-12-25 08:30:45.999, 08:30:45",
            "1999-01-01 00:00:00.000, 00:00:00"
    })
    void testFormatTimeValueParameterized(String input, String expected) {
        assertEquals(expected, platform.formatTimeValue(input));
    }
}
