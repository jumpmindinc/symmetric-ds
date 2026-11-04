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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;

import org.jumpmind.security.SecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

class AwsSettingsTest {
    private static final String REGION = "us-east-2";

    @AfterEach
    void tearDown() {
        System.clearProperty(SecurityConstants.AWS_KMS_KEY_ID);
        System.clearProperty(SecurityConstants.AWS_KMS_REGION);
        System.clearProperty(SecurityConstants.AWS_KMS_ENDPOINT);
        System.clearProperty(SecurityConstants.AWS_SECRETS_MANAGER_PREFIX);
        System.clearProperty(SecurityConstants.AWS_SECRETS_MANAGER_ENDPOINT);
    }

    @Test
    void testGetKmsKeyId_trimsValue() {
        System.setProperty(SecurityConstants.AWS_KMS_KEY_ID, " alias/symmetricds ");
        assertEquals("alias/symmetricds", AwsSettings.getKmsKeyId());
    }

    @Test
    void testGetKmsKeyId_returnsNullWhenBlank() {
        System.setProperty(SecurityConstants.AWS_KMS_KEY_ID, " ");
        assertNull(AwsSettings.getKmsKeyId());
    }

    @Test
    void testGetSecretsManagerPrefix_defaultsToSymmetricds() {
        assertEquals(AwsSettings.DEFAULT_SECRETS_MANAGER_PREFIX, AwsSettings.getSecretsManagerPrefix());
    }

    @Test
    void testGetSecretsManagerPrefix_trimsConfiguredPrefix() {
        System.setProperty(SecurityConstants.AWS_SECRETS_MANAGER_PREFIX, " prod/sym/ ");
        assertEquals("prod/sym/", AwsSettings.getSecretsManagerPrefix());
    }

    @Test
    void testBuildKmsClient_usesRegionAndKmsEndpoint() {
        System.setProperty(SecurityConstants.AWS_KMS_REGION, REGION);
        System.setProperty(SecurityConstants.AWS_KMS_ENDPOINT, "http://localhost:4566");
        try (KmsClient client = AwsSettings.buildKmsClient()) {
            assertEquals(Region.of(REGION), client.serviceClientConfiguration().region());
            assertEquals(URI.create("http://localhost:4566"), client.serviceClientConfiguration().endpointOverride().get());
        }
    }

    @Test
    void testBuildSecretsManagerClient_usesKmsRegionAndSecretsManagerEndpoint() {
        System.setProperty(SecurityConstants.AWS_KMS_REGION, REGION);
        System.setProperty(SecurityConstants.AWS_SECRETS_MANAGER_ENDPOINT, "http://localhost:4567");
        try (SecretsManagerClient client = AwsSettings.buildSecretsManagerClient()) {
            assertEquals(Region.of(REGION), client.serviceClientConfiguration().region());
            assertEquals(URI.create("http://localhost:4567"), client.serviceClientConfiguration().endpointOverride().get());
        }
    }
}
