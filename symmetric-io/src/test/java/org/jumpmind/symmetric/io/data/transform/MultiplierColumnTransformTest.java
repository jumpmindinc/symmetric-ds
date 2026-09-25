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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiplierColumnTransformTest {
    private MultiplierColumnTransform multiplierColumnTransform;
    private IDatabasePlatform platform;
    private ISqlTemplate sqlTemplate;
    private TransformColumn transformColumn;
    private Map<String, String> sourceValues;

    @BeforeEach
    void setup() {
        multiplierColumnTransform = new MultiplierColumnTransform();
        platform = mock(IDatabasePlatform.class);
        sqlTemplate = mock(ISqlTemplate.class);
        when(platform.getSqlTemplate()).thenReturn(sqlTemplate);
        transformColumn = new TransformColumn();
        sourceValues = new HashMap<>();
        sourceValues.put("id", "1");
    }

    @Test
    void testGetName() {
        assertEquals("multiply", multiplierColumnTransform.getName());
    }

    @Test
    void testIsExtractColumnTransform() {
        assertTrue(multiplierColumnTransform.isExtractColumnTransform());
    }

    @Test
    void testIsLoadColumnTransform() {
        assertTrue(multiplierColumnTransform.isLoadColumnTransform());
    }

    @Test
    void testTransform_queriesUsingTransformExpressionAndSourceValues() throws IgnoreColumnException, IgnoreRowException {
        transformColumn.setTransformExpression("select val from tbl where id = :id");
        List<String> expected = Arrays.asList("a", "b");
        when(sqlTemplate.query(eq(transformColumn.getTransformExpression()), any(ISqlRowMapper.class), eq(sourceValues)))
                .thenReturn(expected);
        List<String> result = multiplierColumnTransform.transform(platform, null, transformColumn, null, sourceValues, null, null);
        assertEquals(expected, result);
        verify(sqlTemplate).query(eq(transformColumn.getTransformExpression()), any(ISqlRowMapper.class), eq(sourceValues));
    }
}
