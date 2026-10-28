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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Starts an embedded Jetty server with TLS and connects JDK clients to it. The hybrid post-quantum groups (JEP 527) arrive in Java 25.0.5, so these tests are
 * skipped on earlier builds. The JDK does not expose the negotiated named group through SSLSession, so the group itself is checked by hand with
 * -Djavax.net.debug=ssl:handshake (See the Security section of the User Guide).
 */
class JettyTlsNamedGroupTest {
    private static final String PASSWORD = "changeit";
    @TempDir
    static Path tempDir;
    static Server server;
    static int port;
    static File keystore;

    @BeforeAll
    static void startServer() throws Exception {
        Runtime.Version version = Runtime.version();
        assumeTrue(version.feature() > 25 || (version.feature() == 25 && version.update() >= 5), "Requires Java 25.0.5 or later");
        keystore = tempDir.resolve("test.p12").toFile();
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "test", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=localhost",
                "-ext", "san=dns:localhost", "-validity", "2", "-keystore", keystore.getPath(), "-storepass", PASSWORD, "-storetype", "PKCS12")
                .redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertEquals(0, process.waitFor(30, TimeUnit.SECONDS) ? process.exitValue() : -1, "keytool failed");
        SslContextFactory.Server ssl = new SslContextFactory.Server();
        ssl.setKeyStorePath(keystore.getPath());
        ssl.setKeyStorePassword(PASSWORD);
        server = new Server();
        ServerConnector connector = new ServerConnector(server, ssl);
        connector.setPort(0);
        server.addConnector(connector);
        server.start();
        port = connector.getLocalPort();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testDefaultClientNegotiatesTls13() throws Exception {
        assertEquals("TLSv1.3", handshake(new String[] { "TLSv1.3", "TLSv1.2" }, null));
    }

    @Test
    void testTls12OnlyClientStillConnects() throws Exception {
        assertEquals("TLSv1.2", handshake(new String[] { "TLSv1.2" }, null));
    }

    @Test
    void testClassicalGroupClientStillConnects() throws Exception {
        assertEquals("TLSv1.3", handshake(new String[] { "TLSv1.3" }, new String[] { "x25519", "secp256r1" }));
    }

    private String handshake(String[] protocols, String[] namedGroups) throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        try (InputStream in = URI.create(keystore.toURI().toString()).toURL().openStream()) {
            trust.load(in, PASSWORD.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("localhost", port)) {
            SSLParameters params = socket.getSSLParameters();
            params.setProtocols(protocols);
            if (namedGroups != null) {
                params.setNamedGroups(namedGroups);
            }
            socket.setSSLParameters(params);
            socket.startHandshake();
            return socket.getSession().getProtocol();
        }
    }
}
