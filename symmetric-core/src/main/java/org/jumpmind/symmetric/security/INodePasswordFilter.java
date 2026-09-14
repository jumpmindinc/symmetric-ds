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
package org.jumpmind.symmetric.security;

import org.jumpmind.extension.IExtensionPoint;

/**
 * Used to intercept the saving and rendering of the node password.
 */
public interface INodePasswordFilter extends IExtensionPoint {
    /**
     * Called on when the node security password is being saved to the DB.
     * 
     * @param password
     *            - The password being saved
     */
    public String onNodeSecuritySave(String password, String nodeId);

    /**
     * Called on when the password has been selected from the DB.
     * 
     * @param password
     *            - The password to be used
     */
    public String onNodeSecurityRender(String password, String nodeId);
}