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

/**
 * An {@link IExtensionPoint} to examine an incoming registration request and redirect it to another node. For example, all nodes contact the root server for
 * registration, but they are redirected to a regional server that is closest to them. If this extension is unused, the default behavior is to check the
 * registration_redirect table for a matching externalId and redirect to the configured registration node.
 */
public interface IRegistrationRedirect extends IExtensionPoint {
    public String getRedirectionUrlFor(String externalId, String nodeGroupId);
}
