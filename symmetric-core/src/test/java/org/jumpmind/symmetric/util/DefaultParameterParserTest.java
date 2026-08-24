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
package org.jumpmind.symmetric.util;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jumpmind.properties.DefaultParameterParser;
import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.junit.jupiter.api.Test;

public class DefaultParameterParserTest {
    @Test
    public void testParse() {
        DefaultParameterParser parser = new DefaultParameterParser("/symmetric-default.properties");
        Map<String, ParameterMetaData> metaData = parser.parse();
        assertNotNull(metaData);
        assertTrue(metaData.size() > 0);
        ParameterMetaData meta = metaData.get(ParameterConstants.PARAMETER_REFRESH_PERIOD_IN_MS);
        assertNotNull(meta);
        assertTrue(meta.getDescription().length() > 0);
        assertTrue(meta.isDatabaseOverridable());
        meta = metaData.get(ParameterConstants.NODE_GROUP_ID);
        assertNotNull(meta);
        assertTrue(meta.getDescription().length() > 0);
        assertFalse(meta.isDatabaseOverridable());
    }

    private Map<String, ParameterMetaData> parse(String propertiesText) {
        DefaultParameterParser parser = new DefaultParameterParser(
                new ByteArrayInputStream(propertiesText.getBytes(StandardCharsets.UTF_8)));
        return parser.parse();
    }

    @Test
    void parse_valueIsOnlyWhitespace_defaultValueIsEmptyString() {
        Map<String, ParameterMetaData> metaData = parse("some.key= \n");
        assertEquals("", metaData.get("some.key").getDefaultValue());
    }

    @Test
    void parse_leadingWhitespaceAfterEquals_isStripped() {
        Map<String, ParameterMetaData> metaData = parse("some.key=   actual-value\n");
        assertEquals("actual-value", metaData.get("some.key").getDefaultValue());
    }

    @Test
    void parse_trailingWhitespaceAfterEquals_isPreserved() {
        Map<String, ParameterMetaData> metaData = parse("some.key=actual-value   \n");
        assertEquals("actual-value   ", metaData.get("some.key").getDefaultValue());
    }

    @Test
    void parse_simpleValueWithNoWhitespace_unaffected() {
        Map<String, ParameterMetaData> metaData = parse("some.key=value\n");
        assertEquals("value", metaData.get("some.key").getDefaultValue());
    }

    @Test
    void parse_continuationLineWithLiteralLeadingWhitespace_isStripped() {
        String propertiesText = "some.key=first-line \\\n"
                + "       second-line\n";
        Map<String, ParameterMetaData> metaData = parse(propertiesText);
        assertEquals("first-line second-line", metaData.get("some.key").getDefaultValue());
    }

    @Test
    void parse_continuationLineWithEscapedLeadingSpaces_preservesEscapedSpaces() {
        String propertiesText = "some.key=first-line\\n\\u0020\\u0020\\\n"
                + "       second-line\n";
        Map<String, ParameterMetaData> metaData = parse(propertiesText);
        assertEquals("first-line\n  second-line", metaData.get("some.key").getDefaultValue());
    }
}
