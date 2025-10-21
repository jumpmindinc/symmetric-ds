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
package org.jumpmind.symmetric.transport.internal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.jumpmind.symmetric.model.ChannelMapWrapper;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.transport.IOutgoingTransport;

public class InternalOutgoingTransport implements IOutgoingTransport {
    BufferedWriter writer = null;
    OutputStream os = null;
    ChannelMapWrapper mapWrapper = null;
    boolean open = true;

    public InternalOutgoingTransport(OutputStream os, String encoding) throws UnsupportedEncodingException {
        this(os, new ChannelMapWrapper(), encoding);
    }

    public InternalOutgoingTransport(OutputStream os, ChannelMapWrapper mapWrapper, String encoding)
            throws UnsupportedEncodingException {
        this.os = os;
        this.writer = new BufferedWriter(new OutputStreamWriter(os, encoding == null ? Charset.defaultCharset().name() : encoding));
        this.mapWrapper = mapWrapper;
    }

    public InternalOutgoingTransport(BufferedWriter writer) {
        this.writer = writer;
        this.mapWrapper = new ChannelMapWrapper();
    }

    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
        }
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public OutputStream openStream() {
        return os;
    }

    public BufferedWriter openWriter() {
        return writer;
    }

    @Override
    public BufferedWriter getWriter() {
        return writer;
    }

    public ChannelMapWrapper getSuspendIgnoreChannelLists(IConfigurationService configurationService, String queue, Node targetNode) {
        return mapWrapper;
    }
}