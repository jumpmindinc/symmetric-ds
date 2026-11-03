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
package org.jumpmind.security;

import javax.crypto.SecretKey;

/**
 * Supplies the symmetric secret key used by {@link ISecurityService#encrypt(String)}, {@link ISecurityService#decrypt(String)} and
 * {@link ISecurityService#getCipher(int)}. This is the implementor side of a Bridge: {@link SecurityService} owns the cipher operations and delegates to a
 * provider for where the key comes from, so a backend such as a cloud KMS can supply the key without a local keystore.
 * <p>
 * Implementations may create and persist a key on first use. Callers cache the result by {@link #getKeyIdentity()}, so each call may be expensive.
 */
public interface ISecretKeyProvider {
    public SecretKey getSecretKey() throws Exception;

    /**
     * Identifies the key this provider supplies. Providers that supply the same key return equal identities, so the key is fetched once per JVM and shared
     * between them. The default treats every instance of the same class as supplying the same key.
     */
    public default Object getKeyIdentity() {
        return getClass();
    }
}
