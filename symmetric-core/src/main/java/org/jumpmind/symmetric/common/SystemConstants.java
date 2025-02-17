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

import org.jumpmind.security.SecurityConstants;

/**
 * These are properties that can be set only as Java System properties using -D settings.
 */
public class SystemConstants {
    public static final String SYSPROP_STANDALONE_WEB = "symmetric.standalone.web";
    public static final String SYSPROP_LAUNCHER = "symmetric.launcher";
    public static final String SYSPROP_ENGINES_DIR = "symmetric.engines.dir";
    public static final String SYSPROP_WAIT_FOR_DATABASE = "symmetric.wait.for.database.enabled";
    public static final String SYSPROP_WEB_DIR = "symmetric.default.web.dir";
    public static final String SYSPROP_SERVER_PROPERTIES_PATH = "symmetric.server.properties.path";
    public static final String SYSPROP_MODULES_DIR = "symmetric.modules.dir";
    public static final String SYSPROP_TOOLS_DIR = "symmetric.tools.dir";
    public static final String SYSPROP_CLUSTER_SERVER_ID = "runtime.symmetric.cluster.server.id";
    public static final String SYSPROP_DEFAULT_HTTP_PORT = "symmetric.default.http.port";
    public static final String SYSPROP_DEFAULT_HTTPS_PORT = "symmetric.default.https.port";
    public static final String SYSPROP_CONCURRENT_ENGINES_STARTING_COUNT = "symmetric.concurrent.engines.starting.count";
    public static final String SYSPROP_REST_PROPERTIES_FILE = "sym.rest.properties.file";
    public static final String SYSPROP_KEYSTORE_TYPE = SecurityConstants.SYSPROP_KEYSTORE_TYPE;
    public static final String SYSPROP_KEYSTORE_CERT_ALIAS = SecurityConstants.SYSPROP_KEYSTORE_CERT_ALIAS;
    public static final String SYSPROP_KEYSTORE_PARAMETERS = "symmetric.parameters.saved.in.keystore";
    public static final String SYSPROP_SNOWFLAKE_CLUSTER_PRIMARY_KEYS = "snowflake.cluster.primary.keys";
}