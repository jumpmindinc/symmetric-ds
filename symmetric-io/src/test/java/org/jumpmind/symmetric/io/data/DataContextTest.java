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
package org.jumpmind.symmetric.io.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.io.data.writer.DefaultDatabaseWriter;
import org.jumpmind.symmetric.io.data.writer.NestedDataWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataContextTest {
    private static final String TABLE_PREFIX = "sym";
    private DataContext context;
    private DefaultDatabaseWriter databaseWriter;
    private ISqlTransaction transaction;

    @BeforeEach
    void setUp() {
        context = new DataContext();
        databaseWriter = mock(DefaultDatabaseWriter.class);
        transaction = mock(ISqlTransaction.class);
    }

    @Test
    void testConstructor_withNoArguments() {
        assertNull(context.getBatch());
        assertNull(context.getReader());
        assertNull(context.getWriter());
        assertTrue(context.getParsedRelations().isEmpty());
        assertNull(context.getLastParsedRelation());
    }

    @Test
    void testConstructor_withBatch() {
        Batch batch = new Batch();
        assertSame(batch, new DataContext(batch).getBatch());
    }

    @Test
    void testConstructor_withReader() {
        IDataReader reader = mock(IDataReader.class);
        assertSame(reader, new DataContext(reader).getReader());
    }

    @Test
    void testSetReader() {
        IDataReader reader = mock(IDataReader.class);
        context.setReader(reader);
        assertSame(reader, context.getReader());
    }

    @Test
    void testSetWriter() {
        IDataWriter writer = mock(IDataWriter.class);
        context.setWriter(writer);
        assertSame(writer, context.getWriter());
    }

    @Test
    void testSetBatch() {
        Batch batch = new Batch();
        context.setBatch(batch);
        assertSame(batch, context.getBatch());
    }

    @Test
    void testSetData() {
        CsvData data = new CsvData(DataEventType.INSERT);
        context.setData(data);
        assertSame(data, context.getData());
    }

    @Test
    void testSetRelation() {
        Table table = new Table("item");
        context.setRelation(table);
        assertSame(table, context.getRelation());
    }

    @Test
    void testSetLastError() {
        Throwable error = new IllegalStateException("failed");
        context.setLastError(error);
        assertSame(error, context.getLastError());
    }

    @Test
    void testSetLastParsedRelation() {
        Table table = new Table("item");
        context.setLastParsedRelation(table);
        assertSame(table, context.getLastParsedRelation());
    }

    @Test
    void testGetParsedRelations() {
        Table table = new Table("item");
        context.getParsedRelations().put("item", table);
        assertEquals(1, context.getParsedRelations().size());
        assertSame(table, context.getParsedRelations().get("item"));
    }

    @Test
    void testFindTransaction_withDatabaseWriter() {
        when(databaseWriter.getTransaction()).thenReturn(transaction);
        context.setWriter(databaseWriter);
        assertSame(transaction, context.findTransaction());
    }

    @Test
    void testFindTransaction_withNestedWriter() {
        when(databaseWriter.getTransaction()).thenReturn(transaction);
        context.setWriter(nestedWriterHolding(databaseWriter));
        assertSame(transaction, context.findTransaction());
    }

    @Test
    void testFindTransaction_withNestedWriterHoldingNoDatabaseWriter() {
        context.setWriter(nestedWriterHolding(null));
        assertNull(context.findTransaction());
    }

    @Test
    void testFindTransaction_withUnrelatedWriter() {
        context.setWriter(mock(IDataWriter.class));
        assertNull(context.findTransaction());
    }

    @Test
    void testFindTransaction_withNoWriter() {
        assertNull(context.findTransaction());
    }

    @Test
    void testFindTargetTransaction_withDatabaseWriter() {
        when(databaseWriter.getTargetTransaction()).thenReturn(transaction);
        context.setWriter(databaseWriter);
        assertSame(transaction, context.findTargetTransaction());
    }

    @Test
    void testFindTargetTransaction_withNestedWriter() {
        when(databaseWriter.getTargetTransaction()).thenReturn(transaction);
        context.setWriter(nestedWriterHolding(databaseWriter));
        assertSame(transaction, context.findTargetTransaction());
    }

    @Test
    void testFindTargetTransaction_withNestedWriterHoldingNoDatabaseWriter() {
        context.setWriter(nestedWriterHolding(null));
        assertNull(context.findTargetTransaction());
    }

    @Test
    void testFindTargetTransaction_withNoWriter() {
        assertNull(context.findTargetTransaction());
    }

    @Test
    void testFindSymmetricTransaction_withDatabaseWriter() {
        when(databaseWriter.getTransaction(TABLE_PREFIX)).thenReturn(transaction);
        context.setWriter(databaseWriter);
        assertSame(transaction, context.findSymmetricTransaction(TABLE_PREFIX));
    }

    @Test
    void testFindSymmetricTransaction_withNestedWriter() {
        when(databaseWriter.getTransaction(TABLE_PREFIX)).thenReturn(transaction);
        context.setWriter(nestedWriterHolding(databaseWriter));
        assertSame(transaction, context.findSymmetricTransaction(TABLE_PREFIX));
    }

    @Test
    void testFindSymmetricTransaction_withNestedWriterHoldingNoDatabaseWriter() {
        context.setWriter(nestedWriterHolding(null));
        assertNull(context.findSymmetricTransaction(TABLE_PREFIX));
    }

    @Test
    void testFindSymmetricTransaction_withNoWriter() {
        assertNull(context.findSymmetricTransaction(TABLE_PREFIX));
    }

    private NestedDataWriter nestedWriterHolding(DefaultDatabaseWriter nestedWriter) {
        NestedDataWriter writer = mock(NestedDataWriter.class);
        when(writer.<DefaultDatabaseWriter> getNestedWriterOfType(DefaultDatabaseWriter.class)).thenReturn(nestedWriter);
        return writer;
    }
}
