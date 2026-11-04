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
package org.jumpmind.security.aws;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.codec.binary.Base64;
import org.jumpmind.security.SecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrappedDataKeySourceTest {
    private static final byte[] WRAPPED_KEY = "wrapped-data-key".getBytes(StandardCharsets.UTF_8);
    @TempDir
    Path directory;

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY);
        System.clearProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY_FILE);
        System.clearProperty(SecurityConstants.AWS_SECRETS_MANAGER_PREFIX);
    }

    @Test
    void testFromSystemProperties_defaultsToSecretsManager() {
        WrappedDataKeySource source = WrappedDataKeySource.fromSystemProperties();
        assertInstanceOf(SecretsManagerWrappedDataKeySource.class, source);
        assertEquals("AWS Secrets Manager secret symmetricds/kms-wrapped-key", source.describe());
    }

    @Test
    void testFromSystemProperties_withPrefixNamesSecretWithIt() {
        System.setProperty(SecurityConstants.AWS_SECRETS_MANAGER_PREFIX, "prod/sym/");
        assertEquals("AWS Secrets Manager secret prod/sym/kms-wrapped-key", WrappedDataKeySource.fromSystemProperties().describe());
    }

    @Test
    void testFromSystemProperties_withInlineKeyReadsIt() throws Exception {
        System.setProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY, Base64.encodeBase64String(WRAPPED_KEY));
        WrappedDataKeySource source = WrappedDataKeySource.fromSystemProperties();
        assertInstanceOf(LocalWrappedDataKeySource.class, source);
        assertArrayEquals(WRAPPED_KEY, source.read().get());
    }

    @Test
    void testFromSystemProperties_withFileUsesIt() {
        Path file = directory.resolve("kms-wrapped.key");
        System.setProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY_FILE, file.toString());
        WrappedDataKeySource source = WrappedDataKeySource.fromSystemProperties();
        assertInstanceOf(LocalWrappedDataKeySource.class, source);
        assertEquals(file.toAbsolutePath().toString(), source.describe());
    }
}
