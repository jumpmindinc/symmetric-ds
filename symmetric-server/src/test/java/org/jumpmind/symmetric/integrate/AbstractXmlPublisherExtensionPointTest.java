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
package org.jumpmind.symmetric.integrate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.jumpmind.symmetric.route.SimpleRouterContext;
import org.jumpmind.util.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbstractXmlPublisherExtensionPointTest {
    private XmlPublisherDataRouter extensionPoint;

    @BeforeEach
    void setUp() {
        extensionPoint = new XmlPublisherDataRouter();
    }

    @Test
    void testReplaceInvalidChars_leavesPrintableText() {
        assertEquals("hello world", extensionPoint.replaceInvalidChars("hello world"));
    }

    @Test
    void testReplaceInvalidChars_withNull() {
        assertEquals("", extensionPoint.replaceInvalidChars(null));
    }

    @Test
    void testReplaceInvalidChars_withEmptyString() {
        assertEquals("", extensionPoint.replaceInvalidChars(""));
    }

    @Test
    void testReplaceInvalidChars_stripsControlCharacters() {
        String withControlChars = "a" + (char) 0x01 + (char) 0x08 + "b";
        assertEquals("ab", extensionPoint.replaceInvalidChars(withControlChars));
    }

    @Test
    void testReplaceInvalidChars_keepsTabNewlineAndCarriageReturn() {
        String whitespace = "a" + (char) 0x09 + (char) 0x0A + (char) 0x0D + "b";
        assertEquals(whitespace, extensionPoint.replaceInvalidChars(whitespace));
    }

    @Test
    void testGetKeyColumnNames_withoutGroupByColumns() {
        assertEquals("", extensionPoint.getKeyColumnNames());
    }

    @Test
    void testGetKeyColumnNames_withGroupByColumns() {
        extensionPoint.setGroupByColumnNames(Arrays.asList("ID1", "ID2"));
        assertEquals("[ID1, ID2]", extensionPoint.getKeyColumnNames());
    }

    @Test
    void testGetRelationNames_withoutRelations() {
        assertEquals("", extensionPoint.getRelationNames());
    }

    @Test
    void testGetRelationNames_withRelations() {
        extensionPoint.setRelationNameToPublish("ITEM");
        assertEquals("[ITEM]", extensionPoint.getRelationNames());
    }

    @Test
    void testSetRelationNamesToPublishAsGroup() {
        extensionPoint.setRelationNamesToPublishAsGroup(new HashSet<>(Arrays.asList("ITEM")));
        assertEquals("[ITEM]", extensionPoint.getRelationNames());
    }

    @Test
    void testSetNodeGroup() {
        extensionPoint.setNodeGroup("store");
        assertArrayEquals(new String[] { "store" }, extensionPoint.getNodeGroupIdsToApplyTo());
    }

    @Test
    void testSetNodeGroups() {
        extensionPoint.setNodeGroups(new String[] { "store", "corp" });
        assertArrayEquals(new String[] { "store", "corp" }, extensionPoint.getNodeGroupIdsToApplyTo());
    }

    @Test
    void testToXmlGroupId_withoutGroupByColumnsReturnsNull() {
        assertNull(extensionPoint.toXmlGroupId(new String[] { "ID" }, new String[] { "1" }, null, null));
    }

    @Test
    void testToXmlGroupId_fromKeyData() {
        extensionPoint.setGroupByColumnNames(Collections.singletonList("ID"));
        assertEquals("7", extensionPoint.toXmlGroupId(new String[] { "ID", "NAME" }, new String[] { "1", "widget" },
                new String[] { "ID" }, new String[] { "7" }));
    }

    @Test
    void testToXmlGroupId_fallsBackToRowDataWhenKeyColumnMissing() {
        extensionPoint.setGroupByColumnNames(Collections.singletonList("NAME"));
        assertEquals("widget", extensionPoint.toXmlGroupId(new String[] { "ID", "NAME" }, new String[] { "1", "widget" },
                new String[] { "ID" }, new String[] { "7" }));
    }

    @Test
    void testToXmlGroupId_fromRowDataWhenNoKeys() {
        extensionPoint.setGroupByColumnNames(Collections.singletonList("ID"));
        assertEquals("1", extensionPoint.toXmlGroupId(new String[] { "ID", "NAME" }, new String[] { "1", "widget" }, null, null));
    }

    @Test
    void testToXmlGroupId_concatenatesMultipleColumns() {
        extensionPoint.setGroupByColumnNames(Arrays.asList("ID", "NAME"));
        assertEquals("1widget", extensionPoint.toXmlGroupId(new String[] { "ID", "NAME" }, new String[] { "1", "widget" }, null, null));
    }

    @Test
    void testToXmlGroupId_returnsNullWhenColumnNotFound() {
        extensionPoint.setGroupByColumnNames(Collections.singletonList("MISSING"));
        assertNull(extensionPoint.toXmlGroupId(new String[] { "ID", "NAME" }, new String[] { "1", "widget" }, null, null));
    }

    @Test
    void testGetXmlCache_isEmptyForNewContext() {
        Context context = new SimpleRouterContext();
        assertTrue(extensionPoint.getXmlCache(context).isEmpty());
    }

    @Test
    void testDoesXmlExistToPublish_isFalseForNewContext() {
        Context context = new SimpleRouterContext();
        assertFalse(extensionPoint.doesXmlExistToPublish(context));
    }

    @Test
    void testGetXmlNamespace() {
        assertEquals("http://www.w3.org/2001/XMLSchema-instance", AbstractXmlPublisherExtensionPoint.getXmlNamespace().getURI());
    }
}
