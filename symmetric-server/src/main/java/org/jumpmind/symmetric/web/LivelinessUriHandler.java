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
import java.util.Collections;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jumpmind.symmetric.ApplicationHealthTracker;
import org.jumpmind.symmetric.IApplicationHealthTracker;

public class LivelinessUriHandler implements IUriHandler {
    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        IApplicationHealthTracker tracker = ApplicationHealthTracker.getTracker();
        boolean alive = tracker == null || tracker.isAlive();
        res.setContentType("application/json");
        if (alive) {
            res.getWriter().write("{\"status\": \"UP\"}");
        } else {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.getWriter().write("{\"status\": \"DOWN\"}");
        }
    }

    @Override
    public String getUriPattern() {
        return "/liveliness";
    }

    @Override
    public List<IInterceptor> getInterceptors() {
        return Collections.emptyList();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
