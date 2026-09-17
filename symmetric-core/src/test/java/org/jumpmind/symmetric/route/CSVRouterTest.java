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
package org.jumpmind.symmetric.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CSVRouterTest {
    private ISymmetricEngine engine;
    private CSVRouter router;

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class);
        router = new CSVRouter(engine);
    }

    @Test
    void testGetEngine() {
        assertSame(engine, router.getEngine());
    }

    @Test
    void testParse_skipsHeaderAndReturnsRows() {
        List<String> rows = router.parse(toStream("id,name\n1,widget\n2,gadget\n"), "item.csv", 0, 0);
        assertEquals(Arrays.asList("1,widget", "2,gadget"), rows);
    }

    @Test
    void testParse_capturesHeaderAsColumnNames() {
        router.parse(toStream("id,name\n1,widget\n"), "item.csv", 0, 0);
        assertEquals("id,name", router.getColumnNames());
    }

    @Test
    void testParse_resumesFromGivenLineNumber() {
        List<String> rows = router.parse(toStream("id,name\n1,widget\n2,gadget\n3,gizmo\n"), "item.csv", 3, 0);
        assertEquals(Arrays.asList("3,gizmo"), rows);
    }

    @Test
    void testParse_returnsNoRowsWhenLineNumberIsPastEnd() {
        assertTrue(router.parse(toStream("id,name\n1,widget\n"), "item.csv", 99, 0).isEmpty());
    }

    @Test
    void testParse_withHeaderOnly() {
        assertTrue(router.parse(toStream("id,name\n"), "item.csv", 0, 0).isEmpty());
    }

    @Test
    void testParse_withEmptyFile() {
        assertTrue(router.parse(toStream(""), "item.csv", 0, 0).isEmpty());
    }

    @Test
    void testGetColumnNames_isNullBeforeParsing() {
        assertNull(router.getColumnNames());
    }

    @Test
    void testParse_keepsEmbeddedCommasInRowText() {
        List<String> rows = router.parse(toStream("id,name\n1,\"widget, large\"\n"), "item.csv", 0, 0);
        assertEquals(Arrays.asList("1,\"widget, large\""), rows);
    }

    private InputStream toStream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
