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
package org.jumpmind.symmetric.db.hsqldb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;

import org.hsqldb.types.BinaryData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HsqlDbTriggerTest {
    private HsqlDbTrigger trigger;
    private StringBuilder out;

    @BeforeEach
    void setUp() {
        trigger = new HsqlDbTrigger();
        out = new StringBuilder();
    }

    @Test
    void testAppendVirtualTableStringValue_withBinaryData() {
        byte[] bytes = "hello".getBytes(Charset.defaultCharset());
        BinaryData value = new BinaryData(bytes, false);
        trigger.appendVirtualTableStringValue(value, out);
        assertEquals("'aGVsbG8='", out.toString());
    }

    @Test
    void testAppendVirtualTableStringValue_withEmptyBinaryData() {
        BinaryData value = new BinaryData(new byte[0], false);
        trigger.appendVirtualTableStringValue(value, out);
        assertEquals("''", out.toString());
    }

    @Test
    void testAppendVirtualTableStringValue_withNull() {
        trigger.appendVirtualTableStringValue(null, out);
        assertEquals("null", out.toString());
    }

    @Test
    void testAppendVirtualTableStringValue_withString() {
        trigger.appendVirtualTableStringValue("abc", out);
        assertEquals("'abc'", out.toString());
    }

    @Test
    void testAppendVirtualTableStringValue_escapesQuoteInString() {
        trigger.appendVirtualTableStringValue("O'Brien", out);
        assertEquals("'O''Brien'", out.toString());
    }

    @Test
    void testAppendVirtualTableStringValue_withNumber() {
        trigger.appendVirtualTableStringValue(42, out);
        assertEquals("42", out.toString());
    }

    @Test
    void testAppendVirtualTableStringValue_withBoolean() {
        trigger.appendVirtualTableStringValue(true, out);
        assertEquals("true", out.toString());
    }

    @Test
    void testAppendVirtualTableStringValue_returnsEncodedBinaryValue() {
        byte[] bytes = "hello".getBytes(Charset.defaultCharset());
        BinaryData value = new BinaryData(bytes, false);
        assertEquals("aGVsbG8=", trigger.appendVirtualTableStringValue(value, out));
    }

    @Test
    void testAppendVirtualTableStringValue_returnsOriginalStringValue() {
        assertEquals("abc", trigger.appendVirtualTableStringValue("abc", out));
    }
}
