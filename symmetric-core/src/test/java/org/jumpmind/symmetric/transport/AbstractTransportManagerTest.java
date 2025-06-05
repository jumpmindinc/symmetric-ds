package org.jumpmind.symmetric.transport;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.web.WebConstants;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class AbstractTransportManagerTest {
    private static final List<IncomingBatch> incomingBatchList = new ArrayList<IncomingBatch>();
    static {
        for (long batchId = 0; batchId < 10; batchId++) {
            incomingBatchList.add(createIncomingBatch(batchId));
        }
    }

    private static IncomingBatch createIncomingBatch(long batchId) {
        IncomingBatch batch = new IncomingBatch();
        batch.setBatchId(batchId);
        if (batchId % 3 == 0) {
            batch.setStatus(Status.OK);
            batch.setIgnoreCount(1);
        } else if (batchId % 3 == 1) {
            batch.setStatus(Status.RS);
        } else {
            batch.setStatus(Status.ER);
            batch.setSqlState("JDBC-0123456789abcdef");
            batch.setSqlCode(1);
            batch.setSqlMessage("test message");
        }
        batch.setFailedRowNumber(0);
        batch.setNetworkMillis(1);
        batch.setFilterMillis(2);
        batch.setLoadMillis(3);
        batch.setStartTime(4);
        batch.setByteCount(5);
        batch.setLoadRowCount(6);
        batch.setTransformLoadMillis(7);
        batch.setLoadInsertRowCount(8);
        batch.setLoadUpdateRowCount(9);
        batch.setLoadDeleteRowCount(10);
        batch.setFallbackInsertCount(11);
        batch.setFallbackUpdateCount(12);
        batch.setConflictWinCount(13);
        batch.setConflictLoseCount(14);
        batch.setIgnoreRowCount(15);
        batch.setMissingDeleteCount(16);
        batch.setSkipCount(17);
        batch.setBulkLoaderFlag(true);
        return batch;
    }

    @Test
    public void testGetAcknowledgementDataLimitedFormKeys() throws IOException {
        MockTransportManager transportManager = new MockTransportManager();
        List<String> dataList = transportManager.getAcknowledgementData(false, "node_id", incomingBatchList,
                AbstractTransportManager.FORM_KEYS_PER_BATCH * 2, -1);
        Assert.assertEquals(5, dataList.size());
        for (int i = 0; i < 5; i++) {
            String data = dataList.get(i);
            long batchId = i * 2;
            verifyData(data, batchId);
            verifyData(data, batchId + 1);
        }
    }

    @Test
    public void testGetAcknowledgementDataLimitedByteSize() throws IOException {
        MockTransportManager transportManager = new MockTransportManager();
        List<String> dataList = transportManager.getAcknowledgementData(false, "node_id", incomingBatchList, -1, 1000);
        Assert.assertEquals(5, dataList.size());
        for (int i = 0; i < 5; i++) {
            String data = dataList.get(i);
            long batchId = i * 2;
            verifyData(data, batchId);
            verifyData(data, batchId + 1);
        }
    }

    @Test
    public void testGetAcknowledgementDataUnlimited() throws IOException {
        MockTransportManager transportManager = new MockTransportManager();
        List<String> dataList = transportManager.getAcknowledgementData(false, "node_id", incomingBatchList, -1, -1);
        Assert.assertEquals(1, dataList.size());
        String data = dataList.get(0);
        for (long batchId = 0; batchId < 10; batchId++) {
            verifyData(data, batchId);
        }
    }

    private void verifyData(String data, long batchId) throws IOException {
        Object value = 0;
        if (batchId % 3 == 0) {
            assertContains(data, WebConstants.ACK_IGNORE_COUNT + batchId, 1);
            value = WebConstants.ACK_BATCH_OK;
        } else {
            assertNotContains(data, WebConstants.ACK_IGNORE_COUNT + batchId);
        }
        if (batchId % 3 == 1) {
            value = WebConstants.ACK_BATCH_RESEND;
        }
        if (batchId % 3 == 2) {
            assertContains(data, WebConstants.ACK_SQL_STATE + batchId, "0123456789");
            assertContains(data, WebConstants.ACK_SQL_CODE + batchId, 1);
            assertContains(data, WebConstants.ACK_SQL_MESSAGE + batchId, "test message");
        } else {
            assertNotContains(data, WebConstants.ACK_SQL_STATE + batchId);
            assertNotContains(data, WebConstants.ACK_SQL_CODE + batchId);
            assertNotContains(data, WebConstants.ACK_SQL_MESSAGE + batchId);
        }
        assertContains(data, WebConstants.ACK_BATCH_NAME + batchId, value);
        assertContains(data, WebConstants.ACK_NODE_ID + batchId, "node_id");
        assertContains(data, WebConstants.ACK_NETWORK_MILLIS + batchId, 1);
        assertContains(data, WebConstants.ACK_FILTER_MILLIS + batchId, 2);
        assertContains(data, WebConstants.ACK_DATABASE_MILLIS + batchId, 3);
        assertContains(data, WebConstants.ACK_START_TIME + batchId, 4);
        assertContains(data, WebConstants.ACK_BYTE_COUNT + batchId, 5);
        assertContains(data, WebConstants.ACK_LOAD_ROW_COUNT + batchId, 6);
        assertContains(data, WebConstants.TRANSFORM_TIME + batchId, 7);
        assertContains(data, WebConstants.ACK_LOAD_INSERT_ROW_COUNT + batchId, 8);
        assertContains(data, WebConstants.ACK_LOAD_UPDATE_ROW_COUNT + batchId, 9);
        assertContains(data, WebConstants.ACK_LOAD_DELETE_ROW_COUNT + batchId, 10);
        assertContains(data, WebConstants.ACK_FALLBACK_INSERT_COUNT + batchId, 11);
        assertContains(data, WebConstants.ACK_FALLBACK_UPDATE_COUNT + batchId, 12);
        assertContains(data, WebConstants.ACK_CONFLICT_WIN_COUNT + batchId, 13);
        assertContains(data, WebConstants.ACK_CONFLICT_LOSE_COUNT + batchId, 14);
        assertContains(data, WebConstants.ACK_IGNORE_ROW_COUNT + batchId, 15);
        assertContains(data, WebConstants.ACK_MISSING_DELETE_COUNT + batchId, 16);
        assertContains(data, WebConstants.ACK_SKIP_COUNT + batchId, 17);
        assertContains(data, WebConstants.ACK_BULK_LOADER_FLAG + batchId, true);
    }

    private void assertContains(String data, String name, Object value) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append(name).append("=").append(URLEncoder.encode(value.toString(), StandardCharsets.UTF_8.name()));
        Assert.assertTrue(data.contains(builder.toString()));
    }

    private void assertNotContains(String data, String name) {
        Assert.assertTrue(!data.contains(name + "="));
    }
}
