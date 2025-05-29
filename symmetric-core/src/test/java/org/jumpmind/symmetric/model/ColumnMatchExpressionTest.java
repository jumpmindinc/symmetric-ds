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
package org.jumpmind.symmetric.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.INodeService;
import org.junit.jupiter.api.Test;

public class ColumnMatchExpressionTest {
    @Test
    public void testExpressionUsingLineFeedsParsing() {
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        List<ColumnMatchExpression> expressions = ColumnMatchExpression.parse("one=two\ntwo=three\rthree!=:EXTERNAL_ID");
        assertEquals(3, expressions.size());
        assertEquals("two", expressions.get(0).getTokens()[1]);
        assertEquals("three", expressions.get(2).getTokens()[0]);
        assertEquals(false, expressions.get(2).hasEquals());
    }

    @Test
    public void testExpressionOrParsing() {
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        List<ColumnMatchExpression> expressions = ColumnMatchExpression.parse("one=door OR two=three or three!=:EXTERNAL_ID");
        assertEquals(3, expressions.size());
        assertEquals("door", expressions.get(0).getTokens()[1]);
        assertEquals("three", expressions.get(2).getTokens()[0]);
        assertEquals(false, expressions.get(2).hasEquals());
    }

    @Test
    public void testExpressionTickParsing() {
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        List<ColumnMatchExpression> expressions = ColumnMatchExpression.parse("one='two three' OR four='five'\r\nor six=isn't \r\n seven='can''t'" +
                " or eight='yall \n nine=' ten  ' or eleven  =  'twelve'  ");
        assertEquals(7, expressions.size());
        assertEquals("one", expressions.get(0).getTokens()[0]);
        assertEquals("two three", expressions.get(0).getTokens()[1]);
        assertEquals("four", expressions.get(1).getTokens()[0]);
        assertEquals("five", expressions.get(1).getTokens()[1]);
        assertEquals("six", expressions.get(2).getTokens()[0]);
        assertEquals("isn't", expressions.get(2).getTokens()[1]);
        assertEquals("seven", expressions.get(3).getTokens()[0]);
        assertEquals("can't", expressions.get(3).getTokens()[1]);
        assertEquals("eight", expressions.get(4).getTokens()[0]);
        assertEquals("'yall", expressions.get(4).getTokens()[1]);
        assertEquals("nine", expressions.get(5).getTokens()[0]);
        assertEquals(" ten  ", expressions.get(5).getTokens()[1]);
        assertEquals("eleven", expressions.get(6).getTokens()[0]);
        assertEquals("twelve", expressions.get(6).getTokens()[1]);
    }

    @Test
    public void testExpressionOrAndLineFeedsParsing() {
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        List<ColumnMatchExpression> expressions = ColumnMatchExpression.parse("one=two OR three=four\r\nor   five!=:EXTERNAL_ID");
        assertEquals(3, expressions.size());
        assertEquals("two", expressions.get(0).getTokens()[1]);
        assertEquals("three", expressions.get(1).getTokens()[0]);
        assertEquals("five", expressions.get(2).getTokens()[0]);
        assertEquals(false, expressions.get(2).hasEquals());
    }

    @Test
    public void testExpressionWithOrInColumnNameParsing() {
        ISymmetricEngine engine = mock(AbstractSymmetricEngine.class);
        INodeService nodeService = mock(INodeService.class);
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        doReturn(symmetricDialect).when(engine).getSymmetricDialect();
        doReturn(nodeService).when(engine).getNodeService();
        List<ColumnMatchExpression> expressions = ColumnMatchExpression.parse("ORDER_ID=:EXTERNAL_ID");
        assertEquals(1, expressions.size());
        assertEquals("ORDER_ID", expressions.get(0).getTokens()[0]);
        assertEquals(":EXTERNAL_ID", expressions.get(0).getTokens()[1]);
    }

    @Test
    public void testParseAndRunExpressionWithAllOperators() {
        List<ColumnMatchExpression> expressions = ColumnMatchExpression.parse("0=0 or 1!=1 or 2 contains 2 or 3 not contains 3 or 4 has 4"
                + " or 5 not has 5 or 6 starts with 6 or 7 not starts with 7 or 8 ends with 8 or 9 not ends with 9");
        assertEquals(10, expressions.size());
        assertTrue(expressions.get(0).hasEquals());
        assertFalse(expressions.get(0).run("test", "testEquals"));
        assertTrue(expressions.get(0).run(null, null));
        assertTrue(expressions.get(0).run("testEquals", "testEquals"));
        assertTrue(expressions.get(1).hasNotEquals());
        assertFalse(expressions.get(1).run("testNotEquals", "testNotEquals"));
        assertTrue(expressions.get(1).run(null, "testNotEquals"));
        assertTrue(expressions.get(1).run("test", "testNotEquals"));
        assertTrue(expressions.get(2).hasContains());
        assertFalse(expressions.get(2).run("test,test,test", "testContains"));
        assertFalse(expressions.get(2).run(null, null));
        assertTrue(expressions.get(2).run("test,testContains,test", "testContains"));
        assertTrue(expressions.get(3).hasNotContains());
        assertFalse(expressions.get(3).run("test,testNotContains", "testNotContains"));
        assertFalse(expressions.get(3).run(null, null));
        assertTrue(expressions.get(3).run("test,test", "testNotContains"));
        assertTrue(expressions.get(4).isHasHas());
        assertFalse(expressions.get(4).run("test", "testHas"));
        assertTrue(expressions.get(4).run(null, null));
        assertTrue(expressions.get(4).run("testHastest", "testHas"));
        assertTrue(expressions.get(5).isHasNotHas());
        assertFalse(expressions.get(5).run("testNotHastest", "testNotHas"));
        assertFalse(expressions.get(5).run(null, null));
        assertTrue(expressions.get(5).run("test", "testNotHas"));
        assertTrue(expressions.get(6).isHasStartsWith());
        assertFalse(expressions.get(6).run("testtestStartsWith", "testStartsWith"));
        assertTrue(expressions.get(6).run(null, null));
        assertTrue(expressions.get(6).run("testStartsWithtest", "testStartsWith"));
        assertTrue(expressions.get(7).isHasNotStartsWith());
        assertFalse(expressions.get(7).run("testNotStartsWithtest", "testNotStartsWith"));
        assertFalse(expressions.get(7).run(null, null));
        assertTrue(expressions.get(7).run("testtestNotStartsWith", "testNotStartsWith"));
        assertTrue(expressions.get(8).isHasEndsWith());
        assertFalse(expressions.get(8).run("testEndsWithtest", "testEndsWith"));
        assertTrue(expressions.get(8).run(null, null));
        assertTrue(expressions.get(8).run("testtestEndsWith", "testEndsWith"));
        assertTrue(expressions.get(9).isHasNotEndsWith());
        assertFalse(expressions.get(9).run("testtestNotEndsWith", "testNotEndsWith"));
        assertFalse(expressions.get(9).run(null, null));
        assertTrue(expressions.get(9).run("testNotEndsWithtest", "testNotEndsWith"));
    }
}
