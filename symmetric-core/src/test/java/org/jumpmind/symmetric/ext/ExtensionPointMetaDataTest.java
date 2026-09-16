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
package org.jumpmind.symmetric.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.extension.IExtensionPoint;
import org.junit.jupiter.api.Test;

class ExtensionPointMetaDataTest {
    @Test
    void testConstructor_withExtensionPointNameAndInstalled_deducesTypeAndLeavesExtraInfoNull() {
        SampleExtension extensionPoint = new SampleExtension();
        ExtensionPointMetaData meta = new ExtensionPointMetaData(extensionPoint, "sample", true);
        assertSame(extensionPoint, meta.getExtensionPoint());
        assertEquals("sample", meta.getName());
        assertEquals(ISampleExtensionPoint.class, meta.getType());
        assertTrue(meta.isInstalled());
        assertNull(meta.getExtraInfo());
    }

    @Test
    void testConstructor_withExplicitType_keepsProvidedType() {
        SampleExtension extensionPoint = new SampleExtension();
        ExtensionPointMetaData meta = new ExtensionPointMetaData(extensionPoint, "sample", ISampleExtensionPoint.class, false);
        assertEquals(ISampleExtensionPoint.class, meta.getType());
        assertFalse(meta.isInstalled());
        assertNull(meta.getExtraInfo());
    }

    @Test
    void testConstructor_withServletExtensionType_forcesInstalledTrue() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", IServletExtension.class, false);
        assertTrue(meta.isInstalled());
    }

    @Test
    void testConstructor_withServletFilterExtensionType_forcesInstalledTrue() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", IServletFilterExtension.class, false);
        assertTrue(meta.isInstalled());
    }

    @Test
    void testConstructor_withAllArgs_setsExtraInfo() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(new SampleExtension(), "sample", ISampleExtensionPoint.class, true, "extra-info");
        assertEquals("extra-info", meta.getExtraInfo());
        assertTrue(meta.isInstalled());
    }

    @Test
    void testConstructor_withNullExtensionPointAndNullType_leavesTypeNull() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        assertNull(meta.getType());
        assertNull(meta.getExtensionPoint());
        assertFalse(meta.isInstalled());
    }

    @Test
    void testGetTypeText_withNullType_returnsEmptyString() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        assertEquals("", meta.getTypeText());
    }

    @Test
    void testGetTypeText_withType_returnsSpacedUppercaseText() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", ISampleExtensionPoint.class, false, null);
        assertEquals("SAMPLE EXTENSION POINT", meta.getTypeText());
    }

    @Test
    void testAssignExtensionPointInterface_deducesTypeFromExtensionPointHierarchy() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(new SampleExtension(), "sample", ISampleExtensionPoint.class, false);
        meta.setType(null);
        meta.assignExtensionPointInterface();
        assertEquals(ISampleExtensionPoint.class, meta.getType());
    }

    @Test
    void testAssignExtensionPointInterface_withNullExtensionPoint_doesNothing() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        meta.assignExtensionPointInterface();
        assertNull(meta.getType());
    }

    @Test
    void testFindExtensionPoint_withDirectExtensionPointInterface_returnsExtensionPoint() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        assertEquals(IExtensionPoint.class, meta.findExtensionPoint(new Class<?>[] { IExtensionPoint.class }));
    }

    @Test
    void testFindExtensionPoint_withInterfaceExtendingExtensionPoint_returnsInterface() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        assertEquals(ISampleExtensionPoint.class, meta.findExtensionPoint(new Class<?>[] { ISampleExtensionPoint.class }));
    }

    @Test
    void testFindExtensionPoint_withNoMatch_returnsNull() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        assertNull(meta.findExtensionPoint(new Class<?>[] { Runnable.class }));
    }

    @Test
    void testSetExtraInfo_updatesExtraInfo() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        meta.setExtraInfo("new-info");
        assertEquals("new-info", meta.getExtraInfo());
    }

    @Test
    void testGetExtraInfo_returnsValueSetAtConstruction() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, "constructed-info");
        assertEquals("constructed-info", meta.getExtraInfo());
    }

    @Test
    void testSetType_updatesType() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        meta.setType(ISampleExtensionPoint.class);
        assertEquals(ISampleExtensionPoint.class, meta.getType());
    }

    @Test
    void testGetType_returnsValueSetAtConstruction() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", ISampleExtensionPoint.class, false, null);
        assertEquals(ISampleExtensionPoint.class, meta.getType());
    }

    @Test
    void testGetExtensionPoint_returnsValueSetAtConstruction() {
        SampleExtension extensionPoint = new SampleExtension();
        ExtensionPointMetaData meta = new ExtensionPointMetaData(extensionPoint, "sample", true);
        assertSame(extensionPoint, meta.getExtensionPoint());
    }

    @Test
    void testSetExtensionPoint_updatesExtensionPoint() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        SampleExtension extensionPoint = new SampleExtension();
        meta.setExtensionPoint(extensionPoint);
        assertSame(extensionPoint, meta.getExtensionPoint());
    }

    @Test
    void testGetName_returnsValueSetAtConstruction() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", true);
        assertEquals("sample", meta.getName());
    }

    @Test
    void testSetName_updatesName() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", true);
        meta.setName("renamed");
        assertEquals("renamed", meta.getName());
    }

    @Test
    void testIsInstalled_returnsValueSetAtConstruction() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", true);
        assertTrue(meta.isInstalled());
    }

    @Test
    void testSetInstalled_updatesInstalled() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", false);
        meta.setInstalled(true);
        assertTrue(meta.isInstalled());
    }

    @Test
    void testIsBuiltIn_withBuiltInExtensionPoint_returnsTrue() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(new SampleBuiltInExtension(), "sample", true);
        assertTrue(meta.isBuiltIn());
    }

    @Test
    void testIsBuiltIn_withNonBuiltInExtensionPoint_returnsFalse() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(new SampleExtension(), "sample", true);
        assertFalse(meta.isBuiltIn());
    }

    @Test
    void testIsBuiltIn_withNullExtensionPoint_returnsFalse() {
        ExtensionPointMetaData meta = new ExtensionPointMetaData(null, "sample", null, false, null);
        assertFalse(meta.isBuiltIn());
    }

    private interface ISampleExtensionPoint extends IExtensionPoint {
    }

    private interface IServletExtension extends IExtensionPoint {
    }

    private interface IServletFilterExtension extends IExtensionPoint {
    }

    private static class SampleExtension implements ISampleExtensionPoint {
    }

    private static class SampleBuiltInExtension implements IBuiltInExtensionPoint {
    }
}
