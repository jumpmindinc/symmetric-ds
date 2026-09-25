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
package org.jumpmind.symmetric.db.ingres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IngresSqlTriggerTemplateTest {
    private IngresSqlTriggerTemplate triggerTemplate;

    @BeforeEach
    void setUp() {
        triggerTemplate = newTemplate();
    }

    @Test
    void testGetNewTriggerValue() {
        assertEquals("new", triggerTemplate.getNewTriggerValue());
    }

    @Test
    void testGetOldTriggerValue() {
        assertEquals("old", triggerTemplate.getOldTriggerValue());
    }

    @Test
    void testGetBlobColumnTemplate() {
        assertEquals(
                "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || hex(varbinary($(tableAlias).\"$(columnName)\")) || '\"' end",
                triggerTemplate.getBlobColumnTemplate());
    }

    @Test
    void testGetBlobColumnTemplate_differsFromClobColumnTemplate() {
        assertNotEquals(triggerTemplate.getClobColumnTemplate(), triggerTemplate.getBlobColumnTemplate());
    }

    @Test
    void testGetWrappedBlobColumnTemplate_isUnset() {
        assertNull(triggerTemplate.getWrappedBlobColumnTemplate());
    }

    @Test
    void testGetTimeColumnTemplate() {
        assertEquals(
                "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || to_char($(tableAlias).\"$(columnName)\") || '\"' end",
                triggerTemplate.getTimeColumnTemplate());
    }

    @Test
    void testGetDateColumnTemplate() {
        assertEquals(
                "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || to_char($(tableAlias).\"$(columnName)\", 'yyyy-mm-dd hh:mi:ss') || '\"' end",
                triggerTemplate.getDateColumnTemplate());
    }

    @Test
    void testGetImageColumnTemplate_isUnset() {
        assertNull(triggerTemplate.getImageColumnTemplate());
    }

    @Test
    void testGetOtherColumnTemplate_matchesStringColumnTemplate() {
        assertEquals(
                "case when $(tableAlias).\"$(columnName)\" is null then '' else '\"' || replace(replace(varchar($(tableAlias).\"$(columnName)\",$(columnSize)),'\\','\\\\'),'\"','\\\"') || '\"' end",
                triggerTemplate.getOtherColumnTemplate());
    }

    @Test
    void testToHashedValue_isStableAcrossInstances() {
        assertEquals(newTemplate().toHashedValue(), triggerTemplate.toHashedValue());
    }

    @Test
    void testToHashedValue_isCachedAfterFirstCall() {
        int before = triggerTemplate.toHashedValue();
        triggerTemplate.setBooleanColumnTemplate("something else");
        assertEquals(before, triggerTemplate.toHashedValue());
    }

    @Test
    void testToHashedValue_reflectsTemplateChangeMadeBeforeFirstCall() {
        IngresSqlTriggerTemplate changed = newTemplate();
        changed.setBooleanColumnTemplate("something else");
        assertNotEquals(triggerTemplate.toHashedValue(), changed.toHashedValue());
    }

    private IngresSqlTriggerTemplate newTemplate() {
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        return new IngresSqlTriggerTemplate(symmetricDialect);
    }
}
