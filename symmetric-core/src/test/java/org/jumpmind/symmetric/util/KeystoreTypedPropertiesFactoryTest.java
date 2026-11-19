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
package org.jumpmind.symmetric.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jumpmind.properties.TypedProperties;
import org.jumpmind.security.ISecurityService;
import org.jumpmind.symmetric.common.SystemConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeystoreTypedPropertiesFactoryTest {
    private static final String PARAMETER = "db.password";
    private static final String KEYSTORE_KEY = "corp-000.db.password";
    @TempDir
    Path directory;
    private ISecurityService securityService;
    private File propertiesFile;
    private KeystoreTypedPropertiesFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(SystemConstants.SYSPROP_KEYSTORE_PARAMETERS, PARAMETER);
        securityService = mock(ISecurityService.class);
        propertiesFile = directory.resolve("corp-000.properties").toFile();
        Files.writeString(propertiesFile.toPath(), "engine.name=corp-000\n" + PARAMETER + "=ks:" + KEYSTORE_KEY + "\n");
        factory = new KeystoreTypedPropertiesFactory(securityService);
        factory.init(propertiesFile, null);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SystemConstants.SYSPROP_KEYSTORE_PARAMETERS);
    }

    @Test
    void testReload_replacesKeystoreReferenceWithEntry() throws Exception {
        when(securityService.getKeystoreEntry(KEYSTORE_KEY)).thenReturn("s3cret");
        TypedProperties properties = factory.reload(propertiesFile);
        assertEquals("s3cret", properties.get(PARAMETER));
    }

    @Test
    void testReload_throwsWhenKeystoreEntryMissing() throws Exception {
        when(securityService.getKeystoreEntry(KEYSTORE_KEY)).thenReturn(null);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> factory.reload(propertiesFile));
        assertEquals("Parameter db.password is set to ks:corp-000.db.password, but there is no keystore entry named corp-000.db.password", exception
                .getMessage());
    }
}
