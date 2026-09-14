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
package org.jumpmind.symmetric.transform;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.transform.JavaColumnTransform;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.io.data.transform.TransformPoint;
import org.jumpmind.symmetric.io.data.transform.TransformTable;
import org.jumpmind.symmetric.io.data.transform.TransformedData;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.ExtensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JavaColumnTransformTest {
    IExtensionService extensionService;
    IDatabasePlatform platform;
    DataContext context;

    @BeforeEach
    public void setUp() throws Exception {
        ISqlTransaction sqlTransaction = mock(ISqlTransaction.class);
        platform = mock(IDatabasePlatform.class);
        ISymmetricEngine engine = mock(ISymmetricEngine.class);
        IParameterService parameterService = mock(IParameterService.class);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        ISymmetricDialect dialect = mock(ISymmetricDialect.class);
        when(dialect.getPlatform()).thenReturn(platform);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(dialect);
        extensionService = new ExtensionService(engine);
        context = mock(DataContext.class);
        when(context.findTransaction()).thenReturn(sqlTransaction);
    }

    @Test
    public void testSimple() throws Exception {
        String javaCode = "return \"transValue\";";
        TransformColumn column = new TransformColumn("sColumn", "tColumn", false, "java", javaCode);
        TransformTable table = new TransformTable("sTable", "tTable", TransformPoint.LOAD, column);
        Map<String, String> sourceKeyValues = new HashMap<String, String>();
        Map<String, String> sourceValues = new HashMap<String, String>();
        sourceValues.put("sColumn", "aNewValue");
        Map<String, String> oldSourceValues = new HashMap<String, String>();
        oldSourceValues.put("sColumn", "anOldValue");
        TransformedData data = new TransformedData(table, DataEventType.INSERT, sourceKeyValues, oldSourceValues, sourceValues);
        JavaColumnTransform transform = new JavaColumnTransform();
        transform.setExtensionService(extensionService);
        String out = transform.transform(platform, context, column, data, sourceValues, "aNewValue", "anOldValue");
        assertEquals("transValue", out);
    }

    @Test
    public void testMultipleColumnsUseDifferentTransforms() throws Exception {
        TransformColumn idColumn = new TransformColumn("id", "id", true, "java", "return newValue + \"0\";");
        idColumn.setTransformId("transform1");
        TransformColumn testColumn = new TransformColumn("test", "test", false, "java", "return newValue + \"1\";");
        testColumn.setTransformId("transform1");
        TransformTable table = new TransformTable("sTable", "tTable", TransformPoint.LOAD, idColumn, testColumn);
        Map<String, String> sourceKeyValues = new HashMap<String, String>();
        Map<String, String> sourceValues = new HashMap<String, String>();
        sourceValues.put("id", "test");
        sourceValues.put("test", "test");
        Map<String, String> oldSourceValues = new HashMap<String, String>();
        TransformedData data = new TransformedData(table, DataEventType.INSERT, sourceKeyValues, oldSourceValues, sourceValues);
        DataContext realContext = new DataContext();
        JavaColumnTransform transform = new JavaColumnTransform();
        transform.setExtensionService(extensionService);
        String idResult = transform.transform(platform, realContext, idColumn, data, sourceValues, "test", null);
        String testResult = transform.transform(platform, realContext, testColumn, data, sourceValues, "test", null);
        assertEquals("test0", idResult);
        assertEquals("test1", testResult);
    }

    @Test
    public void testSameColumnDifferentIncludeOnUseDifferentTransforms() throws Exception {
        TransformColumn insertColumn = new TransformColumn("col", "col", false, "java", "return newValue + \"_insert\";");
        insertColumn.setTransformId("transform1");
        insertColumn.setIncludeOn(TransformColumn.IncludeOnType.INSERT);
        TransformColumn updateColumn = new TransformColumn("col", "col", false, "java", "return newValue + \"_update\";");
        updateColumn.setTransformId("transform1");
        updateColumn.setIncludeOn(TransformColumn.IncludeOnType.UPDATE);
        TransformTable table = new TransformTable("sTable", "tTable", TransformPoint.LOAD, insertColumn, updateColumn);
        Map<String, String> sourceKeyValues = new HashMap<String, String>();
        Map<String, String> sourceValues = new HashMap<String, String>();
        sourceValues.put("col", "val");
        Map<String, String> oldSourceValues = new HashMap<String, String>();
        TransformedData data = new TransformedData(table, DataEventType.INSERT, sourceKeyValues, oldSourceValues, sourceValues);
        DataContext realContext = new DataContext();
        JavaColumnTransform transform = new JavaColumnTransform();
        transform.setExtensionService(extensionService);
        String insertResult = transform.transform(platform, realContext, insertColumn, data, sourceValues, "val", null);
        String updateResult = transform.transform(platform, realContext, updateColumn, data, sourceValues, "val", null);
        assertEquals("val_insert", insertResult);
        assertEquals("val_update", updateResult);
    }

    @Test
    public void testInnerClass() throws Exception {
        String javaCode = "final DataContext ctx = context;"
                + "HashMap namedParams = new HashMap();"
                + "context.findTransaction().query(\"sql\", new ISqlRowMapper<Object>() {"
                + "        public Object mapRow(Row row) {"
                + "            ctx.put(\"a\", row.getString(\"b\"));"
                + "            return null;"
                + "        }"
                + "}, namedParams);"
                + "return \"transValue\";";
        TransformColumn column = new TransformColumn("sColumn", "tColumn", false, "java", javaCode);
        TransformTable table = new TransformTable("sTable", "tTable", TransformPoint.LOAD, column);
        Map<String, String> sourceKeyValues = new HashMap<String, String>();
        Map<String, String> sourceValues = new HashMap<String, String>();
        sourceValues.put("sColumn", "aNewValue");
        Map<String, String> oldSourceValues = new HashMap<String, String>();
        oldSourceValues.put("sColumn", "anOldValue");
        TransformedData data = new TransformedData(table, DataEventType.INSERT, sourceKeyValues, oldSourceValues, sourceValues);
        JavaColumnTransform transform = new JavaColumnTransform();
        transform.setExtensionService(extensionService);
        String out = transform.transform(platform, context, column, data, sourceValues, "aNewValue", "anOldValue");
        assertEquals("transValue", out);
    }
}
