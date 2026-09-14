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
package org.jumpmind.symmetric.ext;

import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.model.Node;

/**
 * This is an extension point that, when registered, will be called on a regular basis by the {@link HeartbeatJob}.
 */
public interface IHeartbeatListener extends IExtensionPoint {
    /**
     * Called periodically when the heartbeat job is enabled.
     * 
     * @param me
     *            A representation of this instance. It's statistics will be updated prior to calling this method.
     */
    public void heartbeat(Node me);

    /**
     * @return The number of seconds between heartbeat notifications.
     */
    public long getTimeBetweenHeartbeatsInSeconds();
}