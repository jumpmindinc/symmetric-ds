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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationCookieManagerTest {
    private ReservationCookieManager reservationCookieManager;
    private ISymmetricEngine engine;
    private IParameterService ps;
    private CookieHandler originalCookieHandler;

    @BeforeEach
    void setUp() {
        originalCookieHandler = CookieHandler.getDefault();
        CookieHandler.setDefault(null);
        engine = mock(ISymmetricEngine.class);
        ps = mock(IParameterService.class);
        when(engine.getParameterService()).thenReturn(ps);
        reservationCookieManager = new ReservationCookieManager(engine);
    }

    @AfterEach
    void tearDown() {
        CookieHandler.setDefault(originalCookieHandler);
    }

    @Test
    void testBeginReservation_incrementsCountForUri() throws Exception {
        URL url = URI.create("http://node.example.com/sync/push?nodeId=1").toURL();
        reservationCookieManager.beginReservation(url);
        assertEquals(1, reservationCookieManager.outstandingReservationsByUri.size());
        reservationCookieManager.beginReservation(url);
        assertEquals(2, reservationCookieManager.outstandingReservationsByUri.values().iterator().next());
    }

    @Test
    void testBeginReservation_pushAndPullUrlsToSameHostShareCount() throws Exception {
        URL pushUrl = URI.create("http://node.example.com/sync/push?nodeId=1").toURL();
        URL pullUrl = URI.create("http://node.example.com/sync/pull?nodeId=1").toURL();
        reservationCookieManager.beginReservation(pushUrl);
        reservationCookieManager.beginReservation(pullUrl);
        assertEquals(1, reservationCookieManager.outstandingReservationsByUri.size());
        assertEquals(2, reservationCookieManager.outstandingReservationsByUri.values().iterator().next());
    }

    @Test
    void testEndReservation_decrementsAndRemovesEntryWhenCountReachesZero() throws Exception {
        URL url = URI.create("http://node.example.com/sync/push").toURL();
        reservationCookieManager.beginReservation(url);
        reservationCookieManager.beginReservation(url);
        reservationCookieManager.endReservation(url);
        assertEquals(1, reservationCookieManager.outstandingReservationsByUri.values().iterator().next());
        reservationCookieManager.endReservation(url);
        assertTrue(reservationCookieManager.outstandingReservationsByUri.isEmpty());
    }

    @Test
    void testEndReservation_withNoExistingEntry_doesNotThrow() throws Exception {
        URL url = URI.create("http://node.example.com/sync/push").toURL();
        assertDoesNotThrow(() -> reservationCookieManager.endReservation(url));
        assertTrue(reservationCookieManager.outstandingReservationsByUri.isEmpty());
    }

    @Test
    void testBeginAndEndReservation_underConcurrentContentionOnSameUri_neverLeavesAStaleEntry() throws Exception {
        URL url = URI.create("http://node.example.com/sync/push").toURL();
        int threadCount = 20;
        int opsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < opsPerThread; j++) {
                    reservationCookieManager.beginReservation(url);
                    reservationCookieManager.endReservation(url);
                }
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(reservationCookieManager.outstandingReservationsByUri.isEmpty());
    }

    @Test
    void testHandleServiceBusy_whenDisabled_doesNotThrow() throws Exception {
        when(ps.is(ParameterConstants.TRANSPORT_HTTP_SESSION_STICKY_RESET_ENABLED, false)).thenReturn(false);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://node.example.com/sync/push").toURL());
        assertDoesNotThrow(() -> reservationCookieManager.handleServiceBusy(conn));
    }

    @Test
    void testHandleServiceBusy_whenEnabled_clearsCookiesForHost() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        URI targetUri = URI.create("http://node.example.com/");
        cookieManager.getCookieStore().add(targetUri, new HttpCookie("JSESSIONID", "abc"));
        when(ps.is(ParameterConstants.TRANSPORT_HTTP_SESSION_STICKY_RESET_ENABLED, false)).thenReturn(true);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://node.example.com/sync/push").toURL());
        reservationCookieManager.handleServiceBusy(conn);
        assertTrue(cookieManager.getCookieStore().get(targetUri).isEmpty());
    }

    @Test
    void testClearReservationCookieForUri_withOutstandingReservation_leavesCookiesIntact() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        URI targetUri = URI.create("http://node.example.com/");
        cookieManager.getCookieStore().add(targetUri, new HttpCookie("JSESSIONID", "abc"));
        URL url = URI.create("http://node.example.com/sync/push").toURL();
        reservationCookieManager.beginReservation(url);
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(url);
        reservationCookieManager.clearReservationCookieForUri(conn);
        assertEquals(1, cookieManager.getCookieStore().get(targetUri).size());
    }

    @Test
    void testClearReservationCookieForUri_withNoCookieManagerInstalled_doesNotThrow() throws Exception {
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://node.example.com/sync/push").toURL());
        assertDoesNotThrow(() -> reservationCookieManager.clearReservationCookieForUri(conn));
    }

    @Test
    void testClearReservationCookieForUri_withCookieManagerInstalled_clearsAllCookiesForHostOnly() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        URI targetUri = URI.create("http://node.example.com/");
        URI otherUri = URI.create("http://other.example.com/");
        cookieManager.getCookieStore().add(targetUri, new HttpCookie("JSESSIONID", "abc"));
        cookieManager.getCookieStore().add(targetUri, new HttpCookie("WAFCOOKIE", "xyz"));
        cookieManager.getCookieStore().add(otherUri, new HttpCookie("OTHERHOSTCOOKIE", "other"));
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://node.example.com/sync/push").toURL());
        reservationCookieManager.clearReservationCookieForUri(conn);
        assertTrue(cookieManager.getCookieStore().get(targetUri).isEmpty());
        assertEquals(1, cookieManager.getCookieStore().get(otherUri).size());
    }

    @Test
    void testClearReservationCookieForUri_withSameNamedCookieOnDifferentHost_alsoRemovesIt() throws Exception {
        // Defect pinned, not endorsed: java.net.HttpCookie#equals() only compares name/domain/path, and cookies
        // added without an explicit Domain attribute never have a domain set on the HttpCookie object itself, so
        // two same-named cookies from different hosts are equal() to each other. CookieStore.remove(uri, cookie)
        // removes by that equality, not by the uri argument, so this host-scoped clear can still delete an
        // identically-named cookie (e.g. the default "JSESSIONID") belonging to an unrelated host sharing this JVM.
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
        URI targetUri = URI.create("http://node.example.com/");
        URI otherUri = URI.create("http://other.example.com/");
        cookieManager.getCookieStore().add(targetUri, new HttpCookie("JSESSIONID", "abc"));
        cookieManager.getCookieStore().add(otherUri, new HttpCookie("JSESSIONID", "other"));
        HttpConnection conn = mock(HttpConnection.class);
        when(conn.getURL()).thenReturn(URI.create("http://node.example.com/sync/push").toURL());
        reservationCookieManager.clearReservationCookieForUri(conn);
        assertTrue(cookieManager.getCookieStore().get(otherUri).isEmpty());
    }
}
