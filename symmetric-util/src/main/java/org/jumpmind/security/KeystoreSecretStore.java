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

import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;

import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps each value as a secret key entry in the local keystore, saving the keystore after every change. Deleting removes any entry with the alias, including
 * private keys and certificates.
 */
public class KeystoreSecretStore implements ISecretStore {
    private static final String ENTRY_KEY_ALGORITHM = "AES";
    private static final Logger log = LoggerFactory.getLogger(KeystoreSecretStore.class);
    private final IKeyStoreRepository keyStoreRepository;

    public KeystoreSecretStore(IKeyStoreRepository keyStoreRepository) {
        this.keyStoreRepository = keyStoreRepository;
    }

    @Override
    public String getEntry(String alias) throws Exception {
        Key entry = keyStoreRepository.load().getKey(alias, getPassword());
        return entry == null ? null : new String(entry.getEncoded());
    }

    @Override
    public void setEntry(String alias, String value) throws Exception {
        KeyStore ks = keyStoreRepository.load();
        ks.setKeyEntry(alias, new SecretKeySpec(value.getBytes(), ENTRY_KEY_ALGORITHM), getPassword(), null);
        keyStoreRepository.save(ks);
    }

    @Override
    public void deleteEntry(String alias) throws Exception {
        KeyStore ks = keyStoreRepository.load();
        try {
            ks.deleteEntry(alias);
        } catch (KeyStoreException e) {
            log.info("Unable to delete keystore entry with name {}", alias);
        }
        keyStoreRepository.save(ks);
    }

    private char[] getPassword() {
        return keyStoreRepository.getPassword().toCharArray();
    }
}
