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
package org.jumpmind.symmetric.wrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WrapperLogFormatterTest {
    private static final String NEWLINE = System.getProperty("line.separator");
    private static final long MILLIS = 1700000000000L;
    private WrapperLogFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new WrapperLogFormatter();
    }

    @Test
    void testFormat_withoutParameters_usesWrapperAsSource() {
        assertEquals(timestamp() + " [INFO   ] [wrapper] Started wrapper as PID 100" + NEWLINE,
                formatter.format(newRecord(Level.INFO, "Started wrapper as PID 100")));
    }

    @Test
    void testFormat_withParameters_usesFirstParameterAsSource() {
        LogRecord record = newRecord(Level.INFO, "Server started");
        record.setParameters(new Object[] { "java" });
        assertEquals(timestamp() + " [INFO   ] [java   ] Server started" + NEWLINE, formatter.format(record));
    }

    @Test
    void testFormat_withEmptyParameters_usesWrapperAsSource() {
        LogRecord record = newRecord(Level.INFO, "Server started");
        record.setParameters(new Object[0]);
        assertEquals(timestamp() + " [INFO   ] [wrapper] Server started" + NEWLINE, formatter.format(record));
    }

    @Test
    void testFormat_withNonStringParameter_usesToString() {
        LogRecord record = newRecord(Level.INFO, "Server started");
        record.setParameters(new Object[] { Integer.valueOf(12) });
        assertEquals(timestamp() + " [INFO   ] [12     ] Server started" + NEWLINE, formatter.format(record));
    }

    @Test
    void testFormat_padsShortLevelNames() {
        assertTrue(formatter.format(newRecord(Level.FINE, "Details")).contains("[FINE   ]"));
    }

    @Test
    void testFormat_doesNotTruncateLongLevelNames() {
        assertTrue(formatter.format(newRecord(Level.WARNING, "Careful")).contains("[WARNING]"));
    }

    @Test
    void testFormat_doesNotTruncateLongSourceNames() {
        LogRecord record = newRecord(Level.SEVERE, "Boom");
        record.setParameters(new Object[] { "a-very-long-source" });
        assertTrue(formatter.format(record).contains("[a-very-long-source]"));
    }

    @Test
    void testFormat_withNullMessage() {
        assertEquals(timestamp() + " [INFO   ] [wrapper] null" + NEWLINE, formatter.format(newRecord(Level.INFO, null)));
    }

    private LogRecord newRecord(Level level, String message) {
        LogRecord record = new LogRecord(level, message);
        record.setInstant(Instant.ofEpochMilli(MILLIS));
        return record;
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(MILLIS));
    }
}
