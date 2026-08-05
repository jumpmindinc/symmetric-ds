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
package org.jumpmind.symmetric.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.file.FileSyncPullResult;
import org.jumpmind.symmetric.io.stage.StagedResourceETag;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.ProcessInfo;
import org.jumpmind.symmetric.model.ProcessInfo.ProcessStatus;
import org.jumpmind.symmetric.model.ProcessInfoKey;
import org.jumpmind.symmetric.model.ProcessType;
import org.jumpmind.symmetric.transport.IOutgoingTransport;

public class FileSyncPullUriHandler extends AbstractUriHandler {
    private ISymmetricEngine engine;

    public FileSyncPullUriHandler(ISymmetricEngine engine, IInterceptor... interceptors) {
        super("/filesync/pull/*", engine.getParameterService(), interceptors);
        this.engine = engine;
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException {
        String nodeId = ServletUtils.getParameter(req, WebConstants.NODE_ID);
        if (StringUtils.isBlank(nodeId)) {
            ServletUtils.sendError(res, WebConstants.SC_BAD_REQUEST,
                    "Node must be specified");
            return;
        }
        String batchIdParam = ServletUtils.getParameter(req, WebConstants.BATCH_ID);
        String ifETagHeader = req.getHeader(WebConstants.HEADER_IF_ETAG);
        String rangeHeader = req.getHeader(WebConstants.HEADER_RANGE);
        log.debug("File sync pull request received from {}: batchId={}, {}={}, {}={}", nodeId, batchIdParam,
                WebConstants.HEADER_IF_ETAG, ifETagHeader, WebConstants.HEADER_RANGE, rangeHeader);
        IOutgoingTransport outgoingTransport = createOutgoingTransport(res.getOutputStream(),
                req.getHeader(WebConstants.HEADER_ACCEPT_CHARSET),
                engine.getConfigurationService().getSuspendIgnoreChannelLists(nodeId));
        ProcessInfo processInfo = engine.getStatisticManager().newProcessInfo(
                new ProcessInfoKey(engine.getNodeService().findIdentityNodeId(), nodeId,
                        ProcessType.FILE_SYNC_PULL_HANDLER));
        try {
            Node targetNode = engine.getNodeService().findNode(nodeId, true);
            FileSyncPullResult result = engine.getFileSyncService().prepareFilesForPull(processInfo, targetNode,
                    batchIdParam, ifETagHeader, rangeHeader);
            if (result.getResumeEtag() != null) {
                res.setHeader(WebConstants.HEADER_ETAG, quoteEtag(result.getResumeEtag()));
                res.setHeader(WebConstants.HEADER_ACCEPT_RANGES, "bytes");
                if (result.isPartialContent()) {
                    res.setStatus(WebConstants.SC_PARTIAL_CONTENT);
                    res.setHeader(WebConstants.HEADER_CONTENT_RANGE,
                            "bytes " + result.getSkipCount() + "-" + (result.getTotalSize() - 1) + "/" + result.getTotalSize());
                }
            }
            if (result.isEnvelopeFormatUsed()) {
                res.setHeader(WebConstants.HEADER_FILESYNC_FORMAT, "1");
            }
            if (processInfo.getTotalBatchCount() == 0 && targetNode.isVersionGreaterThanOrEqualTo(3, 8, 0)) {
                ServletUtils.sendError(res, HttpServletResponse.SC_NO_CONTENT,
                        "No files to pull.");
            } else {
                res.setContentType("application/zip");
                res.addHeader("Content-Disposition", "attachment; filename=\"file-sync.zip\"");
                engine.getFileSyncService().writeFilesForPull(result, outgoingTransport);
            }
            processInfo.setStatus(ProcessStatus.OK);
        } catch (RuntimeException ex) {
            processInfo.setStatus(ProcessStatus.ERROR);
            throw ex;
        } finally {
            if (outgoingTransport != null) {
                outgoingTransport.close();
            }
        }
    }

    /**
     * An entity-tag must be an opaque quoted string (RFC 9110 section 8.8.3). Base64-encoding the JSON first guarantees the payload can never contain a quote
     * or other syntax-breaking character; this header is never parsed back by our own client (which tracks its own {@code If-ETag} instead), so the encoding is
     * unidirectional.
     */
    private static String quoteEtag(StagedResourceETag etag) {
        return "\"" + Base64.getEncoder().encodeToString(etag.toJson().getBytes(StandardCharsets.UTF_8)) + "\"";
    }
}
