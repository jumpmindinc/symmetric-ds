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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

import org.apache.commons.codec.binary.Base64;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.util.FormatUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public class LogSqlBuilderTest {
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger("LogSqlBuilderTestLogger");
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        MDC.remove("engineName");
    }

    @Test
    void testLogSql_fiveArgOverload_delegatesWithNullMessage() {
        LogSqlBuilder builder = new LogSqlBuilder();
        builder.logSql(logger, "select 1", null, null, 5L);
        assertEquals(1, appender.list.size());
        assertEquals(Level.DEBUG, appender.list.get(0).getLevel());
        assertEquals("(5ms.) select 1", appender.list.get(0).getFormattedMessage());
    }

    @Test
    void testLogSql_sixArgOverload_withMessage_prependsMessageToLogEntry() {
        LogSqlBuilder builder = new LogSqlBuilder();
        builder.logSql(logger, "context", "select 1", null, null, 5L);
        assertEquals("(5ms.) context select 1", appender.list.get(0).getFormattedMessage());
    }

    @Test
    void testLogSql_whenExecutionTimeExceedsDefaultThreshold_logsAtInfoWithLongRunningPrefix() {
        LogSqlBuilder builder = new LogSqlBuilder();
        builder.logSql(logger, null, "select 1", null, null, 20000L);
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertEquals("Long Running: (20000ms.) select 1", appender.list.get(0).getFormattedMessage());
    }

    @Test
    void testLogSql_withGuiEngine_usesLowerConsoleThresholdForLongRunning() {
        MDC.put("engineName", "gui");
        LogSqlBuilder builder = new LogSqlBuilder();
        builder.logSql(logger, null, "select 1", null, null, 5000L);
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().startsWith("Long Running:"));
    }

    @Test
    void testLogSql_withParametersInlineFalse_logsRawSqlThenSeparateArgsLine() {
        LogSqlBuilder builder = new LogSqlBuilder();
        builder.setLogSqlParametersInline(false);
        builder.logSql(logger, null, "select * from t where id = ?", new Object[] { 1 }, null, 5L);
        assertEquals(2, appender.list.size());
        assertEquals("(5ms.) select * from t where id = ?", appender.list.get(0).getFormattedMessage());
        assertEquals("sql args: [1]", appender.list.get(1).getFormattedMessage());
    }

    @Test
    void testLogSql_whenLoggerNotDebugEnabledAndNotLongRunning_logsNothing() {
        logger.setLevel(Level.INFO);
        LogSqlBuilder builder = new LogSqlBuilder();
        builder.logSql(logger, null, "select 1", null, null, 5L);
        assertTrue(appender.list.isEmpty());
    }

    @Test
    void testLogSql_whenLoggerTraceEnabled_appendsTrailingCarriageReturn() {
        logger.setLevel(Level.TRACE);
        LogSqlBuilder builder = new LogSqlBuilder();
        builder.logSql(logger, null, "select 1", null, null, 5L);
        assertEquals("(5ms.) select 1\r\n", appender.list.get(0).getFormattedMessage());
    }

    @Test
    void testLogSqlAfterException_returnsSameExceptionAndLogsMessageWithArgs() {
        LogSqlBuilder builder = new LogSqlBuilder();
        SQLException ex = new SQLException("boom");
        SQLException result = builder.logSqlAfterException(logger, "select * from t where id = ?", new Object[] { 1 }, ex);
        assertSame(ex, result);
        assertEquals("SQL caused exception: [select * from t where id = ?] sql args: [1] " + ex, appender.list.get(0).getFormattedMessage());
    }

    @Test
    void testLogSqlAfterException_withNoArgs_omitsArgsSegment() {
        LogSqlBuilder builder = new LogSqlBuilder();
        SQLException ex = new SQLException("boom");
        builder.logSqlAfterException(logger, "select 1", null, ex);
        assertEquals("SQL caused exception: [select 1] " + ex, appender.list.get(0).getFormattedMessage());
    }

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

    @Test
    void testFormatValue_withNullObject_returnsLiteralNullString() {
        LogSqlBuilder builder = new LogSqlBuilder();
        assertEquals("null", builder.formatValue(null, Types.INTEGER));
    }

    @Test
    void testFormatValue_withStringOverAbbreviationLimit_truncatesOutput() {
        LogSqlBuilder builder = new LogSqlBuilder();
        String longValue = "a".repeat(9000);
        String result = builder.formatValue(longValue, Types.VARCHAR);
        assertEquals(LogSqlBuilder.MAX_FIELD_SIZE_TO_PRINT_TO_LOG, result.length());
        assertTrue(result.endsWith("..."));
    }

    @Test
    void testFormatValue_withTimestampAndTimeType_usesTimeFormatter() {
        LogSqlBuilder builder = new LogSqlBuilder();
        Date date = FormatUtils.parseDate("2016-04-20 17:12:57", FormatUtils.TIMESTAMP_PATTERNS);
        Timestamp ts = new Timestamp(date.getTime());
        assertEquals("{t '17:12:57.000000000'}", builder.formatValue(ts, Types.TIME));
    }

    @Test
    void testFormatValue_withParseableDateTimeString_parsesAndFormatsAsTimestamp() {
        LogSqlBuilder builder = new LogSqlBuilder();
        String result = builder.formatValue("2016-04-20 17:12:57", Types.TIMESTAMP);
        assertEquals("{ts '2016-04-20 17:12:57.000'}", result);
    }

    @Test
    void testFormatValue_withTimezoneAwareTimestampString_parsesViaTimezoneFallback() {
        LogSqlBuilder builder = new LogSqlBuilder();
        String result = builder.formatValue("2016-04-20 17:12:57.123456789 +02:00", Types.TIMESTAMP);
        assertTrue(result.startsWith("{ts '"));
    }

    @Test
    void testFormatValue_withUnparseableDateTimeValue_returnsQuotedRawValue() {
        LogSqlBuilder builder = new LogSqlBuilder();
        assertEquals("'not-a-date'", builder.formatValue("not-a-date", Types.TIMESTAMP));
    }

    @Test
    void testFormatValue_withBlobAndBinaryType_readsBytesAndHexEncodesThem() throws Exception {
        Blob blob = mock(Blob.class);
        when(blob.getBinaryStream()).thenReturn(new ByteArrayInputStream(new byte[] { 0, 2, 8 }));
        LogSqlBuilder builder = new LogSqlBuilder();
        assertEquals("'000208'", builder.formatValue(blob, Types.BLOB));
    }

    @Test
    void testFormatValue_withBlobReadFailure_returnsQuotedRawValue() throws Exception {
        Blob blob = mock(Blob.class);
        when(blob.getBinaryStream()).thenThrow(new SQLException("boom"));
        LogSqlBuilder builder = new LogSqlBuilder();
        assertEquals("'" + blob + "'", builder.formatValue(blob, Types.BLOB));
    }

    @Test
    void testFormatValue_withNonByteArrayNonBlobAndBinaryType_returnsQuotedRawValue() {
        LogSqlBuilder builder = new LogSqlBuilder();
        assertEquals("'not-binary'", builder.formatValue("not-binary", Types.BINARY));
    }

    @Test
    void testFormatValue_withBase64Encoding_encodesBytesAsBase64() {
        LogSqlBuilder builder = new LogSqlBuilder();
        builder.encoding = BinaryEncoding.BASE64;
        byte[] bytes = { 0, 2, 8 };
        String expected = "'" + new String(Base64.encodeBase64(bytes), Charset.defaultCharset()) + "'";
        assertEquals(expected, builder.formatValue(bytes, Types.BINARY));
    }

    @Test
    void testFormatValue_withNoneEncoding_returnsQuotedRawValue() {
        LogSqlBuilder builder = new LogSqlBuilder();
        builder.encoding = BinaryEncoding.NONE;
        byte[] bytes = { 0, 2, 8 };
        assertEquals("'" + bytes + "'", builder.formatValue(bytes, Types.BINARY));
    }

    @Test
    void testFormatValue_withBooleanAndUnknownType_returnsToStringViaFormatUnknownType() {
        LogSqlBuilder builder = new LogSqlBuilder();
        assertEquals("true", builder.formatValue(Boolean.TRUE, Types.OTHER));
    }

    @Test
    void testFormatValue_withOtherNumericWrapperTypes_returnsToStringViaFormatUnknownType() {
        LogSqlBuilder builder = new LogSqlBuilder();
        assertEquals("5", builder.formatValue(5L, Types.OTHER));
        assertEquals("5", builder.formatValue(new BigDecimal("5"), Types.OTHER));
        assertEquals("5", builder.formatValue((short) 5, Types.OTHER));
        assertEquals("5.0", builder.formatValue(5.0f, Types.OTHER));
        assertEquals("5.0", builder.formatValue(5.0d, Types.OTHER));
    }

    @Test
    void testFormatValue_withDateAndUnknownType_delegatesToFormatDateTimeValueAsTimestamp() {
        LogSqlBuilder builder = new LogSqlBuilder();
        Date date = FormatUtils.parseDate("2016-04-20 17:12:57", FormatUtils.TIMESTAMP_PATTERNS);
        String expected = builder.formatValue(date, Types.TIMESTAMP);
        assertEquals(expected, builder.formatValue(date, Types.OTHER));
    }
}
