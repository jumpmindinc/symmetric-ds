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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class UpdateServiceTest {
    private static final int MINIMUM_READ_TIMEOUT_MILLIS = 1;
    private static final int RESPONSIVE_SERVER_TIMEOUT_MILLIS = 2000;
    private static final Duration HANG_GUARD = Duration.ofSeconds(2);
    private static final byte[] POST_DATA = "guid=abc&version=3.18.0".getBytes(StandardCharsets.UTF_8);
    private ISymmetricEngine engine;
    private ISqlTemplate sqlTemplateDirty;
    private UpdateService updateService;
    private HttpServer httpServer;
    private int readTimeoutMillis;

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
        readTimeoutMillis = RESPONSIVE_SERVER_TIMEOUT_MILLIS;
        updateService = new UpdateService(engine) {
            @Override
            protected int getConnectTimeoutMillis() {
                return RESPONSIVE_SERVER_TIMEOUT_MILLIS;
            }

            @Override
            protected int getReadTimeoutMillis() {
                return readTimeoutMillis;
            }
        };
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void testPostDataForVersionTimesOutWhenServerNeverResponds() throws IOException {
        readTimeoutMillis = MINIMUM_READ_TIMEOUT_MILLIS;
        try (ServerSocket silentServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            URL url = URI.create("http://127.0.0.1:" + silentServer.getLocalPort() + "/api/getlatest.php").toURL();
            assertTimeoutPreemptively(HANG_GUARD, () -> assertThrows(SocketTimeoutException.class, () -> updateService.postDataForVersion(url, POST_DATA)));
        }
        assertNull(updateService.getLatestVersion());
    }

    @Test
    void testPostDataForVersionParsesVersionAndLinkOnSuccess() throws IOException {
        URL url = startServer(200, "{\"version\":\"3.18.5\",\"link\":\"https://example.com/download\"}");
        updateService.postDataForVersion(url, POST_DATA);
        assertEquals("3.18.5", updateService.getLatestVersion());
        assertEquals("https://example.com/download", updateService.getDownloadUrl());
    }

    @Test
    void testPostDataForVersionIgnoresBodyOnNonOkResponse() throws IOException {
        URL url = startServer(503, "{\"version\":\"9.9.9\",\"link\":\"https://example.com/error\"}");
        updateService.postDataForVersion(url, POST_DATA);
        assertNull(updateService.getLatestVersion());
        assertNull(updateService.getDownloadUrl());
    }

    @Test
    void testPostDataForVersionSkipsBooleanNullAndNestedValues() throws IOException {
        URL url = startServer(200, "{\"beta\":true,\"notes\":null,\"build\":12345,\"mirrors\":[\"a\",\"b\"],"
                + "\"meta\":{\"x\":1},\"version\":\"3.18.6\",\"link\":\"https://example.com/d\"}");
        assertTimeoutPreemptively(HANG_GUARD, () -> updateService.postDataForVersion(url, POST_DATA));
        assertEquals("3.18.6", updateService.getLatestVersion());
        assertEquals("https://example.com/d", updateService.getDownloadUrl());
    }

    @Test
    void testPostDataForVersionDecodesResponseAsUtf8() throws IOException {
        URL url = startServer(200, "{\"version\":\"3.18.7\",\"link\":\"https://example.com/déjà\"}");
        updateService.postDataForVersion(url, POST_DATA);
        assertEquals("https://example.com/déjà", updateService.getDownloadUrl());
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

    private URL startServer(int responseCode, String body) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/api/getlatest.php", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(responseCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        httpServer.start();
        return URI.create("http://127.0.0.1:" + httpServer.getAddress().getPort() + "/api/getlatest.php").toURL();
    }
}
