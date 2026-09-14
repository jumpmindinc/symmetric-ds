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
package org.jumpmind.util;

public class VersionUtil {
    private static final AbstractVersion instance = new AbstractVersion() {
        @Override
        protected String getArtifactName() {
            return null;
        }
    };

    public static int[] parseVersion(String version) {
        return instance.parseVersion(version);
    }

    public static boolean isOlderThanVersion(String checkVersion, String targetVersion) {
        return instance.isOlderThanVersion(checkVersion, targetVersion);
    }

    public static boolean isOlderThanVersion(int[] checkVersion, int[] targetVersion) {
        return instance.isOlderThanVersion(checkVersion, targetVersion);
    }

    public static boolean isOlderMinorVersion(String checkVersion, String targetVersion) {
        return instance.isOlderMinorVersion(checkVersion, targetVersion);
    }

    public static boolean isOlderMinorVersion(int[] checkVersion, int[] targetVersion) {
        return instance.isOlderMinorVersion(checkVersion, targetVersion);
    }
}
