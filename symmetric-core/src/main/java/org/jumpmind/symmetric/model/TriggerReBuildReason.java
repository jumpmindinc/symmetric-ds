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

/**
 * {@link TriggerHistory}
 */
public enum TriggerReBuildReason {
    NEW_TRIGGERS("N"), TABLE_SCHEMA_CHANGED("S"), TABLE_SYNC_CONFIGURATION_CHANGED("C"), FORCED("F"), TRIGGERS_MISSING("T"), TRIGGER_TEMPLATE_CHANGED(
            "E"), TRIGGER_HIST_MISSING("H");

    private String code;

    TriggerReBuildReason(String code) {
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }

    public static TriggerReBuildReason fromCode(String code) {
        if (code != null && code.length() > 0) {
            if (code.equals(NEW_TRIGGERS.code)) {
                return NEW_TRIGGERS;
            } else if (code.equals(TABLE_SCHEMA_CHANGED.code)) {
                return TABLE_SCHEMA_CHANGED;
            } else if (code.equals(TABLE_SYNC_CONFIGURATION_CHANGED.code)) {
                return TABLE_SYNC_CONFIGURATION_CHANGED;
            } else if (code.equals(FORCED.code)) {
                return FORCED;
            } else if (code.equals(TRIGGERS_MISSING.code)) {
                return TRIGGERS_MISSING;
            } else if (code.equals(TRIGGER_TEMPLATE_CHANGED.code)) {
                return TRIGGER_TEMPLATE_CHANGED;
            } else if (code.equals(TRIGGER_HIST_MISSING.code)) {
                return TRIGGER_HIST_MISSING;
            }
        }
        return null;
    }
}