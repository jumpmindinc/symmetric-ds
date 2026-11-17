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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.extract.SelectFromSymDataSource;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.transform.TransformColumn;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.route.AbstractFileParsingRouter;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.impl.TransformService.TransformTableNodeGroupLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DataExtractorServiceTest {
    private static final Node SELF = new Node("server", "server");
    protected ISymmetricEngine engine;
    private DataExtractorService service;

    @BeforeEach
    public void setUp() {
        engine = mock(ISymmetricEngine.class);
        when(engine.getTablePrefix()).thenReturn("sym");
        TriggerRouterService triggerRouterService = mock(TriggerRouterService.class);
        when(triggerRouterService.getTriggerRoutersByTriggerHist("target", false)).thenReturn(null);
        when(engine.getTriggerRouterService()).thenReturn(triggerRouterService);
        IParameterService parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        when(engine.getParameterService()).thenReturn(parameterService);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        when(symmetricDialect.getName()).thenReturn("H2");
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        IDatabasePlatform platform = mock(IDatabasePlatform.class);
        when(engine.getDatabasePlatform()).thenReturn(platform);
        when(symmetricDialect.getPlatform()).thenReturn(platform);
        when(symmetricDialect.getSqlReplacementTokens()).thenReturn(new HashMap<String, String>());
        IDataService dataService = mock(IDataService.class);
        when(engine.getDataService()).thenReturn(dataService);
        service = new DataExtractorService(engine);
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
    void testRemoveSmtpPasswordDecryptTransformIfExportingToSelf_withSmtpRule_removesOnlySmtpRule() {
        TransformTableNodeGroupLink heartbeatRule = transformRule("sym_node_host", "system_timestamp");
        TransformTableNodeGroupLink nodePasswordRule = transformRule("sym_node_security", "filter.onNodeSecurityRender(currentValue)");
        TransformTableNodeGroupLink smtpPasswordRule = transformRule("sym_parameter", "filter.onSmtpPasswordRender(currentValue)");
        List<TransformTableNodeGroupLink> transforms = new ArrayList<>(List.of(heartbeatRule, nodePasswordRule, smtpPasswordRule));
        service.removeSmtpPasswordDecryptTransformIfExportingToSelf(transforms, SELF, SELF);
        assertEquals(List.of(heartbeatRule, nodePasswordRule), transforms);
    }

    @Test
    void testRemoveSmtpPasswordDecryptTransformIfExportingToSelf_withoutSmtpRule_keepsAllRules() {
        TransformTableNodeGroupLink heartbeatRule = transformRule("sym_node_host", "system_timestamp");
        List<TransformTableNodeGroupLink> transforms = new ArrayList<>(List.of(heartbeatRule));
        service.removeSmtpPasswordDecryptTransformIfExportingToSelf(transforms, SELF, SELF);
        assertEquals(List.of(heartbeatRule), transforms);
    }

    @Test
    void testRemoveSmtpPasswordDecryptTransformIfExportingToSelf_withOtherParameterRule_keepsIt() {
        TransformTableNodeGroupLink otherParameterRule = transformRule("sym_parameter", "currentValue");
        List<TransformTableNodeGroupLink> transforms = new ArrayList<>(List.of(otherParameterRule));
        service.removeSmtpPasswordDecryptTransformIfExportingToSelf(transforms, SELF, SELF);
        assertEquals(List.of(otherParameterRule), transforms);
    }

    @Test
    void testRemoveSmtpPasswordDecryptTransformIfExportingToSelf_withColumnWithoutExpression_keepsRule() {
        TransformTableNodeGroupLink copyRule = transformRule("sym_parameter", null);
        List<TransformTableNodeGroupLink> transforms = new ArrayList<>(List.of(copyRule));
        service.removeSmtpPasswordDecryptTransformIfExportingToSelf(transforms, SELF, SELF);
        assertEquals(List.of(copyRule), transforms);
    }

    @Test
    void testRemoveSmtpPasswordDecryptTransformIfExportingToSelf_withOtherTargetNode_keepsSmtpRule() {
        TransformTableNodeGroupLink smtpPasswordRule = transformRule("sym_parameter", "filter.onSmtpPasswordRender(currentValue)");
        List<TransformTableNodeGroupLink> transforms = new ArrayList<>(List.of(smtpPasswordRule));
        service.removeSmtpPasswordDecryptTransformIfExportingToSelf(transforms, SELF, new Node("client", "client"));
        assertEquals(List.of(smtpPasswordRule), transforms);
    }

    private TransformTableNodeGroupLink transformRule(String tableName, String expression) {
        TransformColumn column = new TransformColumn("value", "value", false);
        column.setTransformExpression(expression);
        TransformTableNodeGroupLink rule = new TransformTableNodeGroupLink();
        rule.setSourceTableName(tableName);
        rule.setTargetTableName(tableName);
        rule.addTransformColumn(column);
        return rule;
    }
}
