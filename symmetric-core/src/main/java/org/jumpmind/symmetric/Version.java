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
package org.jumpmind.symmetric;

import org.jumpmind.util.AbstractVersion;

/**
 * Follow the Apache versioning scheme documented <a href="http://apr.apache.org/versioning.html">here</a>.
 */
final public class Version {
    public static final int[] VERSION_3_7_0 = new int[] { 3, 7, 0 };
    public static final int[] VERSION_3_8_0 = new int[] { 3, 8, 0 };
    public static final int[] VERSION_3_8_18 = new int[] { 3, 8, 18 };
    public static final int[] VERSION_3_9_0 = new int[] { 3, 9, 0 };
    public static final int[] VERSION_3_11_0 = new int[] { 3, 11, 0 };
    public static final int[] VERSION_3_12_0 = new int[] { 3, 12, 0 };
    private static AbstractVersion version = new AbstractVersion() {
        @Override
        protected String getArtifactName() {
            return "symmetric-core";
        }
    };

    public static String version() {
        return version.version();
    }

    public static String versionWithUnderscores() {
        return version.versionWithUnderscores();
    }

    public static int[] parseVersion(String version) {
        return Version.version.parseVersion(version);
    }

    public static boolean isOlderVersion(String version) {
        return isOlderThanVersion(version, version());
    }

    public static boolean isOlderThanVersion(String checkVersion, String targetVersion) {
        return version.isOlderThanVersion(checkVersion, targetVersion);
    }

    public static boolean isOlderThanVersion(int[] checkVersion, int[] targetVersion) {
        return version.isOlderThanVersion(checkVersion, targetVersion);
    }

    public static boolean isOlderMinorVersion(String version) {
        return isOlderMinorVersion(version, version());
    }

    public static boolean isOlderMinorVersion(String checkVersion, String targetVersion) {
        return version.isOlderMinorVersion(checkVersion, targetVersion);
    }

    public static boolean isOlderMinorVersion(int[] checkVersion, int[] targetVersion) {
        return version.isOlderMinorVersion(checkVersion, targetVersion);
    }

    public static boolean isDevelopment(String version) {
        return Version.version.isDevelopment(version);
    }

    public static long getBuildTime() {
        return version.getBuildTime();
    }

    public static String getBuildYear() {
        return version.getBuildYear();
    }

    public static int getMajorVersion() {
        return version.getMajorVersion();
    }

    public static int getMinorVersion() {
        return version.getMinorVersion();
    }

    public static int getPatchVersion() {
        return version.getPatchVersion();
    }
}