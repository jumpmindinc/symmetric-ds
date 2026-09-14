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
package org.jumpmind.symmetric.common;

final public class ErrorConstants {
    private ErrorConstants() {
    }

    public static final String CONFLICT_STATE = "CONFLICT";
    public static final int CONFLICT_CODE = -999;
    public static final String FK_VIOLATION_STATE = "FK";
    public static final int FK_VIOLATION_CODE = -900;
    public static final String DEADLOCK_STATE = "DEADLOCK";
    public static final int DEADLOCK_CODE = -911;
    public static final String PROTOCOL_VIOLATION_STATE = "PROTOCOL";
    public static final int PROTOCOL_VIOLATION_CODE = -888;
    public static final String STAGE_ERROR_STATE = "STAGE";
    public static final int STAGE_ERROR_CODE = -808;
}