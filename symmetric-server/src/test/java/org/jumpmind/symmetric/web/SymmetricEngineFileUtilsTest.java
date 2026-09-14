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
package org.jumpmind.symmetric.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SymmetricEngineFileUtilsTest {
    @TempDir
    File tempDir;

    private TypedProperties fullyPopulatedEnvProps() {
        TypedProperties envProps = new TypedProperties();
        envProps.setProperty(DataSourceProperties.DB_POOL_URL, "jdbc:h2:mem:test");
        envProps.setProperty(DataSourceProperties.DB_POOL_DRIVER, "org.h2.Driver");
        envProps.setProperty(ParameterConstants.REGISTRATION_URL, "http://localhost:31415/sync/root");
        envProps.setProperty(ParameterConstants.NODE_GROUP_ID, "client");
        envProps.setProperty(ParameterConstants.EXTERNAL_ID, "001");
        envProps.setProperty(ParameterConstants.SYNC_URL, "http://localhost:31415/sync/engine1");
        envProps.setProperty(ParameterConstants.ENGINE_NAME, "engine1");
        return envProps;
    }

    @Test
    void isEnginePossibleFromEnvironmentVars_allMandatoryParamsPresent_returnsTrue() {
        assertTrue(SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(fullyPopulatedEnvProps()));
    }

    @Test
    void isEnginePossibleFromEnvironmentVars_missingDbPoolUrl_returnsFalse() {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.remove(DataSourceProperties.DB_POOL_URL);
        assertFalse(SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps));
    }

    @Test
    void isEnginePossibleFromEnvironmentVars_blankDbPoolUrl_returnsFalse() {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.setProperty(DataSourceProperties.DB_POOL_URL, "   ");
        assertFalse(SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps));
    }

    @Test
    void isEnginePossibleFromEnvironmentVars_missingDbPoolDriver_returnsFalse() {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.remove(DataSourceProperties.DB_POOL_DRIVER);
        assertFalse(SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps));
    }

    @Test
    void isEnginePossibleFromEnvironmentVars_missingNodeGroupId_returnsFalse() {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.remove(ParameterConstants.NODE_GROUP_ID);
        assertFalse(SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps));
    }

    @Test
    void isEnginePossibleFromEnvironmentVars_missingExternalId_returnsFalse() {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.remove(ParameterConstants.EXTERNAL_ID);
        assertFalse(SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps));
    }

    @Test
    void isEnginePossibleFromEnvironmentVars_missingSyncUrl_returnsFalse() {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.remove(ParameterConstants.SYNC_URL);
        assertFalse(SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps));
    }

    @Test
    void isEnginePossibleFromEnvironmentVars_registrationUrlKeyAbsent_returnsFalse() {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.remove(ParameterConstants.REGISTRATION_URL);
        assertFalse(SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps));
    }

    @Test
    void isEnginePossibleFromEnvironmentVars_registrationUrlKeyPresentButBlank_returnsTrue() {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.setProperty(ParameterConstants.REGISTRATION_URL, "");
        assertTrue(SymmetricEngineFileUtils.isEnginePossibleFromEnvironmentVars(envProps));
    }

    @Test
    void createEngineFileFromEnvironmentVars_createsFileNamedAfterEngineName() {
        File enginesDir = new File(tempDir, "engines");
        File engineFile = SymmetricEngineFileUtils.createEngineFileFromEnvironmentVars(enginesDir, fullyPopulatedEnvProps());
        assertEquals("engine1.properties", engineFile.getName());
        assertTrue(engineFile.exists());
    }

    @Test
    void createEngineFileFromEnvironmentVars_enginesDirMissing_createsDirectory() {
        File enginesDir = new File(tempDir, "does-not-exist-yet");
        assertFalse(enginesDir.exists());
        SymmetricEngineFileUtils.createEngineFileFromEnvironmentVars(enginesDir, fullyPopulatedEnvProps());
        assertTrue(enginesDir.exists());
    }

    @Test
    void createEngineFileFromEnvironmentVars_writesMandatoryParams() throws IOException {
        File enginesDir = new File(tempDir, "engines");
        File engineFile = SymmetricEngineFileUtils.createEngineFileFromEnvironmentVars(enginesDir, fullyPopulatedEnvProps());
        Properties written = loadProperties(engineFile);
        assertEquals("jdbc:h2:mem:test", written.getProperty(DataSourceProperties.DB_POOL_URL));
        assertEquals("org.h2.Driver", written.getProperty(DataSourceProperties.DB_POOL_DRIVER));
        assertEquals("http://localhost:31415/sync/root", written.getProperty(ParameterConstants.REGISTRATION_URL));
        assertEquals("client", written.getProperty(ParameterConstants.NODE_GROUP_ID));
        assertEquals("001", written.getProperty(ParameterConstants.EXTERNAL_ID));
        assertEquals("http://localhost:31415/sync/engine1", written.getProperty(ParameterConstants.SYNC_URL));
        assertEquals("engine1", written.getProperty(ParameterConstants.ENGINE_NAME));
    }

    @Test
    void createEngineFileFromEnvironmentVars_includesOptionalParamWhenPresent() throws IOException {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.setProperty(DataSourceProperties.DB_POOL_USER, "sym");
        File enginesDir = new File(tempDir, "engines");
        File engineFile = SymmetricEngineFileUtils.createEngineFileFromEnvironmentVars(enginesDir, envProps);
        Properties written = loadProperties(engineFile);
        assertEquals("sym", written.getProperty(DataSourceProperties.DB_POOL_USER));
    }

    @Test
    void createEngineFileFromEnvironmentVars_excludesOptionalParamWhenAbsent() throws IOException {
        File enginesDir = new File(tempDir, "engines");
        File engineFile = SymmetricEngineFileUtils.createEngineFileFromEnvironmentVars(enginesDir, fullyPopulatedEnvProps());
        Properties written = loadProperties(engineFile);
        assertNull(written.getProperty(DataSourceProperties.DB_POOL_USER));
    }

    @Test
    void createEngineFileFromEnvironmentVars_excludesOptionalParamWhenBlank() throws IOException {
        TypedProperties envProps = fullyPopulatedEnvProps();
        envProps.setProperty(DataSourceProperties.DB_POOL_USER, "   ");
        File enginesDir = new File(tempDir, "engines");
        File engineFile = SymmetricEngineFileUtils.createEngineFileFromEnvironmentVars(enginesDir, envProps);
        Properties written = loadProperties(engineFile);
        assertNull(written.getProperty(DataSourceProperties.DB_POOL_USER));
    }

    @Test
    void createEngineFileFromEnvironmentVars_calledTwiceWithSameEngineName_overwritesFile() throws IOException {
        File enginesDir = new File(tempDir, "engines");
        TypedProperties envProps = fullyPopulatedEnvProps();
        SymmetricEngineFileUtils.createEngineFileFromEnvironmentVars(enginesDir, envProps);
        envProps.setProperty(ParameterConstants.EXTERNAL_ID, "002");
        File engineFile = SymmetricEngineFileUtils.createEngineFileFromEnvironmentVars(enginesDir, envProps);
        Properties written = loadProperties(engineFile);
        assertEquals("002", written.getProperty(ParameterConstants.EXTERNAL_ID));
    }

    private Properties loadProperties(File file) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
        }
        return props;
    }
}
