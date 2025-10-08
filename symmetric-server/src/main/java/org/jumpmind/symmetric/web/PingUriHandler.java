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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jumpmind.symmetric.service.IParameterService;

/**
 * Simple handler that returns a 200 to indicate that SymmetricDS is deployed and running.
 */
public class PingUriHandler extends AbstractUriHandler {
    public PingUriHandler(IParameterService parameterService, IInterceptor[] interceptors) {
        super("/ping/*", parameterService, interceptors);
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException,
            ServletException {
        res.setContentType("text/plain");
        res.getWriter().write("pong");
    }

    public void bandwidthTest(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        res.setContentType("text/plain");
        Long start = System.currentTimeMillis();
        long end = start + 5000;
        while (System.currentTimeMillis() < end) {
            res.getWriter().write(StringEscapeUtils.escapeHtml4(RandomStringUtils.secure().nextAlphabetic(600)));
        }
    }
}