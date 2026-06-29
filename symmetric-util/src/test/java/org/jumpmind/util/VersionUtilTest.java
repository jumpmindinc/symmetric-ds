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
package org.jumpmind.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionUtilTest {

    @Test
    void testParseVersion_fullMajorMinorPatch() {
        assertArrayEquals(new int[] { 3, 18, 5 }, VersionUtil.parseVersion("3.18.5"));
    }

    @Test
    void testParseVersion_stripsNonNumericCharacters() {
        assertArrayEquals(new int[] { 3, 18, 5 }, VersionUtil.parseVersion("3.18.5-SNAPSHOT"));
        assertArrayEquals(new int[] { 3, 18, 5 }, VersionUtil.parseVersion("v3.18.5"));
    }

    @Test
    void testParseVersion_defaultsMissingComponentsToZero() {
        assertArrayEquals(new int[] { 3, 18, 0 }, VersionUtil.parseVersion("3.18"));
        assertArrayEquals(new int[] { 3, 0, 0 }, VersionUtil.parseVersion("3"));
    }

    @Test
    void testParseVersion_zerosForEmptyOrNonNumeric() {
        assertArrayEquals(new int[] { 0, 0, 0 }, VersionUtil.parseVersion(""));
        assertArrayEquals(new int[] { 0, 0, 0 }, VersionUtil.parseVersion("abc"));
    }

    @Test
    void testParseVersion_ignoresComponentsBeyondPatch() {
        // Only major/minor/patch are read; a fourth segment is dropped.
        assertArrayEquals(new int[] { 3, 18, 5 }, VersionUtil.parseVersion("3.18.5.2"));
    }

    @Test
    void testIsOlderThanVersion_olderByMajorMinorOrPatch() {
        assertTrue(VersionUtil.isOlderThanVersion("2.0.0", "3.0.0"));
        assertTrue(VersionUtil.isOlderThanVersion("3.17.0", "3.18.0"));
        assertTrue(VersionUtil.isOlderThanVersion("3.18.4", "3.18.5"));
    }

    @Test
    void testIsOlderThanVersion_equalOrNewerIsFalse() {
        assertFalse(VersionUtil.isOlderThanVersion("3.18.0", "3.18.0"));
        assertFalse(VersionUtil.isOlderThanVersion("3.18.0", "3.17.0"));
        assertFalse(VersionUtil.isOlderThanVersion("3.18.5", "3.18.4"));
    }

    @Test
    void testIsOlderThanVersion_blankDevelopmentOrSnapshotTargetIsOlder() {
        assertTrue(VersionUtil.isOlderThanVersion("3.18.0", ""));
        assertTrue(VersionUtil.isOlderThanVersion("3.18.0", "development"));
        assertTrue(VersionUtil.isOlderThanVersion("3.18.0", "3.19.0-SNAPSHOT"));
    }

    @Test
    void testIsOlderThanVersion_noVersionCheckAgainstRealTargetIsNotOlder() {
        assertFalse(VersionUtil.isOlderThanVersion("", "3.18.0"));
        assertFalse(VersionUtil.isOlderThanVersion("development", "3.18.0"));
    }

    @Test
    void testIsOlderThanVersion_noVersionTargetTakesPrecedence() {
        // Target is evaluated first, so a blank target wins even when check is also a no-version.
        assertTrue(VersionUtil.isOlderThanVersion("development", ""));
    }

    @Test
    void testIsOlderThanVersion_arrayComparesMajorThenMinorThenPatch() {
        assertTrue(VersionUtil.isOlderThanVersion(new int[] { 3, 17, 0 }, new int[] { 3, 18, 0 }));
        assertTrue(VersionUtil.isOlderThanVersion(new int[] { 3, 18, 4 }, new int[] { 3, 18, 5 }));
        assertFalse(VersionUtil.isOlderThanVersion(new int[] { 3, 18, 5 }, new int[] { 3, 18, 5 }));
        assertFalse(VersionUtil.isOlderThanVersion(new int[] { 4, 0, 0 }, new int[] { 3, 18, 0 }));
    }

    @Test
    void testIsOlderThanVersion_arrayNullArgumentIsFalse() {
        assertFalse(VersionUtil.isOlderThanVersion(null, new int[] { 3, 18, 0 }));
        assertFalse(VersionUtil.isOlderThanVersion(new int[] { 3, 18, 0 }, null));
    }

    @Test
    void testIsOlderMinorVersion_olderByMajorOrMinor() {
        assertTrue(VersionUtil.isOlderMinorVersion("3.17.9", "3.18.0"));
        assertTrue(VersionUtil.isOlderMinorVersion("2.99.0", "3.0.0"));
    }

    @Test
    void testIsOlderMinorVersion_patchDifferencesIgnored() {
        // Same major/minor is not "older" regardless of patch, in either direction.
        assertFalse(VersionUtil.isOlderMinorVersion("3.18.0", "3.18.5"));
        assertFalse(VersionUtil.isOlderMinorVersion("3.18.9", "3.18.0"));
    }

    @Test
    void testIsOlderMinorVersion_newerMinorIsFalse() {
        assertFalse(VersionUtil.isOlderMinorVersion("4.0.0", "3.18.0"));
    }

    @Test
    void testIsOlderMinorVersion_anyNoVersionIsFalse() {
        // Unlike isOlderThanVersion, a blank target (or check) yields false here, not true.
        assertFalse(VersionUtil.isOlderMinorVersion("3.18.0", ""));
        assertFalse(VersionUtil.isOlderMinorVersion("", "3.18.0"));
    }

    @Test
    void testIsOlderMinorVersion_arrayComparesMajorAndMinorOnly() {
        assertTrue(VersionUtil.isOlderMinorVersion(new int[] { 3, 17, 9 }, new int[] { 3, 18, 0 }));
        assertFalse(VersionUtil.isOlderMinorVersion(new int[] { 3, 18, 9 }, new int[] { 3, 18, 0 }));
    }
}
