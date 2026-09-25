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
package org.jumpmind.symmetric.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class CountingSkippingWriterTest {
    @Test
    void testWrite_withZeroSkipCount_forwardsEverything() throws IOException {
        StringWriter delegate = new StringWriter();
        try (CountingSkippingWriter writer = new CountingSkippingWriter(delegate, 0)) {
            writer.write("hello world".toCharArray(), 0, 11);
            assertEquals("hello world", delegate.toString());
            assertEquals(11, writer.getTotalCount());
        }
    }

    @Test
    void testWrite_withSkipCountWithinFirstBuffer_skipsPartial() throws IOException {
        StringWriter delegate = new StringWriter();
        try (CountingSkippingWriter writer = new CountingSkippingWriter(delegate, 6)) {
            writer.write("hello world".toCharArray(), 0, 11);
            assertEquals("world", delegate.toString());
            assertEquals(11, writer.getTotalCount());
        }
    }

    @Test
    void testWrite_withSkipCountSpanningMultipleWrites_skipsAcrossBoundary() throws IOException {
        StringWriter delegate = new StringWriter();
        try (CountingSkippingWriter writer = new CountingSkippingWriter(delegate, 8)) {
            writer.write("hello ".toCharArray(), 0, 6);
            writer.write("world".toCharArray(), 0, 5);
            assertEquals("rld", delegate.toString());
            assertEquals(11, writer.getTotalCount());
        }
    }

    @Test
    void testWrite_withSkipCountExactlyMatchingFirstBuffer_forwardsOnlySubsequentWrites() throws IOException {
        StringWriter delegate = new StringWriter();
        try (CountingSkippingWriter writer = new CountingSkippingWriter(delegate, 6)) {
            writer.write("hello ".toCharArray(), 0, 6);
            writer.write("world".toCharArray(), 0, 5);
            assertEquals("world", delegate.toString());
            assertEquals(11, writer.getTotalCount());
        }
    }

    @Test
    void testWrite_withSkipCountGreaterThanTotalContent_forwardsNothingButCountsAll() throws IOException {
        StringWriter delegate = new StringWriter();
        try (CountingSkippingWriter writer = new CountingSkippingWriter(delegate, 1000)) {
            writer.write("hello world".toCharArray(), 0, 11);
            assertEquals("", delegate.toString());
            assertEquals(11, writer.getTotalCount());
        }
    }

    @Test
    void testWrite_withOffset_honorsOffsetAndLength() throws IOException {
        StringWriter delegate = new StringWriter();
        try (CountingSkippingWriter writer = new CountingSkippingWriter(delegate, 2)) {
            char[] buffer = "xxhello worldxx".toCharArray();
            writer.write(buffer, 2, 11);
            assertEquals("llo world", delegate.toString());
            assertEquals(11, writer.getTotalCount());
        }
    }

    @Test
    void testFlushAndClose_delegateToUnderlyingWriter() throws IOException {
        StringWriter delegate = new StringWriter();
        try (CountingSkippingWriter writer = new CountingSkippingWriter(delegate, 0)) {
            writer.write("abc".toCharArray(), 0, 3);
            writer.flush();
        }
        assertEquals("abc", delegate.toString());
    }
}
