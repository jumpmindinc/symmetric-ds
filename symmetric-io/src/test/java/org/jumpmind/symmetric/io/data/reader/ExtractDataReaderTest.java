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
package org.jumpmind.symmetric.io.data.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.jumpmind.db.model.Relation;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseNamesConstants;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtractDataReaderTest {
    private IDatabasePlatform platform;
    private IExtractDataReaderSource source;
    private Batch batch;
    private Table targetTable;
    private DataContext context;

    @BeforeEach
    void setUp() {
        platform = mock(IDatabasePlatform.class);
        when(platform.getName()).thenReturn(DatabaseNamesConstants.H2);
        source = mock(IExtractDataReaderSource.class);
        batch = new Batch(BatchType.EXTRACT, 1, "default", BinaryEncoding.BASE64, "store-1", "corp", false);
        targetTable = new Table("cat", "sch", "ITEM");
        context = new DataContext();
        when(source.getBatch()).thenReturn(batch);
        when(source.getTargetRelation()).thenReturn(targetTable);
        when(source.getSourceRelation()).thenReturn(targetTable);
    }

    @Test
    void testNextBatch_returnsBatchFromSource() {
        assertSame(batch, newReader().nextBatch());
    }

    @Test
    void testNextBatch_returnsNullWhenSourcesExhausted() {
        ExtractDataReader reader = newReader();
        reader.nextBatch();
        assertNull(reader.nextBatch());
    }

    @Test
    void testNextBatch_iteratesEachSource() {
        IExtractDataReaderSource second = mock(IExtractDataReaderSource.class);
        Batch secondBatch = new Batch(BatchType.EXTRACT, 2, "default", BinaryEncoding.BASE64, "store-1", "corp", false);
        when(second.getBatch()).thenReturn(secondBatch);
        ExtractDataReader reader = new ExtractDataReader(platform, Arrays.asList(source, second), platform);
        reader.open(context);
        assertSame(batch, reader.nextBatch());
        assertSame(secondBatch, reader.nextBatch());
        assertNull(reader.nextBatch());
    }

    @Test
    void testNextBatch_closesPreviousSource() {
        ExtractDataReader reader = newReader();
        reader.nextBatch();
        reader.nextBatch();
        verify(source).close();
    }

    @Test
    void testNextRelation_returnsTargetRelationFromSource() {
        when(source.next()).thenReturn(insert());
        ExtractDataReader reader = newReader();
        reader.nextBatch();
        assertSame(targetTable, reader.nextRelation());
    }

    @Test
    void testNextRelation_returnsNullAndCompletesBatchWhenSourceIsEmpty() {
        when(source.next()).thenReturn(null);
        ExtractDataReader reader = newReader();
        reader.nextBatch();
        assertNull(reader.nextRelation());
        assertTrue(batch.isComplete());
    }

    @Test
    void testNextRelation_returnsNullBeforeBatchIsOpened() {
        assertNull(newReader().nextRelation());
    }

    @Test
    void testNextRelation_substitutesNodeVariablesInCatalogAndSchema() {
        when(source.next()).thenReturn(insert());
        when(source.getTargetRelation()).thenReturn(new Table("$(sourceNodeId)_cat", "$(targetNodeId)_sch", "ITEM"));
        context.put("sourceNodeId", "store-1");
        context.put("targetNodeId", "corp");
        ExtractDataReader reader = newReader();
        reader.nextBatch();
        Relation relation = reader.nextRelation();
        assertEquals("store-1_cat", relation.getCatalog());
        assertEquals("corp_sch", relation.getSchema());
    }

    @Test
    void testNextData_returnsDataFromSource() {
        CsvData data = insert();
        when(source.next()).thenReturn(data);
        ExtractDataReader reader = newReader();
        reader.nextBatch();
        reader.nextRelation();
        assertSame(data, reader.nextData());
    }

    @Test
    void testNextData_returnsNullWhenSourceIsExhausted() {
        when(source.next()).thenReturn(insert(), (CsvData) null);
        ExtractDataReader reader = newReader();
        reader.nextBatch();
        reader.nextRelation();
        reader.nextData();
        assertNull(reader.nextData());
    }

    @Test
    void testClose_closesCurrentSource() {
        ExtractDataReader reader = newReader();
        reader.nextBatch();
        reader.close();
        verify(source).close();
    }

    @Test
    void testGetStatistics_isEmptyBeforeReading() {
        assertTrue(newReader().getStatistics().isEmpty());
    }

    @Test
    void testSubstituteVariables_leavesPlainStringAlone() {
        ExtractDataReader reader = newReader();
        assertEquals("plain", reader.substituteVariables("plain"));
    }

    @Test
    void testSubstituteVariables_withNull() {
        assertNull(newReader().substituteVariables(null));
    }

    private CsvData insert() {
        return new CsvData(DataEventType.INSERT, new String[] { "1", "widget" });
    }

    private ExtractDataReader newReader() {
        ExtractDataReader reader = new ExtractDataReader(platform, source, platform);
        reader.open(context);
        return reader;
    }
}
