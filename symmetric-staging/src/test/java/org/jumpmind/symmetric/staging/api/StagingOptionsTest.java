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
package org.jumpmind.symmetric.staging.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class StagingOptionsTest {
    @Test
    void defaults_setExpectedValues() {
        StagingOptions options = StagingOptions.defaults();
        assertEquals(64L * 1024L, options.getMemoryThresholdBytes());
        assertEquals(Charset.defaultCharset(), options.getCharset());
        assertFalse(options.isEncryptionEnabled());
        assertFalse(options.isCompressionEnabled());
        assertTrue(options.isByteExact());
    }

    @Test
    void plain_isByteExact() {
        StagingOptions options = StagingOptions.plain();
        assertTrue(options.isByteExact());
    }

    @Test
    void byteExact_falseWhenEncryptionOn() {
        StagingOptions options = StagingOptions.builder().withEncryptionEnabled(true).build();
        assertFalse(options.isByteExact());
    }

    @Test
    void byteExact_falseWhenCompressionOn() {
        StagingOptions options = StagingOptions.builder().withCompressionEnabled(true).build();
        assertFalse(options.isByteExact());
    }

    @Test
    void explicitCharset_returnedAsIs() {
        StagingOptions options = StagingOptions.builder()
                .withCharset(StandardCharsets.ISO_8859_1)
                .build();
        assertEquals(StandardCharsets.ISO_8859_1, options.getCharset());
        assertTrue(options.hasExplicitCharset());
    }
}
