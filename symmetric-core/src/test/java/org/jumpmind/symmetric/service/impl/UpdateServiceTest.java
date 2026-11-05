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
package org.jumpmind.symmetric.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class UpdateServiceTest {
    private static final int SHORT_TIMEOUT_MILLIS = 500;
    private static final byte[] POST_DATA = "guid=abc&version=3.18.0".getBytes(StandardCharsets.UTF_8);
    private static final String DOWNLOAD_LINK = "https://downloads.jumpmind.com/symmetricds/releases/3.18/symmetric-pro-3.18.5-setup.jar";
    private ISymmetricEngine engine;
    private ISqlTemplate sqlTemplateDirty;
    private TestUpdateService updateService;
    private final List<HttpServer> httpServers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        engine = mock(ISymmetricEngine.class, RETURNS_DEEP_STUBS);
        IParameterService parameterService = mock(IParameterService.class);
        when(parameterService.getTablePrefix()).thenReturn("sym");
        ISymmetricDialect symmetricDialect = mock(ISymmetricDialect.class, RETURNS_DEEP_STUBS);
        sqlTemplateDirty = mock(ISqlTemplate.class);
        when(symmetricDialect.getPlatform().getSqlTemplateDirty()).thenReturn(sqlTemplateDirty);
        when(engine.getParameterService()).thenReturn(parameterService);
        when(engine.getSymmetricDialect()).thenReturn(symmetricDialect);
        updateService = new TestUpdateService(engine);
    }

    @AfterEach
    void tearDown() {
        for (HttpServer httpServer : httpServers) {
            httpServer.stop(0);
        }
    }

    @Test
    void testPostDataForVersionTimesOutWhenServerNeverResponds() throws IOException {
        try (ServerSocket silentServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            URL url = toLocalUrl(silentServer.getLocalPort());
            assertTimeoutPreemptively(Duration.ofSeconds(10),
                    () -> assertThrows(SocketTimeoutException.class, () -> updateService.postDataForVersion(url, POST_DATA)));
        }
        assertNull(updateService.getLatestVersion());
    }

    @Test
    void testPostDataForVersionParsesVersionAndLinkOnSuccess() throws IOException {
        URL url = startServer(200, versionResponse("3.18.5", DOWNLOAD_LINK), new AtomicInteger());
        updateService.postDataForVersion(url, POST_DATA);
        assertEquals("3.18.5", updateService.getLatestVersion());
        assertEquals(DOWNLOAD_LINK, updateService.getDownloadUrl());
    }

    @Test
    void testPostDataForVersionIgnoresBodyOnNonOkResponse() throws IOException {
        URL url = startServer(503, versionResponse("9.9.9", DOWNLOAD_LINK), new AtomicInteger());
        updateService.postDataForVersion(url, POST_DATA);
        assertNull(updateService.getLatestVersion());
        assertNull(updateService.getDownloadUrl());
    }

    @Test
    void testPostDataForVersionDoesNotFollowRedirects() throws IOException {
        URL target = startServer(200, versionResponse("9.9.9", DOWNLOAD_LINK), new AtomicInteger());
        HttpServer redirectServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServers.add(redirectServer);
        redirectServer.createContext("/api/getlatest.php", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", target.toString());
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        redirectServer.start();
        updateService.postDataForVersion(toLocalUrl(redirectServer.getAddress().getPort()), POST_DATA);
        assertNull(updateService.getLatestVersion());
    }

    @Test
    void testPostDataForVersionSkipsBooleanNullAndNestedValues() throws IOException {
        URL url = startServer(200, "{\"beta\":true,\"notes\":null,\"build\":12345,\"mirrors\":[\"a\",\"b\"],"
                + "\"meta\":{\"x\":1},\"version\":\"3.18.6\",\"link\":\"" + DOWNLOAD_LINK + "\"}", new AtomicInteger());
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> updateService.postDataForVersion(url, POST_DATA));
        assertEquals("3.18.6", updateService.getLatestVersion());
        assertEquals(DOWNLOAD_LINK, updateService.getDownloadUrl());
    }

    @Test
    void testPostDataForVersionDecodesResponseAsUtf8() throws IOException {
        String link = "https://downloads.jumpmind.com/déjà";
        URL url = startServer(200, versionResponse("3.18.7", link), new AtomicInteger());
        updateService.postDataForVersion(url, POST_DATA);
        assertEquals(link, updateService.getDownloadUrl());
    }

    @Test
    void testPostDataForVersionIgnoresUntrustedDownloadLink() throws IOException {
        URL url = startServer(200, versionResponse("3.18.8", "javascript:alert(1)"), new AtomicInteger());
        updateService.postDataForVersion(url, POST_DATA);
        assertEquals("3.18.8", updateService.getLatestVersion());
        assertNull(updateService.getDownloadUrl());
    }

    @Test
    void testPostDataForVersionIgnoresMalformedVersion() throws IOException {
        URL url = startServer(200, versionResponse("<img src=x onerror=alert(1)>", DOWNLOAD_LINK), new AtomicInteger());
        updateService.postDataForVersion(url, POST_DATA);
        assertNull(updateService.getLatestVersion());
        assertFalse(updateService.isNewVersionAvailable());
    }

    @Test
    void testVersionPatternAcceptsReleaseAndSnapshotVersions() {
        assertTrue(UpdateService.VERSION_PATTERN.matcher("3.18.3").matches());
        assertTrue(UpdateService.VERSION_PATTERN.matcher("3.16.14.1").matches());
        assertTrue(UpdateService.VERSION_PATTERN.matcher("3.19.0-SNAPSHOT").matches());
        assertFalse(UpdateService.VERSION_PATTERN.matcher("3.18.3'><b>").matches());
        assertFalse(UpdateService.VERSION_PATTERN.matcher("latest").matches());
    }

    @Test
    void testIsTrustedDownloadUrlAcceptsHttpsOnJumpmindAndSymmetricDsDomains() {
        assertTrue(updateService.isTrustedDownloadUrl(DOWNLOAD_LINK));
        assertTrue(updateService.isTrustedDownloadUrl("https://jumpmind.com/download"));
        assertTrue(updateService.isTrustedDownloadUrl("https://www.symmetricds.org/download"));
        assertTrue(updateService.isTrustedDownloadUrl("HTTPS://Downloads.JumpMind.com/file.zip"));
    }

    @Test
    void testIsTrustedDownloadUrlRejectsOtherSchemesHostsAndMarkup() {
        assertFalse(updateService.isTrustedDownloadUrl("http://downloads.jumpmind.com/file.zip"));
        assertFalse(updateService.isTrustedDownloadUrl("javascript:alert(1)"));
        assertFalse(updateService.isTrustedDownloadUrl("https://example.com/file.zip"));
        assertFalse(updateService.isTrustedDownloadUrl("https://notjumpmind.com/file.zip"));
        assertFalse(updateService.isTrustedDownloadUrl("https://jumpmind.com.example.com/file.zip"));
        assertFalse(updateService.isTrustedDownloadUrl("https://downloads.jumpmind.com/file.zip\" title=\"x"));
        assertFalse(updateService.isTrustedDownloadUrl("https://downloads.jumpmind.com/a'onmouseover='alert(1)"));
    }

    @Test
    void testGetLatestVersionReturnsNullAndClearsPreviousResultWhenCommunicationFails() throws IOException {
        URL goodUrl = startServer(200, versionResponse("3.18.5", DOWNLOAD_LINK), new AtomicInteger());
        assertEquals("3.18.5", updateService.getLatestVersion(goodUrl, POST_DATA));
        URL failingUrl = startServer(500, "", new AtomicInteger());
        assertNull(updateService.getLatestVersion(failingUrl, POST_DATA));
        assertNull(updateService.getLatestVersion());
        assertNull(updateService.getDownloadUrl());
    }

    @Test
    void testCheckForUpdatesUsesBackupUrlWhenPrimaryFails() throws IOException {
        AtomicInteger backupRequestCount = new AtomicInteger();
        updateService.primaryUrl = startServer(503, "", new AtomicInteger());
        updateService.backupUrl = startServer(200, versionResponse("3.18.9", DOWNLOAD_LINK), backupRequestCount);
        updateService.checkForUpdates();
        assertEquals(1, backupRequestCount.get());
        assertEquals("3.18.9", updateService.getLatestVersion());
        assertEquals(DOWNLOAD_LINK, updateService.getDownloadUrl());
    }

    @Test
    void testCheckForUpdatesUsesBackupUrlWhenPrimaryNeverResponds() throws IOException {
        AtomicInteger backupRequestCount = new AtomicInteger();
        try (ServerSocket silentServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            updateService.primaryUrl = toLocalUrl(silentServer.getLocalPort());
            updateService.backupUrl = startServer(200, versionResponse("3.18.9", DOWNLOAD_LINK), backupRequestCount);
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> updateService.checkForUpdates());
        }
        assertEquals(1, backupRequestCount.get());
        assertEquals("3.18.9", updateService.getLatestVersion());
    }

    @Test
    void testCheckForUpdatesSkipsBackupUrlWhenPrimarySucceeds() throws IOException {
        AtomicInteger backupRequestCount = new AtomicInteger();
        updateService.primaryUrl = startServer(200, versionResponse("3.18.5", DOWNLOAD_LINK), new AtomicInteger());
        updateService.backupUrl = startServer(200, versionResponse("3.18.9", DOWNLOAD_LINK), backupRequestCount);
        updateService.checkForUpdates();
        assertEquals(0, backupRequestCount.get());
        assertEquals("3.18.5", updateService.getLatestVersion());
    }

    @Test
    void testUpdateUrlsUseStatusAndDownloadsHosts() throws MalformedURLException {
        UpdateService defaultUrlService = new UpdateService(engine);
        assertEquals("https://status.symmetricds.org/api/getlatest.php", defaultUrlService.getUpdateUrl().toString());
        assertEquals("https://downloads.jumpmind.com/api/getlatest.php", defaultUrlService.getBackupUpdateUrl().toString());
    }

    @Test
    void testAddUsagePropertiesReportsIncomingBatchCount() {
        Map<String, Object> outgoing = new HashMap<>();
        outgoing.put("batch_count", 11L);
        Map<String, Object> incoming = new HashMap<>();
        incoming.put("batch_count", 22L);
        when(sqlTemplateDirty.queryForMap(anyString(), any(Object[].class))).thenReturn(outgoing, incoming);
        Map<String, Object> prop = new HashMap<>();
        updateService.addUsageProperties(prop);
        assertEquals(11L, prop.get("out_batches"));
        assertEquals(22L, prop.get("in_batches"));
    }

    private static String versionResponse(String version, String link) {
        return "{\"version\":\"" + version + "\",\"link\":\"" + link.replace("\"", "\\\"") + "\"}";
    }

    private static URL toLocalUrl(int port) throws MalformedURLException {
        return URI.create("http://127.0.0.1:" + port + "/api/getlatest.php").toURL();
    }

    private URL startServer(int responseCode, String body, AtomicInteger requestCount) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServers.add(httpServer);
        httpServer.createContext("/api/getlatest.php", exchange -> {
            requestCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(responseCode, responseBytes.length == 0 ? -1 : responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        httpServer.start();
        return toLocalUrl(httpServer.getAddress().getPort());
    }

    private static class TestUpdateService extends UpdateService {
        private URL primaryUrl;
        private URL backupUrl;

        TestUpdateService(ISymmetricEngine engine) {
            super(engine);
        }

        @Override
        protected int getConnectTimeoutMillis() {
            return SHORT_TIMEOUT_MILLIS;
        }

        @Override
        protected int getReadTimeoutMillis() {
            return SHORT_TIMEOUT_MILLIS;
        }

        @Override
        protected URL getUpdateUrl() {
            return primaryUrl;
        }

        @Override
        protected URL getBackupUpdateUrl() {
            return backupUrl;
        }
    }
}
