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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SymmetricModulesPropertiesTest {
    private static final String AWS_SDK_GROUP = "software.amazon.awssdk:";
    // The awssqs module matches the SQS client that symmetric-pro compiles against, not the bundled AWS SDK
    private static final Set<String> MODULES_WITH_OWN_AWS_SDK_VERSION = Set.of("awssqs");
    private Properties modules;

    @BeforeEach
    void setUp() throws Exception {
        modules = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/symmetric-modules.properties")) {
            modules.load(in);
        }
    }

    @Test
    void testAwsSdkJars_useAwsSdkVersion() {
        String awsSdkVersion = System.getProperty("awsSdkVersion");
        assertNotNull(awsSdkVersion);
        List<String> awsSdkJars = findAwsSdkJars();
        assertFalse(awsSdkJars.isEmpty());
        for (String jar : awsSdkJars) {
            assertEquals(awsSdkVersion, jar.substring(jar.lastIndexOf(':') + 1), jar);
        }
    }

    private List<String> findAwsSdkJars() {
        List<String> jars = new ArrayList<>();
        for (String module : modules.stringPropertyNames()) {
            if (MODULES_WITH_OWN_AWS_SDK_VERSION.contains(module)) {
                continue;
            }
            for (String jar : modules.getProperty(module).split(",")) {
                if (jar.trim().startsWith(AWS_SDK_GROUP)) {
                    jars.add(module + "=" + jar.trim());
                }
            }
        }
        return jars;
    }
}
