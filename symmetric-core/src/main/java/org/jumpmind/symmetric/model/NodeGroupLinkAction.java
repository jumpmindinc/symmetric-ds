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
 * Identifies the action to take when the event watcher sees events in the event table.
 */
public enum NodeGroupLinkAction {
    P("pushes to", "push"), W("waits for pull from", "pull"), R("only routes to", "routes");

    private String description;
    private String shortName;

    NodeGroupLinkAction(String desc, String shortName) {
        this.description = desc;
        this.shortName = shortName;
    }

    public static NodeGroupLinkAction fromCode(String code) {
        if (code != null && code.length() > 0) {
            if (P.name().equals(code)) {
                return P;
            } else if (W.name().equals(code)) {
                return W;
            } else if (R.name().equals(code)) {
                return R;
            }
        }
        return null;
    }

    public static NodeGroupLinkAction fromShortName(String shortName) {
        if (shortName != null && shortName.length() > 0) {
            if (P.getShortName().equals(shortName)) {
                return P;
            } else if (W.getShortName().equals(shortName)) {
                return W;
            } else if (R.getShortName().equals(shortName)) {
                return R;
            }
        }
        return null;
    }

    public String getShortName() {
        return shortName;
    }

    @Override
    public String toString() {
        return description;
    }
}