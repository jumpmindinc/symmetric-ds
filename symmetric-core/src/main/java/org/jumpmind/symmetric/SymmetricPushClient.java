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
package org.jumpmind.symmetric;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.db.util.BinaryEncoding;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.io.data.writer.ProtocolDataWriter;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.transport.http.HttpOutgoingTransport;
import org.jumpmind.symmetric.transport.http.HttpTransportManager;
import org.jumpmind.symmetric.web.WebConstants;
import org.jumpmind.util.AppUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SymmetricPushClient {
    private static final Logger log = LoggerFactory.getLogger(SymmetricPushClient.class);
    protected String nodeId;
    protected String securityToken;
    protected String syncUrl;
    protected HttpOutgoingTransport transport;
    protected ProtocolDataWriter writer;
    protected Batch batch;

    public SymmetricPushClient(String nodeId, String securityToken, String syncUrl) {
        super();
        this.nodeId = nodeId;
        this.securityToken = securityToken;
        this.syncUrl = syncUrl;
        batch = new Batch(BatchType.EXTRACT, Constants.VIRTUAL_BATCH_FOR_REGISTRATION, "default",
                BinaryEncoding.BASE64, nodeId, null, false);
    }

    public void open() {
        try {
            transport = new HttpOutgoingTransport(new HttpTransportManager(), URI.create(buildUrl()).toURL(), 30000, 30000, true, 0, -1, null,
                    null, false, -1, false);
            writer = new ProtocolDataWriter(nodeId, transport.openWriter(), false, false, false);
            writer.start(batch);
        } catch (Exception ex) {
            throw new IoException(ex);
        }
    }

    public BatchAck close() {
        try {
            writer.end(batch, false);
            BufferedReader reader = transport.readResponse();
            String ackString = reader.readLine();
            String ackExtendedString = reader.readLine();
            log.debug("Reading ack: {}", ackString);
            log.debug("Reading extend ack: {}", ackExtendedString);
            List<BatchAck> batchAcks = new HttpTransportManager().readAcknowledgement(ackString,
                    ackExtendedString);
            if (batchAcks.size() > 0) {
                return batchAcks.get(0);
            } else {
                return null;
            }
        } catch (IOException ex) {
            throw new IoException(ex);
        } finally {
            transport.close();
        }
    }

    public void insert(Table table, String[] data) {
        writer.start(table);
        writer.write(new CsvData(DataEventType.INSERT, data));
        writer.end(table);
    }

    public void update(Table table, String[] data, String[] pkData) {
        writer.start(table);
        writer.write(new CsvData(DataEventType.INSERT, pkData, data));
        writer.end(table);
    }

    public void delete(Table table, String[] pkData) {
        writer.start(table);
        writer.write(new CsvData(DataEventType.DELETE, pkData, null));
        writer.end(table);
    }

    protected String buildUrl() {
        StringBuilder sb = new StringBuilder(syncUrl);
        sb.append("/push?");
        sb.append(WebConstants.NODE_ID);
        sb.append("=");
        sb.append(nodeId);
        sb.append("&");
        sb.append(WebConstants.SECURITY_TOKEN);
        sb.append("=");
        sb.append(securityToken);
        sb.append("&");
        sb.append(WebConstants.HOST_NAME);
        sb.append("=");
        sb.append(AppUtils.getHostName());
        sb.append("&");
        sb.append(WebConstants.IP_ADDRESS);
        sb.append("=");
        sb.append(AppUtils.getIpAddress());
        return sb.toString();
    }
}
