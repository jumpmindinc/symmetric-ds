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
package org.jumpmind.symmetric.service.impl;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.TestConstants;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.symmetric.service.IDataExtractorService;
import org.jumpmind.symmetric.service.ITriggerRouterService;
import org.jumpmind.symmetric.transport.internal.InternalOutgoingTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractDataExtractorServiceTest extends AbstractServiceTest {
    protected static final String TEST_TABLE = "test_extract_table";
    private static int id = 0;

    @BeforeEach
    public void setupForTest() {
        ITriggerRouterService triggerRouterService = getTriggerRouterService();
        TriggerRouter triggerRouter = triggerRouterService.findTriggerRouterById(TEST_TABLE,
                TestConstants.ROUTER_ID_ROOT_2_TEST);
        if (triggerRouter == null) {
            triggerRouter = new TriggerRouter(
                    new Trigger(TEST_TABLE, TestConstants.TEST_CHANNEL_ID), new Router(
                            TestConstants.ROUTER_ID_ROOT_2_TEST, TestConstants.ROOT_2_TEST));
            triggerRouterService.saveTriggerRouter(triggerRouter);
            triggerRouterService.syncTriggers();
            getDbDialect().truncateTable(TEST_TABLE);
        }
        resetBatches();
    }

    @Test
    public void testExtractConfigurationStandalone() throws Exception {
        IDataExtractorService dataExtractorService = getDataExtractorService();
        StringWriter writer = new StringWriter();
        dataExtractorService.extractConfigurationStandalone(TestConstants.TEST_CLIENT_NODE, writer);
        String content = writer.getBuffer().toString();
        System.out.println(content);
        assertNumberOfLinesThatStartWith(26, "table,", content, false, true);
        assertNumberOfLinesThatStartWith(26, "columns,", content);
        assertNumberOfLinesThatStartWith(26, "keys,", content);
        assertNumberOfLinesThatStartWith(26, "sql,", content);
        assertNumberOfLinesThatStartWith(0, "update,", content);
        assertNumberOfLinesThatStartWith(65, "insert,", content, false, true);
        assertNumberOfLinesThatStartWith(1, "commit,-9999", content);
        assertNumberOfLinesThatStartWith(1, "batch,-9999", content);
        assertNumberOfLinesThatStartWith(1, "nodeid,", content);
        assertNumberOfLinesThatStartWith(1, "binary,", content);
    }

    @Test
    public void testNothingToExtract() {
        ExtractResults results = extract();
        assertNotNull(results.getBatches());
        assertEquals(0, results.getBatches().size());
        assertTrue(StringUtils.isBlank(results.getCsv()));
    }

    @Test
    public void testExtractOneBatchOneRow() {
        save(new TestExtract(id++, "abc 123", "abcdefghijklmnopqrstuvwxyz", new Timestamp(
                System.currentTimeMillis()), new Date(System.currentTimeMillis()), true,
                Integer.MAX_VALUE, new BigDecimal(Double.toString(Math.PI))));
        routeAndCreateGaps();
        ExtractResults results = extract();
        assertNotNull(results.getBatches());
        assertEquals(1, results.getBatches().size());
        assertNumberOfLinesThatStartWith(1, "insert,", results.getCsv());
        long batchId = results.getBatches().get(0).getBatchId();
        assertNumberOfLinesThatStartWith(1, "batch," + batchId, results.getCsv());
        assertNumberOfLinesThatStartWith(1, "commit," + batchId, results.getCsv());
        assertNumberOfLinesThatStartWith(1, "table," + TEST_TABLE, results.getCsv(), true, false);
        // same batch should be extracted
        results = extract();
        assertNumberOfLinesThatStartWith(1, "retry," + batchId, results.getCsv());
        assertNumberOfLinesThatStartWith(1, "commit," + batchId, results.getCsv());
    }

    protected ExtractResults extract() {
        IDataExtractorService service = getDataExtractorService();
        StringWriter writer = new StringWriter();
        InternalOutgoingTransport transport = new InternalOutgoingTransport(new BufferedWriter(
                writer));
        List<OutgoingBatch> batches = service.extract(new ProcessInfo(), TestConstants.TEST_CLIENT_NODE, transport);
        transport.close();
        return new ExtractResults(batches, writer.getBuffer().toString());
    }

    protected void save(TestExtract obj) {
        String updateSql = String
                .format("update %s set varchar_value=?, longvarchar_value=?, timestamp_value=?, date_value=?, bit_value=?, bigint_value=?, decimal_value=? where id=?",
                        TEST_TABLE);
        String insertSql = String
                .format("insert into %s (varchar_value, longvarchar_value, timestamp_value, date_value, bit_value, bigint_value, decimal_value, id) values(?,?,?,?,?,?,?,?)",
                        TEST_TABLE);
        if (0 >= getSqlTemplate().update(
                updateSql,
                new Object[] { obj.getVarcharValue(), obj.getLongVarcharValue(),
                        obj.getTimestampValue(), obj.getDateValue(), obj.isBitValue(),
                        obj.getBigIntValue(), obj.getDecimalValue(), obj.getId() },
                new int[] { Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.DATE, Types.BIT,
                        Types.NUMERIC, Types.NUMERIC, Types.NUMERIC })) {
            getSqlTemplate().update(
                    insertSql,
                    new Object[] { obj.getVarcharValue(), obj.getLongVarcharValue(),
                            obj.getTimestampValue(), obj.getDateValue(), obj.isBitValue(),
                            obj.getBigIntValue(), obj.getDecimalValue(), obj.getId() },
                    new int[] { Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.DATE,
                            Types.BIT, Types.NUMERIC, Types.NUMERIC, Types.NUMERIC });
        }
    }

    static class ExtractResults {
        private List<OutgoingBatch> batches;
        private String csv;

        public ExtractResults(List<OutgoingBatch> batches, String csv) {
            this.batches = batches;
            this.csv = csv;
        }

        public List<OutgoingBatch> getBatches() {
            return batches;
        }

        public String getCsv() {
            return csv;
        }
    }

    static class TestExtract {
        private int id;
        private String varcharValue;
        private String longVarcharValue;
        private Timestamp timestampValue;
        private Date dateValue;
        private boolean bitValue;
        private long bigIntValue;
        private BigDecimal decimalValue;

        public TestExtract(int id, String varcharValue, String longVarcharValue,
                Timestamp timestampValue, Date dateValue, boolean bitValue, long bigIntValue,
                BigDecimal decimalValue) {
            this.id = id;
            this.varcharValue = varcharValue;
            this.longVarcharValue = longVarcharValue;
            this.timestampValue = timestampValue;
            this.dateValue = dateValue;
            this.bitValue = bitValue;
            this.bigIntValue = bigIntValue;
            this.decimalValue = decimalValue;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getVarcharValue() {
            return varcharValue;
        }

        public void setVarcharValue(String varcharValue) {
            this.varcharValue = varcharValue;
        }

        public String getLongVarcharValue() {
            return longVarcharValue;
        }

        public void setLongVarcharValue(String longVarcharValue) {
            this.longVarcharValue = longVarcharValue;
        }

        public Timestamp getTimestampValue() {
            return timestampValue;
        }

        public void setTimestampValue(Timestamp timestampValue) {
            this.timestampValue = timestampValue;
        }

        public Date getDateValue() {
            return dateValue;
        }

        public void setDateValue(Date dateValue) {
            this.dateValue = dateValue;
        }

        public boolean isBitValue() {
            return bitValue;
        }

        public void setBitValue(boolean bitValue) {
            this.bitValue = bitValue;
        }

        public long getBigIntValue() {
            return bigIntValue;
        }

        public void setBigIntValue(long bigIntValue) {
            this.bigIntValue = bigIntValue;
        }

        public BigDecimal getDecimalValue() {
            return decimalValue;
        }

        public void setDecimalValue(BigDecimal decimalValue) {
            this.decimalValue = decimalValue;
        }
    }
}
