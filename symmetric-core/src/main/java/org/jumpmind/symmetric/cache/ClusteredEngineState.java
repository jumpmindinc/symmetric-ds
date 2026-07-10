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

public enum ClusteredEngineState {
    STARTING("ENGINE_STARTING"), DISCOVERING("ENGINE_DISCOVERING"), RUNNING("ENGINE_RUNNING"), UPGRADING("ENGINE_UPGRADING"), FAILED("ENGINE_FAILED"), STOPPED(
            "ENGINE_STOPPED"), OFFLINE("ENGINE_OFFLINE");

    private final String value;

    ClusteredEngineState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    // The Discovering state applies to remote engines and is not considered active (until heartbeat is received).
    public boolean isActive() {
        return value.equals(STARTING.value) || value.equals(RUNNING.value) || value.equals(UPGRADING.value);
    }

    public static ClusteredEngineState fromValue(String value) {
        for (ClusteredEngineState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown engine state: " + value);
    }
}
