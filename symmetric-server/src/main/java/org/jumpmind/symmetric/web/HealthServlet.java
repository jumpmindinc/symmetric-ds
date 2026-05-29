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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload2.core.FileUploadException;

public class HealthServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final List<IUriHandler> handlers = new ArrayList<>();

    @Override
    public void init() {
        handlers.add(new LivelinessUriHandler());
        handlers.add(new ReadinessUriHandler());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        String pathInfo = Objects.toString(req.getPathInfo(), "/");
        for (IUriHandler handler : handlers) {
            if (handler.isEnabled() && pathInfo.equals(handler.getUriPattern())) {
                try {
                    handler.handle(req, res);
                } catch (FileUploadException e) {
                    res.sendError(HttpServletResponse.SC_BAD_REQUEST);
                }
                return;
            }
        }
        res.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
