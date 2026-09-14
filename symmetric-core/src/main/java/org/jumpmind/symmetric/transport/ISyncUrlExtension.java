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
package org.jumpmind.symmetric.transport;

import java.net.URI;

import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.transport.http.HttpBandwidthUrlSelector;

/**
 * This {@link IExtensionPoint} is used to select an appropriate URL based on the URI provided in the sync_url column of sym_node.
 * <p/>
 * To use this extension point configure the sync_url for a node with the protocol of ext://beanName. The beanName is the name you give the extension point in
 * the extension xml file.
 * 
 * @see HttpBandwidthUrlSelector
 *
 * 
 */
public interface ISyncUrlExtension extends IExtensionPoint {
    public String resolveUrl(URI url);
}