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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.jumpmind.symmetric.io.data.transform.TransformColumn.IncludeOnType;
import org.junit.jupiter.api.Test;

class TransformColumnTest {
    @Test
    void testTransformIdConstructor_setsTransformId() {
        TransformColumn column = new TransformColumn("transform1");
        assertEquals("transform1", column.getTransformId());
    }

    @Test
    void testNoArgConstructor_hasDefaults() {
        TransformColumn column = new TransformColumn();
        assertNull(column.getSourceColumnName());
        assertFalse(column.isPk());
        assertEquals(CopyColumnTransform.NAME, column.getTransformType());
        assertEquals(IncludeOnType.ALL, column.getIncludeOn());
    }

    @Test
    void testThreeArgConstructor_setsSourceTargetAndPk() {
        TransformColumn column = new TransformColumn("SRC", "TGT", true);
        assertEquals("SRC", column.getSourceColumnName());
        assertEquals("TGT", column.getTargetColumnName());
        assertTrue(column.isPk());
    }

    @Test
    void testFiveArgConstructor_setsTransformTypeAndExpression() {
        TransformColumn column = new TransformColumn("SRC", "TGT", false, "substr", "1,5");
        assertEquals("SRC", column.getSourceColumnName());
        assertEquals("TGT", column.getTargetColumnName());
        assertFalse(column.isPk());
        assertEquals("substr", column.getTransformType());
        assertEquals("1,5", column.getTransformExpression());
    }

    @Test
    void testSetSourceColumnName_alsoUpdatesLowerCaseCache() {
        TransformColumn column = new TransformColumn();
        column.setSourceColumnName("MixedCase");
        assertEquals("MixedCase", column.getSourceColumnName());
        assertEquals("mixedcase", column.getSourceColumnNameLowerCase());
    }

    @Test
    void testSetSourceColumnName_withNull_clearsLowerCaseCache() {
        TransformColumn column = new TransformColumn();
        column.setSourceColumnName("MixedCase");
        column.setSourceColumnName(null);
        assertNull(column.getSourceColumnName());
        assertNull(column.getSourceColumnNameLowerCase());
    }

    @Test
    void testSetTargetColumnName_alsoUpdatesLowerCaseCache() {
        TransformColumn column = new TransformColumn();
        column.setTargetColumnName("MixedCase");
        assertEquals("MixedCase", column.getTargetColumnName());
        assertEquals("mixedcase", column.getTargetColumnNameLowerCase());
    }

    @Test
    void testSetTargetColumnName_withNull_clearsLowerCaseCache() {
        TransformColumn column = new TransformColumn();
        column.setTargetColumnName("MixedCase");
        column.setTargetColumnName(null);
        assertNull(column.getTargetColumnName());
        assertNull(column.getTargetColumnNameLowerCase());
    }

    @Test
    void testCompareTo_ordersByTransformOrder() {
        TransformColumn first = new TransformColumn();
        first.setTransformOrder(1);
        TransformColumn second = new TransformColumn();
        second.setTransformOrder(2);
        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
        assertEquals(0, first.compareTo(first));
    }

    @Test
    void testEquals_sameTransformIdAndTargetColumnNameAndIncludeOn_isEqual() {
        TransformColumn first = new TransformColumn("transform1");
        first.setTargetColumnName("TGT");
        TransformColumn second = new TransformColumn("transform1");
        second.setTargetColumnName("TGT");
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void testEquals_differentTargetColumnName_isNotEqual() {
        TransformColumn first = new TransformColumn("transform1");
        first.setTargetColumnName("TGT1");
        TransformColumn second = new TransformColumn("transform1");
        second.setTargetColumnName("TGT2");
        assertNotEquals(first, second);
    }

    @Test
    void testEquals_differentIncludeOn_isNotEqual() {
        TransformColumn first = new TransformColumn("transform1");
        first.setIncludeOn(IncludeOnType.INSERT);
        TransformColumn second = new TransformColumn("transform1");
        second.setIncludeOn(IncludeOnType.UPDATE);
        assertNotEquals(first, second);
    }

    @Test
    void testEquals_differentType_isNotEqual() {
        TransformColumn column = new TransformColumn("transform1");
        assertNotEquals("not a transform column", column);
    }

    @Test
    void testEquals_sameInstance_isEqual() {
        TransformColumn column = new TransformColumn("transform1");
        assertEquals(column, column);
    }

    @Test
    void testClone_copiesAllFieldsIntoNewInstance() {
        TransformColumn column = new TransformColumn("transform1");
        column.setSourceColumnName("SRC");
        column.setTargetColumnName("TGT");
        column.setPk(true);
        column.setTransformType("substr");
        column.setTransformExpression("1,5");
        column.setTransformOrder(3);
        column.setIncludeOn(IncludeOnType.UPDATE);
        column.setCreateTime(new Date(1000L));
        column.setLastUpdateTime(new Date(2000L));
        column.setLastUpdateBy("admin");
        TransformColumn clone = column.clone();
        assertNotSame(column, clone);
        assertEquals(column.getTransformId(), clone.getTransformId());
        assertEquals(column.getSourceColumnName(), clone.getSourceColumnName());
        assertEquals(column.getTargetColumnName(), clone.getTargetColumnName());
        assertEquals(column.isPk(), clone.isPk());
        assertEquals(column.getTransformType(), clone.getTransformType());
        assertEquals(column.getTransformExpression(), clone.getTransformExpression());
        assertEquals(column.getTransformOrder(), clone.getTransformOrder());
        assertEquals(column.getIncludeOn(), clone.getIncludeOn());
        assertEquals(column.getCreateTime(), clone.getCreateTime());
        assertNotSame(column.getCreateTime(), clone.getCreateTime());
        assertEquals(column.getLastUpdateTime(), clone.getLastUpdateTime());
        assertNotSame(column.getLastUpdateTime(), clone.getLastUpdateTime());
        assertEquals(column.getLastUpdateBy(), clone.getLastUpdateBy());
    }

    @Test
    void testClone_withNullDates_keepsNullDates() {
        TransformColumn column = new TransformColumn("transform1");
        TransformColumn clone = column.clone();
        assertNull(clone.getCreateTime());
        assertNull(clone.getLastUpdateTime());
    }

    @Test
    void testIncludeOnTypeDecode_mapsCodesToTypes() {
        assertEquals(IncludeOnType.INSERT, IncludeOnType.decode("I"));
        assertEquals(IncludeOnType.UPDATE, IncludeOnType.decode("U"));
        assertEquals(IncludeOnType.DELETE, IncludeOnType.decode("D"));
        assertEquals(IncludeOnType.ALL, IncludeOnType.decode("*"));
    }

    @Test
    void testIncludeOnTypeToDbValue_mapsTypesToCodes() {
        assertEquals("I", IncludeOnType.INSERT.toDbValue());
        assertEquals("U", IncludeOnType.UPDATE.toDbValue());
        assertEquals("D", IncludeOnType.DELETE.toDbValue());
        assertEquals("*", IncludeOnType.ALL.toDbValue());
    }
}
