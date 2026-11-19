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

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.security.SecurityConstants;

/**
 * Where the KMS-encrypted data key is kept. The key is useless without access to the KMS key, but it is the only copy of the secret key that encrypts the
 * node's values, so losing it loses those values.
 */
interface WrappedDataKeySource {
    Optional<byte[]> read() throws IOException;

    /**
     * @return false when a key was already saved, for example by another node that started at the same time
     */
    boolean writeIfAbsent(byte[] wrappedKey) throws IOException;

    /**
     * @return false when the key is set by configuration and cannot be saved here
     */
    boolean isWritable();

    /**
     * Saves the encrypted data key in place of the one saved before, after it is encrypted under a different KMS key.
     */
    void replace(byte[] wrappedKey) throws IOException;

    String describe();

    /**
     * @return the system property {@link SecurityConstants#AWS_KMS_WRAPPED_KEY} or the file {@link SecurityConstants#AWS_KMS_WRAPPED_KEY_FILE} when either is
     *         set, otherwise an AWS Secrets Manager secret that every node using the same secret name prefix shares
     */
    static WrappedDataKeySource fromSystemProperties() {
        String inlineKey = StringUtils.trimToNull(System.getProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY));
        String file = StringUtils.trimToNull(System.getProperty(SecurityConstants.AWS_KMS_WRAPPED_KEY_FILE));
        if (inlineKey != null || file != null) {
            return new LocalWrappedDataKeySource(inlineKey, file == null ? null : Paths.get(file));
        }
        return SecretsManagerWrappedDataKeySource.fromSystemProperties();
    }
}
