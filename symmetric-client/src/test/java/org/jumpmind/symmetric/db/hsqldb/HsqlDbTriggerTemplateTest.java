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
package org.jumpmind.symmetric.db.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HsqlDbTriggerTemplateTest {
    private HsqlDbTriggerTemplate triggerTemplate;

    @BeforeEach
    void setUp() {
        triggerTemplate = newTemplate();
    }

    @Test
    void testGetNewTriggerValue() {
        assertEquals("", triggerTemplate.getNewTriggerValue());
    }

    @Test
    void testGetOldTriggerValue() {
        assertEquals("", triggerTemplate.getOldTriggerValue());
    }

    @Test
    void testGetBlobColumnTemplate_matchesClobColumnTemplate() {
        assertNotNull(triggerTemplate.getBlobColumnTemplate());
        assertEquals(triggerTemplate.getClobColumnTemplate(), triggerTemplate.getBlobColumnTemplate());
    }

    @Test
    void testGetWrappedBlobColumnTemplate_isUnset() {
        assertNull(triggerTemplate.getWrappedBlobColumnTemplate());
    }

    @Test
    void testGetTimeColumnTemplate_isUnset() {
        assertNull(triggerTemplate.getTimeColumnTemplate());
    }

    @Test
    void testGetDateColumnTemplate_isUnset() {
        assertNull(triggerTemplate.getDateColumnTemplate());
    }

    @Test
    void testGetImageColumnTemplate_isUnset() {
        assertNull(triggerTemplate.getImageColumnTemplate());
    }

    @Test
    void testGetOtherColumnTemplate_isUnset() {
        assertNull(triggerTemplate.getOtherColumnTemplate());
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
        HsqlDbTriggerTemplate changed = newTemplate();
        changed.setBooleanColumnTemplate("something else");
        assertNotEquals(triggerTemplate.toHashedValue(), changed.toHashedValue());
    }

    private HsqlDbTriggerTemplate newTemplate() {
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class);
        return new HsqlDbTriggerTemplate(symmetricDialect);
    }
}
