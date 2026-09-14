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
package org.jumpmind.symmetric.io.data.writer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.util.FormatUtils;

public class ProtocolDataWriter extends AbstractProtocolDataWriter {
    private BufferedWriter writer;

    public ProtocolDataWriter(String sourceNodeId, Writer writer, boolean backwardsCompatible, boolean sendCaptureTime, boolean sendRowCaptureTime) {
        this(sourceNodeId, null, writer, backwardsCompatible, sendCaptureTime, sendRowCaptureTime);
    }

    public ProtocolDataWriter(String sourceNodeId, List<IProtocolDataWriterListener> listeners, Writer writer, boolean backwardsCompatible,
            boolean sendCaptureTime, boolean sendRowCaptureTime) {
        super(sourceNodeId, listeners, backwardsCompatible, sendCaptureTime, sendRowCaptureTime);
        if (writer instanceof BufferedWriter) {
            this.writer = (BufferedWriter) writer;
        } else {
            this.writer = new BufferedWriter(writer);
        }
    }

    @Override
    protected void endBatch(Batch batch) {
        super.endBatch(batch);
        try {
            writer.flush();
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    @Override
    protected void notifyEndBatch(Batch batch, IProtocolDataWriterListener listener) {
    }

    @Override
    protected void print(Batch batch, String data) {
        try {
            if (data != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Writing data: {}", FormatUtils.abbreviateForLogging(data));
                }
                writer.write(data);
            }
        } catch (IOException e) {
            throw new IoException(e);
        }
    }
}
