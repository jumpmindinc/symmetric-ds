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
package org.jumpmind.symmetric.db.clickhouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.jumpmind.symmetric.util.MavenArtifact;
import org.junit.jupiter.api.Test;

class ClickHouseModuleTest {
    private Properties loadModules() throws Exception {
        Properties modules = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/symmetric-modules.properties")) {
            assertNotNull(in);
            modules.load(in);
        }
        return modules;
    }

    @Test
    void testModuleInstallsTheDriverAndItsDependencies() throws Exception {
        List<MavenArtifact> artifacts = MavenArtifact.parseCsv(loadModules().getProperty("clickhouse"), "3.19.0");
        List<String> files = artifacts.stream().map(MavenArtifact::toFileName).collect(Collectors.toList());
        assertTrue(files.contains("clickhouse-jdbc-0.11.0.jar"), files.toString());
        assertTrue(files.contains("client-v2-0.11.0.jar"), files.toString());
        assertTrue(files.contains("jdbc-v2-0.11.0.jar"), files.toString());
        // The driver needs these, and the all jar is not used, so each one has to be installed
        assertTrue(files.contains("httpclient5-5.6.4.jar"), files.toString());
        assertTrue(files.contains("lz4-java-1.11.4.jar"), files.toString());
        assertTrue(files.contains("zstd-jni-1.5.7-23.jar"), files.toString());
    }

    @Test
    void testModuleDoesNotRepeatLibrariesThatAreAlreadyShipped() throws Exception {
        List<MavenArtifact> artifacts = MavenArtifact.parseCsv(loadModules().getProperty("clickhouse"), "3.19.0");
        List<String> names = artifacts.stream().map(MavenArtifact::getArtifactId).collect(Collectors.toList());
        for (String shipped : new String[] { "slf4j-api", "commons-lang3", "commons-codec", "commons-io", "guava", "asm" }) {
            assertFalse(names.contains(shipped), shipped + " is already part of the server");
        }
    }

    @Test
    void testDriverClass() throws Exception {
        assertEquals("com.clickhouse.jdbc.ClickHouseDriver", loadModules().getProperty("driver.clickhouse"));
    }
}
