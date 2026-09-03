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
package org.jumpmind.symmetric.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.symmetric.model.StartupParameter.Source;
import org.jumpmind.symmetric.model.StartupParameter.Type;
import org.junit.jupiter.api.Test;

class StartupParameterTest {
    @Test
    void matchesDefault_trueWhenRawEqualsDefault() {
        StartupParameter matching = new StartupParameter("engine", "key", Type.STRING, "same", "same", Source.DEFAULT, false);
        StartupParameter differing = new StartupParameter("engine", "key", Type.STRING, "raw", "default", Source.SYMMETRIC_PROPERTIES_FILE, false);
        assertTrue(matching.matchesDefault());
        assertFalse(differing.matchesDefault());
    }

    @Test
    void asInt_malformedValue_fallsBackToZero() {
        StartupParameter parameter = new StartupParameter("engine", "key", Type.INT, "not-a-number", null, Source.SYMMETRIC_PROPERTIES_FILE, false);
        assertEquals(0, parameter.asInt());
    }

    @Test
    void asBoolean_valueOne_returnsTrue() {
        StartupParameter parameter = new StartupParameter("engine", "key", Type.BOOLEAN, "1", null, Source.SYMMETRIC_PROPERTIES_FILE, false);
        assertTrue(parameter.asBoolean());
    }
}
