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
package org.jumpmind.symmetric.common;

import java.util.Map;

import org.jumpmind.security.SecurityConstants;

import static java.util.Map.entry;

/**
 * These are properties that are server wide. They can be accessed via the parameter service or via System properties.
 */
public class ServerConstants {
    public static final String HOST_BIND_NAME = "host.bind.name";
    public static final String HTTP_ENABLE = "http.enable";
    public static final String HTTP_PORT = "http.port";
    public static final String CLUSTER_SERVER_ID = "cluster.server.id";
    public static final String CLUSTER_PARTITION_ID = "cluster.partition.id";
    public static final String CLUSTER_JCS_PORT = "cluster.jcs.port";
    public static final int INSTANCE_UUID_MARKER_AUTO = 0xaaaa; // Marked is inserted at the byte 4: xxxxxxxx-MMMM-xxxx-xxxx-xxxxxxxxxxxx
    public static final int INSTANCE_UUID_MARKER_HARDWARE = 0xbbbb;
    public static final int INSTANCE_UUID_MARKER_CONFIGURED = 0xcccc;
    public static final long CLUSTER_PEER_HEARTBEAT_DEFAULT_MS = 3_000L;
    public static final long CLUSTER_PEER_STALE_DEFAULT_MS = 60_000L;
    public static final long CLUSTER_PEER_OBSOLETE_DEFAULT_MS = 24 * 60 * 60000L;
    public static final long CLUSTER_PEER_WAIT_FOR_DBUPGRADE_MS = 60_000L;
    public static final String HTTPS_ENABLE = "https.enable";
    public static final String HTTPS_PORT = "https.port";
    public static final String HTTPS2_ENABLE = "https2.enable";
    public static final String HTTPS_VERIFIED_SERVERS = "https.verified.server.names";
    public static final String HTTPS_ALLOW_SELF_SIGNED_CERTS = "https.allow.self.signed.certs";
    public static final String HTTPS_NEED_CLIENT_AUTH = "https.need.client.auth";
    public static final String HTTPS_WANT_CLIENT_AUTH = "https.want.client.auth";
    public static final String SERVER_ALLOW_DIR_LISTING = "server.allow.dir.list";
    public static final String SERVER_ALLOW_HTTP_METHODS = "server.allow.http.methods";
    public static final String SERVER_DISALLOW_HTTP_METHODS = "server.disallow.http.methods";
    public static final String SERVER_HTTP_COOKIES_ENABLED = "server.http.cookies.enabled";
    public static final String STREAM_TO_FILE_ENCRYPT_ENABLED = "stream.to.file.encrypt.enabled";
    public static final String STREAM_TO_FILE_COMPRESSION_ENABLED = "stream.to.file.compression.enabled";
    public static final String STREAM_TO_FILE_COMPRESSION_LEVEL = "stream.to.file.compression.level";
    public static final String SERVER_ENGINE_URI_INTERCEPTORS = "server.engine.uri.interceptors";
    public static final String HTTP_TRANSPORT_MANAGER_CLASS = "http.transport.manager.class";
    public static final String SERVER_ACCESS_LOG_ENABLED = "server.access.log.enabled";
    public static final String SERVER_ACCESS_LOG_FILE = "server.access.log.file";
    public static final String SERVER_COOKIE_NAME = "server.cookie.name";
    public static final String SERVER_CONNECTION_IDLE_TIMEOUT = "server.connection.idle.timeout";
    public static final String SERVER_SERVLET_CONTEXT_PATH = "symmetric.server.web.home";
    public static final String SERVER_SINGLE_PROPERTIES_FILE = "server.single.properties.file";
    public static final String CONTAINER_MODE_ENABLED = "container.mode.enable";
    public static final String SYM_ENV_PREFIX = "SYM_";
    public static final Map<String, String> JVM_OVERRIDE_ENV_VARS = Map.ofEntries(entry("SYM_FILE_ENCODING", "file.encoding"),
            entry("SYM_STAGING_DIR", "java.io.tmpdir"),
            entry("SYM_WEB_MAX_FORM_SIZE", "org.eclipse.jetty.server.Request.maxFormContentSize"),
            entry("SYM_WEB_MAX_FORM_KEYS", "org.eclipse.jetty.server.Request.maxFormKeys"),
            entry("SYM_KEYSTORE_VAULT", SecurityConstants.SYSPROP_KEYSTORE),
            entry("SYM_KEYSTORE_PASSWORD", SecurityConstants.SYSPROP_KEYSTORE_PASSWORD),
            entry("SYM_CERT_TRUST_VAULT", SecurityConstants.SYSPROP_TRUSTSTORE),
            entry("SYM_CRYPTO_IGNORE_CIPHERS", SecurityConstants.SYSPROP_SSL_IGNORE_CIPHERS),
            entry("SYM_HTTP_CONNECT_TIMEOUT", "sun.net.client.defaultConnectTimeout"),
            entry("SYM_HTTP_RESPONSE_TIMEOUT", "sun.net.client.defaultReadTimeout"),
            entry("SYM_NET_PREFER_IPV4STACK", "java.net.preferIPv4Stack"));
    public static final Map<String, String> JVM_IMPORT_ENV_VARS = Map.ofEntries(
            entry("SYM_CLUSTER_KEYSTORE_SEED", SecurityConstants.SYSPROP_CLUSTER_KEYSTORE_SEED));
}