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
package org.jumpmind.symmetric.transport;

import java.io.BufferedWriter;
import java.io.OutputStream;

import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IConfigurationService;

public interface IOutgoingTransport {
    public BufferedWriter openWriter();

    public BufferedWriter getWriter();

    public OutputStream openStream();

    public void close();

    public boolean isOpen();

    /**
     * This returns a NodeChannels object containing two ChannelNodesMaps for suspended and ignored channels. In addition, it will optionally do a reservation
     * in the case of a Push request.
     * 
     * @param targetNode
     */
    public NodeChannels getSuspendIgnoreChannelLists(IConfigurationService configurationService, String queue, Node targetNode);
}