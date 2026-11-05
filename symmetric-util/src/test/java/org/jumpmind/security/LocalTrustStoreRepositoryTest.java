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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalTrustStoreRepositoryTest {
    private static final String PASSWORD = "changeit";
    private static final String ALIAS = "trusted";
    @TempDir
    Path directory;
    private Path trustStoreFile;
    private Certificate certificate;

    @BeforeEach
    void setUp() throws Exception {
        trustStoreFile = directory.resolve("cacerts");
        try (InputStream in = getClass().getResourceAsStream("trusted-cert.pem")) {
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    @Test
    void testLoad_readsTrustStoreFile() throws Exception {
        writeTrustStore(trustStoreFile);
        KeyStore trustStore = new LocalTrustStoreRepository(trustStoreFile.toString(), null, () -> PASSWORD).load();
        assertEquals(certificate, trustStore.getCertificate(ALIAS));
    }

    @Test
    void testLoad_withoutFileReadsClasspathUrl() throws Exception {
        writeTrustStore(trustStoreFile);
        KeyStore trustStore = new LocalTrustStoreRepository(null, trustStoreFile.toUri().toURL(), () -> PASSWORD).load();
        assertEquals(certificate, trustStore.getCertificate(ALIAS));
    }

    @Test
    void testLoad_withWrongPasswordThrows() throws Exception {
        writeTrustStore(trustStoreFile);
        LocalTrustStoreRepository repository = new LocalTrustStoreRepository(trustStoreFile.toString(), null, () -> "wrong");
        assertThrows(RuntimeException.class, repository::load);
    }

    @Test
    void testSave_writesTrustStoreFile() throws Exception {
        LocalTrustStoreRepository repository = new LocalTrustStoreRepository(trustStoreFile.toString(), null, () -> PASSWORD);
        KeyStore trustStore = newTrustStore();
        trustStore.setCertificateEntry(ALIAS, certificate);
        repository.save(trustStore);
        assertEquals(certificate, readTrustStore(trustStoreFile).getCertificate(ALIAS));
    }

    @Test
    void testSave_withoutFileLeavesClasspathTrustStoreUnchanged() throws Exception {
        writeTrustStore(trustStoreFile);
        byte[] original = Files.readAllBytes(trustStoreFile);
        LocalTrustStoreRepository repository = new LocalTrustStoreRepository(null, trustStoreFile.toUri().toURL(), () -> PASSWORD);
        repository.save(newTrustStore());
        assertArrayEquals(original, Files.readAllBytes(trustStoreFile));
    }

    @Test
    void testGetPassword_readsSupplierOnEachCall() {
        AtomicReference<String> password = new AtomicReference<>("first");
        LocalTrustStoreRepository repository = new LocalTrustStoreRepository(trustStoreFile.toString(), null, password::get);
        password.set("second");
        assertEquals("second", repository.getPassword());
    }

    @Test
    void testIsPersistent_withFile() {
        assertTrue(new LocalTrustStoreRepository(trustStoreFile.toString(), null, () -> PASSWORD).isPersistent());
    }

    @Test
    void testIsPersistent_withoutFile() throws Exception {
        assertFalse(new LocalTrustStoreRepository(null, trustStoreFile.toUri().toURL(), () -> PASSWORD).isPersistent());
    }

    private void writeTrustStore(Path file) throws Exception {
        KeyStore trustStore = newTrustStore();
        trustStore.setCertificateEntry(ALIAS, certificate);
        try (FileOutputStream out = new FileOutputStream(file.toFile())) {
            trustStore.store(out, PASSWORD.toCharArray());
        }
    }

    private KeyStore readTrustStore(Path file) throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            trustStore.load(in, PASSWORD.toCharArray());
        }
        return trustStore;
    }

    private KeyStore newTrustStore() throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        return trustStore;
    }
}
