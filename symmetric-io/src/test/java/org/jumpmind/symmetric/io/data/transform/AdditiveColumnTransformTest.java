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
package org.jumpmind.symmetric.io.data.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.DatabaseInfo;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdditiveColumnTransformTest {
    private AdditiveColumnTransform additiveColumnTransform;
    private IDatabasePlatform platform;
    private IDdlBuilder ddlBuilder;
    private DatabaseInfo databaseInfo;
    private DataContext dataContext;
    private ISqlTransaction transaction;
    private TransformTable transformTable;
    private TransformColumn transformColumn;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        additiveColumnTransform = new AdditiveColumnTransform();
        platform = mock(IDatabasePlatform.class);
        ddlBuilder = mock(IDdlBuilder.class);
        databaseInfo = mock(DatabaseInfo.class);
        when(platform.getDdlBuilder()).thenReturn(ddlBuilder);
        when(platform.getDatabaseInfo()).thenReturn(databaseInfo);
        when(databaseInfo.getDelimiterToken()).thenReturn("\"");
        dataContext = mock(DataContext.class);
        transaction = mock(ISqlTransaction.class);
        when(dataContext.findTransaction()).thenReturn(transaction);
        when(dataContext.getBatch()).thenReturn(new Batch(BatchType.LOAD, 1, "default", BinaryEncoding.NONE, "source", "target", false));
        transformTable = new TransformTable();
        transformTable.setTargetTableName("target_table");
        transformColumn = new TransformColumn();
        transformColumn.setTargetColumnName("target_col");
        sourceValues = new HashMap<>();
    }

    @Test
    void testGetName() {
        assertEquals("additive", additiveColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform_returnsFalse() {
        assertFalse(additiveColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform_returnsTrue() {
        assertTrue(additiveColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testGetFullyQualifiedTableName_withDelimitedIdentifiersAndSchemaAndCatalog_quotesAndPrefixes() {
        when(ddlBuilder.isDelimitedIdentifierModeOn()).thenReturn(true);
        String result = additiveColumnTransform.getFullyQualifiedTableName(platform, "my_schema", "my_catalog", "my_table");
        assertEquals("my_catalog.my_schema.\"my_table\"", result);
    }

    @Test
    void testGetFullyQualifiedTableName_withoutDelimitedIdentifiersOrSchemaOrCatalog_returnsTableNameOnly() {
        when(ddlBuilder.isDelimitedIdentifierModeOn()).thenReturn(false);
        String result = additiveColumnTransform.getFullyQualifiedTableName(platform, null, null, "my_table");
        assertEquals("my_table", result);
    }

    @Test
    void testTransform_whenRelationNotFound_throwsIgnoreColumnException() {
        when(platform.getRelationFromCache(any(), any(), any(), eq(false))).thenReturn(null);
        TransformedData data = new TransformedData(transformTable, DataEventType.INSERT, null, null, sourceValues);
        assertThrows(IgnoreColumnException.class, () -> additiveColumnTransform.transform(
                platform, dataContext, transformColumn, data, sourceValues, "10", "4"));
    }

    @Test
    void testTransform_whenTargetColumnNotFound_throwsIgnoreColumnException() {
        Table relation = new Table();
        relation.addColumn(new Column("id"));
        when(platform.getRelationFromCache(any(), any(), any(), eq(false))).thenReturn(relation);
        TransformedData data = new TransformedData(transformTable, DataEventType.INSERT, null, null, sourceValues);
        assertThrows(IgnoreColumnException.class, () -> additiveColumnTransform.transform(
                platform, dataContext, transformColumn, data, sourceValues, "10", "4"));
    }

    @Test
    void testTransform_whenNoExistingRowUpdated_returnsComputedDelta() throws IgnoreColumnException, IgnoreRowException {
        Table relation = new Table();
        relation.addColumn(new Column("target_col"));
        when(platform.getRelationFromCache(any(), any(), any(), eq(false))).thenReturn(relation);
        when(transaction.prepareAndExecute(anyString(), nullable(Object[].class))).thenReturn(0);
        transformColumn.setTransformExpression("2");
        String result = additiveColumnTransform.transform(platform, dataContext, transformColumn, new TransformedData(transformTable,
                DataEventType.INSERT, null, null, sourceValues), sourceValues, "10", "4");
        assertEquals("12", result);
    }

    @Test
    void testTransform_whenExistingRowUpdated_throwsIgnoreColumnException() {
        Table relation = new Table();
        relation.addColumn(new Column("target_col"));
        when(platform.getRelationFromCache(any(), any(), any(), eq(false))).thenReturn(relation);
        when(transaction.prepareAndExecute(anyString(), nullable(Object[].class))).thenReturn(1);
        TransformedData data = new TransformedData(transformTable, DataEventType.INSERT, null, null, sourceValues);
        assertThrows(IgnoreColumnException.class, () -> additiveColumnTransform.transform(
                platform, dataContext, transformColumn, data, sourceValues, "10", "4"));
    }
}
