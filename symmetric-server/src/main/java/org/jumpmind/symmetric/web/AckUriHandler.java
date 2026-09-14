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
package org.jumpmind.symmetric.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.service.IAcknowledgeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.AbstractTransportManager;

public class AckUriHandler extends AbstractUriHandler {
    private static final Comparator<BatchAck> BATCH_ID_COMPARATOR = new Comparator<BatchAck>() {
        public int compare(BatchAck batchInfo1, BatchAck batchInfo2) {
            Long batchId1 = batchInfo1.getBatchId();
            Long batchId2 = batchInfo2.getBatchId();
            return batchId1.compareTo(batchId2);
        }
    };
    private IAcknowledgeService acknowledgeService;
    private boolean isStandalone = false;

    public AckUriHandler(IParameterService parameterService, IAcknowledgeService acknowledgeService, IInterceptor... interceptors) {
        super("/ack/*", parameterService, interceptors);
        this.acknowledgeService = acknowledgeService;
        if ("true".equals(System.getProperty(SystemConstants.SYSPROP_STANDALONE_WEB))) {
            isStandalone = true;
        }
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        if (log.isDebugEnabled()) {
            log.debug("Reading ack from node {} at remote address {}: {}", ServletUtils.getParameter(req, WebConstants.NODE_ID),
                    req.getRemoteAddr(), req.getParameterMap());
        }
        String queue = req.getHeader(WebConstants.CHANNEL_QUEUE);
        List<BatchAck> batches = AbstractTransportManager.readAcknowledgement(req.getParameterMap());
        Collections.sort(batches, BATCH_ID_COMPARATOR);
        if (isStandalone) {
            res.setHeader("Transfer-Encoding", "chunked");
        }
        long keepAliveMillis = parameterService.getLong(ParameterConstants.DATA_LOADER_SEND_ACK_KEEPALIVE);
        long ts = System.currentTimeMillis();
        PrintWriter writer = res.getWriter();
        for (BatchAck batchInfo : batches) {
            acknowledgeService.ack(batchInfo);
            if (keepAliveMillis > 0 && System.currentTimeMillis() - ts >= keepAliveMillis) {
                try {
                    writer.write("1=1&");
                    writer.flush();
                } catch (Exception e) {
                    log.info("Unable to keep client connection alive.  " + e.getClass().getName() + ": " + e.getMessage());
                    keepAliveMillis = 0;
                }
            }
            ts = System.currentTimeMillis();
        }
        writer.close();
        acknowledgeService.checkMissingAck(batches, queue);
    }
}
