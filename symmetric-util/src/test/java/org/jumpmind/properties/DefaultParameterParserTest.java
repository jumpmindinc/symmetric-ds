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
package org.jumpmind.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.junit.jupiter.api.Test;

class DefaultParameterParserTest {
    @Test
    void testParse_withSimpleProperty() {
        Map<String, ParameterMetaData> metaData = parse("key=value\n");
        assertEquals("value", metaData.get("key").getDefaultValue());
    }

    @Test
    void testParse_withDescriptionComment() {
        Map<String, ParameterMetaData> metaData = parse("# This is a description\nkey=value\n");
        assertEquals(" This is a description", metaData.get("key").getDescription());
    }

    @Test
    void testParse_withPrivateCommentExcluded() {
        Map<String, ParameterMetaData> metaData = parse("## secret note\nkey=value\n");
        assertNull(metaData.get("key").getDescription());
    }

    @Test
    void testParse_withDatabaseOverridableTrue() {
        Map<String, ParameterMetaData> metaData = parse("# DatabaseOverridable: true\nkey=value\n");
        assertTrue(metaData.get("key").isDatabaseOverridable());
    }

    @Test
    void testParse_withDatabaseOverridableDefaultsFalse() {
        Map<String, ParameterMetaData> metaData = parse("key=value\n");
        assertFalse(metaData.get("key").isDatabaseOverridable());
    }

    @Test
    void testParse_withTagsComment() {
        Map<String, ParameterMetaData> metaData = parse("# Tags: trigger, other\nkey=value\n");
        assertEquals(Set.of("trigger", "other"), metaData.get("key").getTags());
    }

    @Test
    void testParse_withTypeComment() {
        Map<String, ParameterMetaData> metaData = parse("# Type: boolean\nkey=value\n");
        assertEquals("boolean", metaData.get("key").getType());
        assertTrue(metaData.get("key").isBooleanType());
    }

    @Test
    void testParse_withBlankLineResetsMetadata() {
        Map<String, ParameterMetaData> metaData = parse("# description\n\nkey=value\n");
        assertNull(metaData.get("key").getDescription());
    }

    @Test
    void testParse_withMultilineValue() {
        Map<String, ParameterMetaData> metaData = parse("key=part1\\\npart2\n");
        assertEquals("part1part2", metaData.get("key").getDefaultValue());
    }

    @Test
    void testParse_withEscapedCharacter() {
        Map<String, ParameterMetaData> metaData = parse("key=a\\tb\n");
        assertEquals("a\tb", metaData.get("key").getDefaultValue());
    }

    @Test
    void testParse_withMultipleProperties() {
        Map<String, ParameterMetaData> metaData = parse("# First\nkey1=value1\n\n# Second\nkey2=value2\n");
        assertEquals(2, metaData.size());
        assertEquals(" First", metaData.get("key1").getDescription());
        assertEquals("value1", metaData.get("key1").getDefaultValue());
        assertEquals(" Second", metaData.get("key2").getDescription());
        assertEquals("value2", metaData.get("key2").getDefaultValue());
    }

    @Test
    void testParse_withMissingResource_returnsEmptyMap() {
        Map<String, ParameterMetaData> metaData = new DefaultParameterParser("/nonexistent-parameters.properties").parse();
        assertTrue(metaData.isEmpty());
    }

    @Test
    void testParameterMetaData_typeFlags() {
        ParameterMetaData md = new ParameterMetaData();
        md.setType(ParameterMetaData.TYPE_XML);
        assertTrue(md.isXmlType());
        assertFalse(md.isSqlType());
        md.setType(ParameterMetaData.TYPE_SQL);
        assertTrue(md.isSqlType());
        md.setType(ParameterMetaData.TYPE_CODE);
        assertTrue(md.isCodeType());
        md.setType(ParameterMetaData.TYPE_BOOLEAN);
        assertTrue(md.isBooleanType());
        md.setType(ParameterMetaData.TYPE_INT);
        assertTrue(md.isIntType());
        md.setType(ParameterMetaData.TYPE_TEXT_BOX);
        assertTrue(md.isTextBoxType());
        md.setType(ParameterMetaData.TYPE_ENCRYPTED);
        assertTrue(md.isEncryptedType());
    }

    @Test
    void testAppendDescription_concatenatesAcrossCalls() {
        ParameterMetaData md = new ParameterMetaData();
        md.appendDescription("first");
        md.appendDescription("second");
        assertEquals("firstsecond", md.getDescription());
    }

    @Test
    void testAddTag_addsMultipleTags() {
        ParameterMetaData md = new ParameterMetaData();
        md.addTag("a");
        md.addTag("b");
        assertEquals(Set.of("a", "b"), md.getTags());
    }

    private Map<String, ParameterMetaData> parse(String content) {
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return new DefaultParameterParser(inputStream).parse();
    }
}
