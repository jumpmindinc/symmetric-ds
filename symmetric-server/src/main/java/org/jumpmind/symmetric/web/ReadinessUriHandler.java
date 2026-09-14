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
import java.util.Map;
import java.util.Map.Entry;

import org.jumpmind.symmetric.ApplicationHealthTracker;
import org.jumpmind.symmetric.IApplicationHealthTracker;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ReadinessUriHandler implements IUriHandler {
    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        res.setContentType("application/json");
        IApplicationHealthTracker tracker = ApplicationHealthTracker.getTracker();
        if (tracker == null) {
            res.getWriter().write("{\"status\": \"READY\"}");
            return;
        }
        boolean alive = tracker.isAlive();
        String response = prepareReadinessJsonRes(tracker.getReadinessMap(), alive);
        if (!tracker.isReady()) {
            res.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        res.getWriter().write(response);
    }

    @Override
    public String getUriPattern() {
        return "/readiness";
    }

    @Override
    public List<IInterceptor> getInterceptors() {
        return Collections.emptyList();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    private String prepareReadinessJsonRes(Map<String, Boolean> engineReadiness, boolean alive) {
        StringBuilder response = new StringBuilder("{\"engine_details\": [");
        boolean ready = alive;
        for (Entry<String, Boolean> engine : engineReadiness.entrySet()) {
            ready &= engine.getValue() != null && engine.getValue();
            response.append("{\"engine_name\": \"");
            response.append(engine.getKey());
            response.append("\", \"status\": \"");
            response.append(engine.getValue() ? "READY" : "NOT READY");
            response.append("\"},");
        }
        if (engineReadiness.size() > 0) {
            response.deleteCharAt(response.length() - 1);
        }
        response.append("],");
        response.append("\"status\": \"").append(ready ? "READY" : "NOT READY").append("\"}");
        return response.toString();
    }
}
