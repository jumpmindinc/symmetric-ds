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
package org.jumpmind.symmetric.observability.interfaces;

/** Attribute name constants for {@link MetricAttribute}. Used as the {@code name} field when tagging metric observations with context labels. */
public final class MetricAttributeConstants {
    private MetricAttributeConstants() {
    }

    public static final String BUSINESS_DATE = "business_date";
    public static final String CDC_EVENT = "cdc_event_type";
    public static final String CHANNEL = "channel";
    public static final String CLIENT_ADDRESS = "client_address";
    public static final String CLIENT_PORT = "client_port";
    public static final String CLIENT_VERSION = "client_version";
    public static final String HTTP_METHOD = "http_method";
    public static final String JOB = "job";
    public static final String NODE_GROUP = "node_group";
    public static final String NODE_NAME = "node_name";
    public static final String SERVER_ADDRESS = "server_address";
    public static final String SERVER_PORT = "server_port";
    public static final String SERVER_PROTOCOL = "server_protocol";
    public static final String SERVER_URL = "server_url";
    public static final String SERVER_VERSION = "server_version";
    public static final String SYSTEM_DATE = "system_date";
    public static final String THREAD_ID = "thread_id";
    public static final String THREAD_NAME = "thread_name";
}
