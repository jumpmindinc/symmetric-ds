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

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.JdbcDatabasePlatformFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class SqlScriptTest {
    @Test
    public void testSimpleSqlScript() throws Exception {
        SingleConnectionDataSource ds = getDataSource();
        IDatabasePlatform platform = JdbcDatabasePlatformFactory.getInstance().create(ds, new SqlTemplateSettings(), true, false);
        SqlScript script = new SqlScript(getClass().getResource("sqlscript-simple.sql"), platform.getSqlTemplate());
        script.execute();
        JdbcTemplate template = new JdbcTemplate(ds);
        assertEquals(2, (int) template.queryForObject("select count(*) from test", Integer.class));
        assertEquals(3, template.queryForObject("select test from test where test_id=2", String.class).split("\r\n|\r|\n").length);
        ds.destroy();
    }

    @Test
    void testConstructor_urlWithFailOnErrorFlag_propagatesFailOnErrorAndDefaultDelimiter() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        SqlScript script = new SqlScript(getClass().getResource("sqlscript-simple.sql"), sqlTemplate, false);
        script.execute();
        ArgumentCaptor<Boolean> failOnErrorCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(sqlTemplate).update(anyBoolean(), failOnErrorCaptor.capture(), anyBoolean(), anyBoolean(), anyInt(), any(),
                any(ISqlStatementSource.class));
        assertFalse(failOnErrorCaptor.getValue());
    }

    @Test
    void testConstructor_urlWithCustomDelimiter_usesGivenDelimiterInsteadOfSemicolon() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        List<String> statements = new ArrayList<>();
        when(sqlTemplate.update(anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), any(), any(ISqlStatementSource.class)))
                .thenAnswer(invocation -> drainStatements(invocation, statements));
        SqlScript script = new SqlScript(getClass().getResource("sqlscript-simple.sql"), sqlTemplate, "~");
        script.execute();
        assertEquals(1, statements.size());
    }

    @Test
    void testConstructor_urlFullOverload_propagatesFailOnErrorDelimiterAndReplacementTokens() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        List<String> statements = new ArrayList<>();
        ArgumentCaptor<Boolean> failOnErrorCaptor = ArgumentCaptor.forClass(Boolean.class);
        when(sqlTemplate.update(anyBoolean(), failOnErrorCaptor.capture(), anyBoolean(), anyBoolean(), anyInt(), any(),
                any(ISqlStatementSource.class))).thenAnswer(invocation -> drainStatements(invocation, statements));
        SqlScript script = new SqlScript(getClass().getResource("sqlscript-simple.sql"), sqlTemplate, false, ";", Map.of("TEST", "REPLACED"));
        script.execute();
        assertFalse(failOnErrorCaptor.getValue());
        assertTrue(statements.get(0).contains("REPLACED"));
    }

    @Test
    void testConstructor_stringWithReplacementTokens_defaultsFailOnDropAndFailOnSequenceCreateToTrue() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        List<String> statements = new ArrayList<>();
        ArgumentCaptor<Boolean> failOnDropCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> failOnSeqCaptor = ArgumentCaptor.forClass(Boolean.class);
        when(sqlTemplate.update(anyBoolean(), eq(false), failOnDropCaptor.capture(), failOnSeqCaptor.capture(), anyInt(), any(),
                any(ISqlStatementSource.class))).thenAnswer(invocation -> drainStatements(invocation, statements));
        SqlScript script = new SqlScript("select * from FOO;", sqlTemplate, false, Map.of("FOO", "test_table"));
        script.execute();
        assertTrue(failOnDropCaptor.getValue());
        assertTrue(failOnSeqCaptor.getValue());
        assertEquals("select * from test_table", statements.get(0));
    }

    @Test
    void testConstructor_stringWithExplicitFailOnDropAndSequenceCreate_propagatesBothFlags() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        SqlScript script = new SqlScript("select 1;", sqlTemplate, true, false, false, ";", null);
        script.execute();
        ArgumentCaptor<Boolean> failOnDropCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> failOnSeqCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(sqlTemplate).update(anyBoolean(), anyBoolean(), failOnDropCaptor.capture(), failOnSeqCaptor.capture(), anyInt(), any(), any());
        assertFalse(failOnDropCaptor.getValue());
        assertFalse(failOnSeqCaptor.getValue());
    }

    @Test
    void testConstructor_stringWithTriggersContainJavaTrue_treatsTriggerBodyAsSingleStatementWhenRead() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        List<String> statements = new ArrayList<>();
        when(sqlTemplate.update(anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), any(), any(ISqlStatementSource.class)))
                .thenAnswer(invocation -> drainStatements(invocation, statements));
        String triggerScript = "CREATE TRIGGER trg1\nBEGIN\n  do_something();\nEND;\n";
        SqlScript script = new SqlScript(triggerScript, sqlTemplate, true, true, true, true, ";", null);
        script.execute();
        assertEquals(1, statements.size());
        assertEquals("CREATE TRIGGER trg1\nBEGIN\n  do_something();\nEND", statements.get(0));
    }

    @Test
    void testConstructor_readerOverload_defaultsFailOnDropAndFailOnSequenceCreateToTrue() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        SqlScript script = new SqlScript(new StringReader("select 1;"), sqlTemplate, true, ";", null);
        script.execute();
        ArgumentCaptor<Boolean> failOnDropCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> failOnSeqCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(sqlTemplate).update(anyBoolean(), anyBoolean(), failOnDropCaptor.capture(), failOnSeqCaptor.capture(), anyInt(), any(), any());
        assertTrue(failOnDropCaptor.getValue());
        assertTrue(failOnSeqCaptor.getValue());
    }

    @Test
    void testExecute_withAutoCommitFalseAndFailOnDropFalse_overridesAutoCommitToTrue() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        SqlScript script = new SqlScript("select 1;", sqlTemplate, true, false, true, ";", null);
        script.execute(false);
        ArgumentCaptor<Boolean> autoCommitCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(sqlTemplate).update(autoCommitCaptor.capture(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), any(), any());
        assertTrue(autoCommitCaptor.getValue());
    }

    @Test
    void testExecute_withAllFlagsTrueAndAutoCommitFalse_keepsAutoCommitFalse() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        SqlScript script = new SqlScript("select 1;", sqlTemplate, true, true, true, ";", null);
        script.execute(false);
        ArgumentCaptor<Boolean> autoCommitCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(sqlTemplate).update(autoCommitCaptor.capture(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), any(), any());
        assertFalse(autoCommitCaptor.getValue());
    }

    @Test
    void testExecute_whenSqlTemplateThrows_stillClosesScriptReader() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        when(sqlTemplate.update(anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), any(), any()))
                .thenThrow(new SqlException("boom"));
        CloseTrackingReader reader = new CloseTrackingReader("select 1;");
        SqlScript script = new SqlScript(reader, sqlTemplate, true, ";", null);
        assertThrows(SqlException.class, script::execute);
        assertTrue(reader.closed);
    }

    @Test
    void testCalculateTotalStatements_countsStatementsSeparatedByDelimiter() {
        int count = SqlScript.calculateTotalStatements("select 1;\nselect 2;\nselect 3;\n", ";", false);
        assertEquals(3, count);
    }

    @Test
    void testCalculateTotalStatements_withTriggersContainJavaTrue_countsTriggerBodyAsOneStatement() {
        String triggerScript = "CREATE TRIGGER trg1\nBEGIN\n  do_something();\nEND;\nselect 1;\n";
        int count = SqlScript.calculateTotalStatements(triggerScript, ";", true);
        assertEquals(2, count);
    }

    @Test
    void testSetLineDeliminator_changesDelimiterUsedByUnderlyingReader() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        List<String> statements = new ArrayList<>();
        when(sqlTemplate.update(anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), any(), any(ISqlStatementSource.class)))
                .thenAnswer(invocation -> drainStatements(invocation, statements));
        SqlScript script = new SqlScript("select 1 GO\nselect 2 GO\n", sqlTemplate, true, true, true, ";", null);
        script.setLineDeliminator("GO");
        script.execute();
        assertEquals(2, statements.size());
        assertEquals("select 1", statements.get(0));
        assertEquals("select 2", statements.get(1));
    }

    @Test
    void testSetListener_passesListenerToSqlTemplateUpdate() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        ISqlResultsListener listener = mock(ISqlResultsListener.class);
        SqlScript script = new SqlScript("select 1;", sqlTemplate, true, true, true, ";", null);
        script.setListener(listener);
        script.execute();
        verify(sqlTemplate).update(anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), eq(listener), any());
    }

    @Test
    void testSetCommitRate_updatesCommitRateUsedByUpdate() {
        ISqlTemplate sqlTemplate = mock(ISqlTemplate.class);
        SqlScript script = new SqlScript("select 1;", sqlTemplate, true, true, true, ";", null);
        script.setCommitRate(5);
        assertEquals(5, script.getCommitRate());
        script.execute();
        verify(sqlTemplate).update(anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), eq(5), any(), any());
    }

    private SingleConnectionDataSource getDataSource() throws Exception {
        Class.forName("org.h2.Driver");
        Connection c = DriverManager.getConnection("jdbc:h2:mem:sqlscript");
        return new SingleConnectionDataSource(c, true);
    }

    private static int drainStatements(InvocationOnMock invocation, List<String> statements) {
        ISqlStatementSource source = invocation.getArgument(6);
        String statement;
        while ((statement = source.readSqlStatement()) != null) {
            statements.add(statement);
        }
        return 0;
    }

    private static class CloseTrackingReader extends StringReader {
        boolean closed = false;

        CloseTrackingReader(String s) {
            super(s);
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }
}