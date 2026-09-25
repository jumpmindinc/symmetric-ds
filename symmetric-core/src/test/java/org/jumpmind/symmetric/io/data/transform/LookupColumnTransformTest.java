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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LookupColumnTransformTest {
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;
    private DataContext dataContext;
    private Map<String, String> sourceValues;
    private TransformColumn transformColumn;
    private TransformedData transformedData;
    private LookupColumnTransform lookupColumnTransform;

    @BeforeEach
    void setup() {
        lookupColumnTransform = new LookupColumnTransform();
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        dataContext = mock(DataContext.class);
        sourceValues = new HashMap<>();
        transformColumn = mock(TransformColumn.class);
        when(transformColumn.getTargetColumnName()).thenReturn("col1");
        when(transformColumn.getTransformId()).thenReturn("t1");
        transformedData = mock(TransformedData.class);
        when(transformedData.getTargetDmlType()).thenReturn(DataEventType.INSERT);
    }

    @Test
    void testGetName() {
        assertEquals("lookup", lookupColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertEquals(true, lookupColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertEquals(true, lookupColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_withBlankExpression_returnsNullValueWithoutQuerying() throws Exception {
        when(transformColumn.getTransformExpression()).thenReturn(null);
        NewAndOldValue result = lookupColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertNull(result.getNewValue());
        assertNull(result.getOldValue());
        verify(sqlTemplate, never()).query(any(), any(), anyMap());
    }

    @Test
    void testTransform_withPlatformSingleRow_returnsValue() throws Exception {
        String sql = "select x from t";
        when(transformColumn.getTransformExpression()).thenReturn(sql);
        when(sqlTemplate.query(eq(sql), any(), anyMap())).thenReturn(Collections.singletonList("val1"));
        NewAndOldValue result = lookupColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("val1", result.getNewValue());
    }

    @Test
    void testTransform_withPlatformMultipleRows_returnsFirstValue() throws Exception {
        String sql = "select x from t";
        when(transformColumn.getTransformExpression()).thenReturn(sql);
        when(sqlTemplate.query(eq(sql), any(), anyMap())).thenReturn(Arrays.asList("first", "second"));
        NewAndOldValue result = lookupColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("first", result.getNewValue());
    }

    @Test
    void testTransform_withPlatformNoRows_returnsNullValue() throws Exception {
        String sql = "select x from t";
        when(transformColumn.getTransformExpression()).thenReturn(sql);
        when(sqlTemplate.query(eq(sql), any(), anyMap())).thenReturn(Collections.emptyList());
        NewAndOldValue result = lookupColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertNull(result.getNewValue());
    }

    @Test
    void testTransform_withTransaction_usesTransactionInsteadOfPlatform() throws Exception {
        String sql = "select x from t";
        when(transformColumn.getTransformExpression()).thenReturn(sql);
        ISqlTransaction transaction = mock(ISqlTransaction.class);
        when(dataContext.findTransaction()).thenReturn(transaction);
        when(transaction.query(eq(sql), any(), anyMap())).thenReturn(Collections.singletonList("txVal"));
        NewAndOldValue result = lookupColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        assertEquals("txVal", result.getNewValue());
        verify(sqlTemplate, never()).query(any(), any(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTransform_withOldSourceValuesAndOldToken_includesOldParams() throws Exception {
        String sql = "select x from t where y = :OLD_COL";
        when(transformColumn.getTransformExpression()).thenReturn(sql);
        Map<String, String> oldSourceValues = new HashMap<>();
        oldSourceValues.put("col", "oldval");
        when(transformedData.getOldSourceValues()).thenReturn(oldSourceValues);
        when(sqlTemplate.query(eq(sql), any(), anyMap())).thenReturn(Collections.singletonList("val1"));
        lookupColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        ArgumentCaptor<Map<String, ?>> captor = ArgumentCaptor.forClass(Map.class);
        verify(sqlTemplate).query(eq(sql), any(), captor.capture());
        assertEquals("oldval", captor.getValue().get("OLD_COL"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTransform_withTargetValuesAndTrmToken_includesTrmParams() throws Exception {
        String sql = "select x from t where y = :TRM_COL";
        when(transformColumn.getTransformExpression()).thenReturn(sql);
        Map<String, String> targetValues = new HashMap<>();
        targetValues.put("col", "trmval");
        when(transformedData.getTargetValues()).thenReturn(targetValues);
        when(sqlTemplate.query(eq(sql), any(), anyMap())).thenReturn(Collections.singletonList("val1"));
        lookupColumnTransform.transform(platform, dataContext, transformColumn, transformedData, sourceValues, null, null);
        ArgumentCaptor<Map<String, ?>> captor = ArgumentCaptor.forClass(Map.class);
        verify(sqlTemplate).query(eq(sql), any(), captor.capture());
        assertEquals("trmval", captor.getValue().get("TRM_COL"));
    }

    @Test
    void testDoTokenReplacementOnSql_withCatalogAndSchemaTokens_replacesFromTriggerHistory() {
        Data csvData = mock(Data.class);
        TriggerHistory triggerHistory = mock(TriggerHistory.class);
        when(triggerHistory.getSourceCatalogName()).thenReturn("CAT1");
        when(triggerHistory.getSourceSchemaName()).thenReturn("SCH1");
        when(csvData.getTriggerHistory()).thenReturn(triggerHistory);
        when(dataContext.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA)).thenReturn(csvData);
        String result = lookupColumnTransform.doTokenReplacementOnSql(dataContext, "select * from $(sourceCatalogName).$(sourceSchemaName).t");
        assertEquals("select * from CAT1.SCH1.t", result);
    }

    @Test
    void testDoTokenReplacementOnSql_withBlankSql_returnsSqlUnchanged() {
        assertNull(lookupColumnTransform.doTokenReplacementOnSql(dataContext, null));
        assertEquals("", lookupColumnTransform.doTokenReplacementOnSql(dataContext, ""));
    }
}
