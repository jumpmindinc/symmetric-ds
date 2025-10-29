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
package org.jumpmind.symmetric.transport.mock;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.model.NodeChannels;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.transport.IOutgoingTransport;

public class MockOutgoingTransport implements IOutgoingTransport {
    private ByteArrayOutputStream bos = new ByteArrayOutputStream();
    private StringWriter writer = new StringWriter();
    private BufferedWriter bWriter;

    public MockOutgoingTransport() {
    }

    public OutputStream openStream() {
        return bos;
    }

    public void close() {
        try {
            bWriter.flush();
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    public BufferedWriter openWriter() {
        bWriter = new BufferedWriter(writer);
        return bWriter;
    }

    @Override
    public BufferedWriter getWriter() {
        return bWriter;
    }

    public boolean isOpen() {
        return true;
    }

    public String toString() {
        try {
            bWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return writer.getBuffer().toString();
    }

    public NodeChannels getSuspendIgnoreChannelLists(IConfigurationService configurationService, String queue, Node targetNode) {
        return new NodeChannels();
    }
}