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
package org.jumpmind.symmetric.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClusteredEngineStateTest {
    @ParameterizedTest
    @EnumSource(ClusteredEngineState.class)
    void fromValue_eachStatesOwnValue_returnsSameState(ClusteredEngineState state) {
        assertSame(state, ClusteredEngineState.fromValue(state.getValue()));
    }

    @Test
    void fromValue_unknownValue_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ClusteredEngineState.fromValue("NOT_A_REAL_STATE"));
        assertEquals("Unknown engine state: NOT_A_REAL_STATE", ex.getMessage());
    }

    @Test
    void isActive_starting_returnsTrue() {
        assertTrue(ClusteredEngineState.STARTING.isActive());
    }

    @Test
    void isActive_running_returnsTrue() {
        assertTrue(ClusteredEngineState.RUNNING.isActive());
    }

    @Test
    void isActive_upgrading_returnsTrue() {
        assertTrue(ClusteredEngineState.UPGRADING.isActive());
    }

    @Test
    void isActive_discovering_returnsFalse() {
        assertFalse(ClusteredEngineState.DISCOVERING.isActive());
    }

    @Test
    void isActive_failed_returnsFalse() {
        assertFalse(ClusteredEngineState.FAILED.isActive());
    }

    @Test
    void isActive_stopped_returnsFalse() {
        assertFalse(ClusteredEngineState.STOPPED.isActive());
    }

    @Test
    void isActive_offline_returnsFalse() {
        assertFalse(ClusteredEngineState.OFFLINE.isActive());
    }
}
