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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyStore;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the truststore from a file, or from the classpath when no file is set, and writes it back to the file.
 */
class LocalTrustStoreRepository implements IKeyStoreRepository {
    private static final Logger log = LoggerFactory.getLogger(LocalTrustStoreRepository.class);
    private final String fileName;
    private final URL url;
    private final Supplier<String> password;

    /**
     * @param password
     *            read on each use, so a subclass of {@link SecurityService} that overrides how the truststore password is found still supplies it
     */
    LocalTrustStoreRepository(String fileName, URL url, Supplier<String> password) {
        this.fileName = fileName;
        this.url = url;
        this.password = password;
    }

    @Override
    public KeyStore load() {
        try {
            KeyStore ks = KeyStore.getInstance(System.getProperty(SecurityConstants.SYSPROP_TRUSTSTORE_TYPE, KeyStore.getDefaultType()));
            if (fileName != null) {
                try (FileInputStream is = new FileInputStream(fileName)) {
                    ks.load(is, getPassword().toCharArray());
                }
            } else if (url != null) {
                try (InputStream is = url.openStream()) {
                    ks.load(is, getPassword().toCharArray());
                }
            }
            return ks;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save(KeyStore trustStore) throws Exception {
        if (fileName != null) {
            log.info("Saving truststore {}", fileName);
            try (FileOutputStream os = new FileOutputStream(fileName)) {
                trustStore.store(os, getPassword().toCharArray());
            }
        }
    }

    @Override
    public String getPassword() {
        return password.get();
    }

    @Override
    public boolean isPersistent() {
        return fileName != null;
    }
}
