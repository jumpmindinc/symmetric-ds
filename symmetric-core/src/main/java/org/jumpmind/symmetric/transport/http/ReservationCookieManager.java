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
package org.jumpmind.symmetric.transport.http;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks outstanding push/pull reservations per target host, and clears the load balancer's session-affinity cookies for a target host when it rejects a
 * request with SC_SERVICE_BUSY, so the next request can be routed to a cluster member with reservation capacity.
 */
public class ReservationCookieManager {
    private static final Logger log = LoggerFactory.getLogger(ReservationCookieManager.class);
    private final ISymmetricEngine engine;
    protected Map<URI, Integer> outstandingReservationsByUri = new ConcurrentHashMap<URI, Integer>();

    public ReservationCookieManager(ISymmetricEngine engine) {
        this.engine = engine;
    }

    public void beginReservation(URL url) {
        outstandingReservationsByUri.compute(getAffinityURI(url), (key, count) -> count == null ? 1 : count + 1);
    }

    public void endReservation(URL url) {
        outstandingReservationsByUri.computeIfPresent(getAffinityURI(url), (key, count) -> count <= 1 ? null : count - 1);
    }

    public void handleServiceBusy(HttpConnection conn) {
        boolean clearBusyAffinityCookieEnabled = engine.getParameterService()
                .is(ParameterConstants.TRANSPORT_HTTP_SESSION_STICKY_RESET_ENABLED, false);
        if (clearBusyAffinityCookieEnabled) {
            clearReservationCookieForUri(conn);
        } else {
            log.debug("Not clearing load balancer affinity cookies for {} because {} = {}", getUri(conn),
                    ParameterConstants.TRANSPORT_HTTP_SESSION_STICKY_RESET_ENABLED, clearBusyAffinityCookieEnabled);
        }
    }

    public void clearReservationCookieForUri(HttpConnection conn) {
        Integer outstandingReservations = outstandingReservationsByUri.get(getAffinityURI(conn.getURL()));
        if (outstandingReservations != null && outstandingReservations > 0) {
            log.debug(
                    "Not clearing load balancer affinity cookies for {} because {} reservation(s) are outstanding",
                    getUri(conn), outstandingReservations);
            return;
        }
        CookieHandler handler = CookieHandler.getDefault();
        if (!(handler instanceof CookieManager)) {
            log.debug("Not clearing load balancer affinity cookies for {} because no CookieManager is installed (server.http.cookies.enabled may be false)",
                    getUri(conn));
            return;
        }
        URI uri = getAffinityURI(conn.getURL());
        if (uri == null) {
            log.debug("Not clearing load balancer affinity cookies for {} because uri could not be built", conn.getURL());
            return;
        }
        CookieStore store = ((CookieManager) handler).getCookieStore();
        int cookieCount = 0;
        for (HttpCookie cookie : new ArrayList<HttpCookie>(store.get(uri))) {
            store.remove(uri, cookie);
            if (log.isDebugEnabled()) {
                log.debug("Cleared cookie '{}' for {} after SC_SERVICE_BUSY", cookie.getName(), uri.getHost());
            }
            cookieCount++;
        }
        log.info("Cleared {} cookies for {} after SC_SERVICE_BUSY", cookieCount, uri.getHost());
    }

    private URI getAffinityURI(URL url) {
        try {
            return new URI(url.getProtocol(), null, url.getHost(), url.getPort(), "/", null, null);
        } catch (URISyntaxException e) {
            log.debug("URI could not be built for {}", url, e);
            return null;
        }
    }

    private String getUri(HttpConnection conn) {
        String uri = conn.getURL().toExternalForm();
        return uri.substring(0, uri.lastIndexOf("/"));
    }
}
