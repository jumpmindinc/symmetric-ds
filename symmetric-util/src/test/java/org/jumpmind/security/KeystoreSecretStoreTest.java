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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyStoreException;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeystoreSecretStoreTest {
    private static final String ALIAS = "db.password";
    private InMemoryKeyStoreRepository repository;
    private KeystoreSecretStore store;

    @BeforeEach
    void setUp() throws Exception {
        repository = InMemoryKeyStoreRepository.fileBased();
        store = new KeystoreSecretStore(repository);
    }

    @Test
    void testGetEntry_returnsValueFromSetEntry() throws Exception {
        store.setEntry(ALIAS, "s3cret");
        assertEquals("s3cret", store.getEntry(ALIAS));
    }

    @Test
    void testGetEntry_returnsNullWhenMissing() throws Exception {
        assertNull(store.getEntry(ALIAS));
        assertEquals(0, repository.getSaveCount());
    }

    @Test
    void testGetEntry_throwsWhenKeyStoreNotLoaded() throws Exception {
        KeystoreSecretStore unloadedStore = new KeystoreSecretStore(InMemoryKeyStoreRepository.unloaded());
        assertThrows(KeyStoreException.class, () -> unloadedStore.getEntry(ALIAS));
    }

    @Test
    void testSetEntry_savesKeyStore() throws Exception {
        store.setEntry(ALIAS, "s3cret");
        assertEquals(1, repository.getSaveCount());
    }

    @Test
    void testSetEntry_replacesExistingValue() throws Exception {
        store.setEntry(ALIAS, "first");
        store.setEntry(ALIAS, "second");
        assertEquals("second", store.getEntry(ALIAS));
    }

    @Test
    void testSetEntry_throwsWhenSaveFails() {
        repository.failOnSave();
        assertThrows(IllegalStateException.class, () -> store.setEntry(ALIAS, "s3cret"));
    }

    @Test
    void testDeleteEntry_removesValueAndSaves() throws Exception {
        repository.load().setKeyEntry(ALIAS, new SecretKeySpec("s3cret".getBytes(), "AES"), InMemoryKeyStoreRepository.PASSWORD.toCharArray(), null);
        store.deleteEntry(ALIAS);
        assertFalse(repository.load().containsAlias(ALIAS));
        assertEquals(1, repository.getSaveCount());
    }

    @Test
    void testDeleteEntry_whenKeyStoreRejectsDeleteStillSaves() throws Exception {
        InMemoryKeyStoreRepository unloadedRepository = InMemoryKeyStoreRepository.unloaded();
        new KeystoreSecretStore(unloadedRepository).deleteEntry(ALIAS);
        assertEquals(1, unloadedRepository.getSaveCount());
    }
}
