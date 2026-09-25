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
package org.jumpmind.symmetric.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FileParsingOptionsTest {
    @Test
    void testDefaults() {
        FileParsingOptions options = new FileParsingOptions();
        assertFalse(options.isOverwrite());
        assertTrue(options.isTailFile());
        assertFalse(options.isStandardizeNames());
        assertFalse(options.isSendDdl());
        assertFalse(options.isIncludeTransactionId());
    }

    @Test
    void testSettersReturnSameInstanceForChaining() {
        FileParsingOptions options = new FileParsingOptions();
        assertEquals(options, options.overwrite(true).tailFile(false).standardizeNames(true).sendDdl(true).includeTransactionId(true));
    }

    @Test
    void testFormat_rendersEveryOption() {
        String formatted = new FileParsingOptions().standardizeNames(true).overwrite(true).tailFile(false).format();
        assertEquals("STANDARDIZE_NAMES=true,OVERWRITE=true,TAIL_FILE=false,SEND_DDL=false,INCLUDE_TRANSACTION_ID=false", formatted);
    }

    @Test
    void testParse_readsEveryOption() {
        FileParsingOptions options = FileParsingOptions.parse(
                "STANDARDIZE_NAMES=true,OVERWRITE=false,TAIL_FILE=true,SEND_DDL=true,INCLUDE_TRANSACTION_ID=true");
        assertTrue(options.isStandardizeNames());
        assertFalse(options.isOverwrite());
        assertTrue(options.isTailFile());
        assertTrue(options.isSendDdl());
        assertTrue(options.isIncludeTransactionId());
    }

    @Test
    void testParse_isCaseInsensitiveOnKeys() {
        assertTrue(FileParsingOptions.parse("send_ddl=true").isSendDdl());
    }

    @Test
    void testParse_clearsTailFileWhenOverwriteIsSet() {
        FileParsingOptions options = FileParsingOptions.parse("OVERWRITE=true,TAIL_FILE=true");
        assertTrue(options.isOverwrite());
        assertFalse(options.isTailFile());
    }

    @Test
    void testParse_withNullExpressionReturnsDefaults() {
        assertTrue(FileParsingOptions.parse(null).isTailFile());
    }

    @Test
    void testParse_ignoresEntriesWithoutValue() {
        assertFalse(FileParsingOptions.parse("SEND_DDL").isSendDdl());
    }

    @Test
    void testParse_ignoresUnknownKeys() {
        assertFalse(FileParsingOptions.parse("SOMETHING_ELSE=true").isSendDdl());
    }

    @Test
    void testParse_treatsNonBooleanValueAsFalse() {
        assertFalse(FileParsingOptions.parse("SEND_DDL=yes").isSendDdl());
    }

    @Test
    void testFormatAndParse_roundTrip() {
        FileParsingOptions original = new FileParsingOptions().standardizeNames(true).sendDdl(true).includeTransactionId(true).tailFile(false);
        FileParsingOptions parsed = FileParsingOptions.parse(original.format());
        assertEquals(original.format(), parsed.format());
    }
}
