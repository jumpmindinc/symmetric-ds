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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.extract.SelectFromSymDataSource;
import org.jumpmind.symmetric.io.data.CsvConstants;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.jumpmind.symmetric.io.stage.IStagedResource.State;
import org.jumpmind.symmetric.io.stage.IStagingManager;
import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.route.AbstractFileParsingRouter;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DataExtractorServiceTest {
    protected ISymmetricEngine engine;
    protected IStagingManager stagingManager;
    protected INodeService nodeService;
    protected IParameterService parameterService;
    protected DataExtractorService dataExtractorService;

    @BeforeEach
    public void setUp() {
        engine = mock(ISymmetricEngine.class);
        when(engine.getTablePrefix()).thenReturn("sym");
        TriggerRouterService triggerRouterService = mock(TriggerRouterService.class);
        when(triggerRouterService.getTriggerRoutersByTriggerHist("target", false)).thenReturn(null);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getName()).thenReturn("H2");
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(platform.getSqlTemplate()).thenReturn(mock(ISqlTemplate.class));
        when(platform.getSqlTemplateDirty()).thenReturn(mock(ISqlTemplate.class));
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        when(engine.getDatabasePlatform()).thenReturn(platform);
        IDataService dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
        parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getParameterService()).thenReturn(parameterService);
        stagingManager = mock(IStagingManager.class);
        when(engine.getStagingManager()).thenReturn(stagingManager);
        nodeService = mock(INodeService.class);
        when(engine.getNodeService()).thenReturn(nodeService);
        dataExtractorService = new DataExtractorService(engine);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void selectFromSymDataSource_csvValuesAreExtracted_triggerRouterIsNotMarkedAsMissing() {
        ISqlReadCursor<Data> cursor = mock(ISqlReadCursor.class);
        TriggerHistory hist = new TriggerHistory("foo", "id", "id");
        hist.setTriggerId(AbstractFileParsingRouter.TRIGGER_ID_FILE_PARSER);
        Data data = new Data(1, "1", "1", DataEventType.INSERT, "foo", new Date(), hist, "default", null, null);
        when(cursor.next()).thenReturn(data);
        when(engine.getDataService().selectDataFor(any(), any(), eq(false))).thenReturn(cursor);
        SelectFromSymDataSource source = new SelectFromSymDataSource(engine, new OutgoingBatch(), new Node(), new Node(), new ProcessInfo(), false);
        assertTrue(source.next().equals(data));
    }

    @Test
    public void getStagedResourceForResume_nullBatch_returnsNull() {
        assertNull(dataExtractorService.getStagedResourceForResume(null));
    }

    @Test
    public void getStagedResourceForResume_delegatesToStagingManagerLookup() {
        OutgoingBatch batch = new OutgoingBatch();
        batch.setBatchId(123);
        batch.setNodeId("node1");
        IStagedResource resource = mock(IStagedResource.class);
        when(stagingManager.find(Constants.STAGING_CATEGORY_OUTGOING, batch.getStagedLocation(), batch.getBatchId())).thenReturn(resource);
        assertEquals(resource, dataExtractorService.getStagedResourceForResume(batch));
    }

    @Test
    public void getResumeEtagIfEligible_resumeDisabled_returnsNull() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(false);
        IStagedResource resource = mock(IStagedResource.class);
        assertNull(dataExtractorService.getResumeEtagIfEligible(new OutgoingBatch(), resource));
    }

    @Test
    public void getResumeEtagIfEligible_nullStagedResource_returnsNull() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        assertNull(dataExtractorService.getResumeEtagIfEligible(new OutgoingBatch(), null));
    }

    @Test
    public void getResumeEtagIfEligible_resourceNotDone_returnsNull() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getState()).thenReturn(State.CREATE);
        assertNull(dataExtractorService.getResumeEtagIfEligible(new OutgoingBatch(), resource));
    }

    @Test
    public void getResumeEtagIfEligible_resourceNotFileBacked_returnsNull() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getState()).thenReturn(State.DONE);
        when(resource.isFileResource()).thenReturn(false);
        assertNull(dataExtractorService.getResumeEtagIfEligible(new OutgoingBatch(), resource));
    }

    @Test
    public void getResumeEtagIfEligible_nodeNotFound_returnsNull() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getState()).thenReturn(State.DONE);
        when(resource.isFileResource()).thenReturn(true);
        OutgoingBatch batch = new OutgoingBatch();
        batch.setNodeId("node1");
        when(nodeService.findNode("node1", true)).thenReturn(null);
        assertNull(dataExtractorService.getResumeEtagIfEligible(batch, resource));
    }

    @Test
    public void getResumeEtagIfEligible_nodeVersionTooOld_returnsNull() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getState()).thenReturn(State.DONE);
        when(resource.isFileResource()).thenReturn(true);
        OutgoingBatch batch = new OutgoingBatch();
        batch.setNodeId("node1");
        Node node = new Node();
        node.setSymmetricVersion("3.17.0");
        when(nodeService.findNode("node1", true)).thenReturn(node);
        assertNull(dataExtractorService.getResumeEtagIfEligible(batch, resource));
    }

    @Test
    public void getResumeEtagIfEligible_allConditionsMet_returnsEtag() {
        when(parameterService.is(ParameterConstants.TRANSPORT_HTTP_RESUME_ENABLED)).thenReturn(true);
        IStagedResource resource = mock(IStagedResource.class);
        when(resource.getState()).thenReturn(State.DONE);
        when(resource.isFileResource()).thenReturn(true);
        when(resource.getGenerationTime()).thenReturn(1000L);
        when(resource.getSize()).thenReturn(2000L);
        OutgoingBatch batch = new OutgoingBatch();
        batch.setNodeId("node1");
        Node node = new Node();
        node.setSymmetricVersion("3.18.0");
        when(nodeService.findNode("node1", true)).thenReturn(node);
        StagedResourceETag etag = dataExtractorService.getResumeEtagIfEligible(batch, resource);
        assertNotNull(etag);
        assertEquals(1000L, etag.getGenerationTime());
        assertEquals(2000L, etag.getSize());
    }

    @Test
    public void writeBatchPreambleExtras_neitherStatsNorEtag_writesBufferUnchanged() throws IOException {
        String content = "\n" + CsvConstants.BATCH + ",1\ndata after batch line";
        char[] buffer = content.toCharArray();
        StringWriter stringWriter = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            boolean injected = dataExtractorService.writeBatchPreambleExtras(writer, buffer, buffer.length, "", new OutgoingBatch(), false, null);
            writer.flush();
            assertTrue(injected);
            assertEquals(content, stringWriter.toString());
        }
    }

    @Test
    public void writeBatchPreambleExtras_etagOnly_injectsEtagLineAfterBatchLine() throws IOException {
        String content = "\n" + CsvConstants.BATCH + ",1\ndata after batch line";
        char[] buffer = content.toCharArray();
        StringWriter stringWriter = new StringWriter();
        StagedResourceETag etag = new StagedResourceETag(1000L, 2000L);
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            boolean injected = dataExtractorService.writeBatchPreambleExtras(writer, buffer, buffer.length, "", new OutgoingBatch(), false, etag);
            writer.flush();
            assertTrue(injected);
            String result = stringWriter.toString();
            assertTrue(result.contains(CsvConstants.ETAG + "," + etag.toJson()));
            assertTrue(result.endsWith("data after batch line"));
        }
    }

    @Test
    public void writeBatchPreambleExtras_statsAndEtag_injectsBothInOrder() throws IOException {
        String content = "\n" + CsvConstants.BATCH + ",1\ndata after batch line";
        char[] buffer = content.toCharArray();
        StringWriter stringWriter = new StringWriter();
        StagedResourceETag etag = new StagedResourceETag(1000L, 2000L);
        OutgoingBatch batch = new OutgoingBatch();
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            boolean injected = dataExtractorService.writeBatchPreambleExtras(writer, buffer, buffer.length, "", batch, true, etag);
            writer.flush();
            assertTrue(injected);
            String result = stringWriter.toString();
            int statsIndex = result.indexOf(CsvConstants.STATS_COLUMNS);
            int etagIndex = result.indexOf(CsvConstants.ETAG + ",");
            assertTrue(statsIndex >= 0);
            assertTrue(etagIndex > statsIndex);
        }
    }

    @Test
    public void writeBatchPreambleExtras_noBatchLineFound_writesBufferUnchangedAndReturnsFalse() throws IOException {
        String content = "no batch marker in this text";
        char[] buffer = content.toCharArray();
        StringWriter stringWriter = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            boolean injected = dataExtractorService.writeBatchPreambleExtras(writer, buffer, buffer.length, "", new OutgoingBatch(), true,
                    new StagedResourceETag(1L, 2L));
            writer.flush();
            assertFalse(injected);
            assertEquals(content, stringWriter.toString());
        }
    }
}
