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

import java.security.KeyStore;

public class InMemoryKeyStoreRepository implements IKeyStoreRepository {
    public static final String PASSWORD = "changeit";
    private final KeyStore keyStore;
    private final boolean persistent;
    private int saveCount;
    private boolean failOnSave;

    private InMemoryKeyStoreRepository(KeyStore keyStore, boolean persistent) {
        this.keyStore = keyStore;
        this.persistent = persistent;
    }

    public static InMemoryKeyStoreRepository persistent() throws Exception {
        return new InMemoryKeyStoreRepository(newLoadedKeyStore(), true);
    }

    public static InMemoryKeyStoreRepository notPersistent() throws Exception {
        return new InMemoryKeyStoreRepository(newLoadedKeyStore(), false);
    }

    public static InMemoryKeyStoreRepository unloaded() throws Exception {
        return new InMemoryKeyStoreRepository(KeyStore.getInstance(SecurityConstants.KEYSTORE_TYPE_PKCS12), true);
    }

    @Override
    public KeyStore load() {
        return keyStore;
    }

    @Override
    public void save(KeyStore ks) {
        if (failOnSave) {
            throw new IllegalStateException("save failed");
        }
        saveCount++;
    }

    @Override
    public String getPassword() {
        return PASSWORD;
    }

    @Override
    public boolean isPersistent() {
        return persistent;
    }

    public int getSaveCount() {
        return saveCount;
    }

    public void failOnSave() {
        failOnSave = true;
    }

    private static KeyStore newLoadedKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(SecurityConstants.KEYSTORE_TYPE_PKCS12);
        keyStore.load(null, PASSWORD.toCharArray());
        return keyStore;
    }
}
