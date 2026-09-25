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
package org.jumpmind.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnvironmentSpecificPropertiesTest {
    private static final String SYSTEM_PROPERTY_NAME = "environmentSpecificPropertiesTest.env";
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("environment-specific-properties-test");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(SYSTEM_PROPERTY_NAME);
        // EnvironmentSpecificProperties never closes the stream it opens from the URL, so on Windows
        // the backing file can still be locked here; deletion is best-effort and failures are ignored.
        try {
            FileUtils.deleteDirectory(tempDir.toFile());
        } catch (Exception e) {
            // Best-effort cleanup; a locked file on Windows should not fail the test.
        }
    }

    @Test
    void testActivate_stripsMatchingEnvironmentPrefix() throws Exception {
        URL fileUrl = writeProperties("dev.db.url=devUrl\nprod.db.url=prodUrl\ncommon.key=commonValue\n");
        EnvironmentSpecificProperties props = new EnvironmentSpecificProperties(fileUrl, null, "dev");
        assertEquals("devUrl", props.getProperty("db.url"));
        assertEquals("prodUrl", props.getProperty("prod.db.url"));
        assertEquals("commonValue", props.getProperty("common.key"));
    }

    @Test
    void testActivate_stripsNestedPrefixesFromMultipleTokens() throws Exception {
        URL fileUrl = writeProperties("dev.test.key=value\n");
        EnvironmentSpecificProperties props = new EnvironmentSpecificProperties(fileUrl, null, "dev", "test");
        assertEquals("value", props.getProperty("key"));
    }

    @Test
    void testActivate_systemPropertyActivatesAdditionalEnvironment() throws Exception {
        System.setProperty(SYSTEM_PROPERTY_NAME, "staging");
        URL fileUrl = writeProperties("staging.key=stagingValue\n");
        EnvironmentSpecificProperties props = new EnvironmentSpecificProperties(fileUrl, SYSTEM_PROPERTY_NAME);
        assertEquals("stagingValue", props.getProperty("key"));
    }

    @Test
    void testActivate_systemPropertyFallsBackToFileProperty() throws Exception {
        URL fileUrl = writeProperties(SYSTEM_PROPERTY_NAME + "=staging\nstaging.foo=bar\n");
        EnvironmentSpecificProperties props = new EnvironmentSpecificProperties(fileUrl, SYSTEM_PROPERTY_NAME);
        assertEquals("bar", props.getProperty("foo"));
        assertEquals("staging", props.getProperty(SYSTEM_PROPERTY_NAME));
    }

    @Test
    void testLoad_inputStreamThrowsNotImplementedException() throws Exception {
        URL fileUrl = writeProperties("key=value\n");
        EnvironmentSpecificProperties props = new EnvironmentSpecificProperties(fileUrl, null);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);
        assertThrows(NotImplementedException.class, () -> props.load(inputStream));
    }

    @Test
    void testLoad_readerThrowsNotImplementedException() throws Exception {
        URL fileUrl = writeProperties("key=value\n");
        EnvironmentSpecificProperties props = new EnvironmentSpecificProperties(fileUrl, null);
        StringReader reader = new StringReader("");
        assertThrows(NotImplementedException.class, () -> props.load(reader));
    }

    @Test
    void testGetPropertyKeysThatBeginWith() throws Exception {
        URL fileUrl = writeProperties("db.url=url\ndb.user=user\nother.key=value\n");
        EnvironmentSpecificProperties props = new EnvironmentSpecificProperties(fileUrl, null);
        assertEquals(Set.of("db.url", "db.user"), props.getPropertyKeysThatBeginWith("db."));
    }

    private URL writeProperties(String content) throws Exception {
        Path file = tempDir.resolve("test-" + System.nanoTime() + ".properties");
        Files.writeString(file, content);
        return file.toUri().toURL();
    }
}
