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

import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.jumpmind.symmetric.ISymmetricEngine;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FileSyncPushUriHandler extends AbstractUriHandler {
    private ISymmetricEngine engine;

    public FileSyncPushUriHandler(ISymmetricEngine engine, IInterceptor... interceptors) {
        super("/filesync/push/*", engine.getParameterService(), interceptors);
        this.engine = engine;
    }

    @SuppressWarnings("rawtypes")
    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException, FileUploadException {
        String nodeId = ServletUtils.getParameter(req, WebConstants.NODE_ID);
        if (StringUtils.isBlank(nodeId)) {
            ServletUtils.sendError(res, WebConstants.SC_BAD_REQUEST,
                    "Node must be specified");
            return;
        } else if (!JakartaServletFileUpload.isMultipartContent(req)) {
            ServletUtils.sendError(res, WebConstants.SC_BAD_REQUEST,
                    "We only handle multipart requests");
            return;
        } else {
            log.debug("File sync push request received from {}", nodeId);
        }
        JakartaServletFileUpload upload = new JakartaServletFileUpload();
        // Parse the request
        FileItemInputIterator iter = upload.getItemIterator(req);
        while (iter.hasNext()) {
            FileItemInput item = iter.next();
            String name = item.getFieldName();
            if (!item.isFormField()) {
                log.debug("Processing upload file field " + name + " with file name " + item.getName()
                        + " detected.");
                engine.getFileSyncService().loadFilesFromPush(nodeId, item.getInputStream(),
                        res.getOutputStream());
            }
        }
        res.flushBuffer();
    }
}
