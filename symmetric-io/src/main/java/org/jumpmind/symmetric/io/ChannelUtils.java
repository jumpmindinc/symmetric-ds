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
package org.jumpmind.symmetric.io;

import org.apache.commons.lang3.ArrayUtils;

public class ChannelUtils {
    public static final String HEARTBEAT = "heartbeat";
    public static final String MONITOR = "monitor";
    public static final String CONFIG = "config";
    public static final String SYSTEM = "system";
    public static final String DYNAMIC = "dynamic";
    public static final String FILESYNC = "filesync";
    public static final String FILESYNC_RELOAD = "filesync_reload";
    public static final String[] SYMMETRIC_INTERNAL = { HEARTBEAT, MONITOR, CONFIG, SYSTEM, DYNAMIC, FILESYNC, FILESYNC_RELOAD };

    public static boolean isInternalSymmetricChannel(String channelId) {
        return channelId != null && ArrayUtils.contains(SYMMETRIC_INTERNAL, channelId);
    }
}
