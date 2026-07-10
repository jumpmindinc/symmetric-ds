/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.util.DataSourceProperties;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SymmetricEngineFileUtils {
    private static final Logger log = LoggerFactory.getLogger(SymmetricEngineFileUtils.class);
    // Parameters for creating engine files from environment variables:
    public static final String[] STARTUP_DB_OBJECTS_SETUP_PARAMS = new String[] {
            ParameterConstants.TRIGGER_CAPTURE_DDL_CHANGES,
            ParameterConstants.POSTGRES_TRIGGER_CAPTURE_TRUNCATE,
            ParameterConstants.TRIGGER_CAPTURE_DDL_CHECK_TRIGGER_HIST,
            ParameterConstants.TRIGGER_CAPTURE_DDL_DELIMITER,
            DataSourceProperties.DB_POOL_USER,
            DataSourceProperties.DB_POOL_URL,
            ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_URL };
    public static final String[] ENGINE_MANDATORY_STARTUP_PARAMETERS = {
            DataSourceProperties.DB_POOL_URL,
            DataSourceProperties.DB_POOL_DRIVER,
            ParameterConstants.REGISTRATION_URL,
            ParameterConstants.NODE_GROUP_ID,
            ParameterConstants.EXTERNAL_ID,
            ParameterConstants.SYNC_URL
    };
    public static final String[] ENGINE_OPTIONAL_STARTUP_PARAMETERS = {
            DataSourceProperties.DB_POOL_USER,
            DataSourceProperties.DB_POOL_PASSWORD,
            DataSourceProperties.DB_POOL_CONNECTION_PROPERTIES,
            DataSourceProperties.DB_POOL_VALIDATION_QUERY,
            DataSourceProperties.DB_POOL_INIT_SQL,
            ParameterConstants.AUTO_REGISTER_ENABLED,
            ParameterConstants.NODE_LOAD_ONLY,
            ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_DRIVER,
            ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_URL,
            ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_USER,
            ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_PASSWORD,
            ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_CONNECTION_PROPERTIES,
            ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_VALIDATION_QUERY,
            ParameterConstants.LOAD_ONLY_PROPERTY_PREFIX + DataSourceProperties.DB_POOL_INIT_SQL
    };

    static boolean isEnginePossibleFromEnvironmentVars(TypedProperties envProps) {
        for (String param : ENGINE_MANDATORY_STARTUP_PARAMETERS) {
            if (param.equals(ParameterConstants.REGISTRATION_URL)) {
                if (!envProps.containsKey(param)) {
                    return false;
                }
            } else if (StringUtils.isBlank(envProps.getProperty(param))) {
                return false;
            }
        }
        return true;
    }

    static File createEngineFileFromEnvironmentVars(File enginesDir, TypedProperties envProps) {
        Properties props = new Properties();
        for (String param : ENGINE_MANDATORY_STARTUP_PARAMETERS) {
            props.setProperty(param, envProps.getProperty(param));
        }
        for (String param : ENGINE_OPTIONAL_STARTUP_PARAMETERS) {
            String value = envProps.getProperty(param);
            if (StringUtils.isNotBlank(value)) {
                props.setProperty(param, value);
            }
        }
        String engineName = envProps.getProperty(ParameterConstants.ENGINE_NAME);
        props.setProperty(ParameterConstants.ENGINE_NAME, engineName);
        enginesDir.mkdirs();
        File engineFile = new File(enginesDir, engineName + ".properties");
        try (FileOutputStream fos = new FileOutputStream(engineFile)) {
            props.store(fos, "Auto-generated from environment variables by SymmetricDS version " + Version.version());
            log.info("Created engine properties file {} from environment variables", engineFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create engine properties file " + engineFile, e);
        }
        return engineFile;
    }
}
