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
package org.jumpmind.symmetric.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.SystemConstants;
import org.jumpmind.util.AppUtils;

public class PropertiesUtil {
    public static final String PROPERTIES_FACTORY_CLASS_NAME = "properties.factory.class.name";

    public static String getEnginesDir() {
        String enginesDir = System.getProperty(SystemConstants.SYSPROP_ENGINES_DIR, AppUtils.getSymHome() + "/engines");
        new File(enginesDir).mkdirs();
        return enginesDir;
    }

    public static File findPropertiesFileForEngineWithName(String engineName) {
        return findPropertiesFileForEngineWithName(engineName, null);
    }

    public static File findPropertiesFileForEngineWithName(String engineName, Map<String, String> replacementValues) {
        if (replacementValues == null) {
            replacementValues = new HashMap<String, String>();
        }
        boolean isEmptyReplacementValues = replacementValues.isEmpty();
        File[] files = findEnginePropertiesFiles();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            Properties properties = new Properties();
            try (FileInputStream is = new FileInputStream(file)) {
                properties.load(is);
                TypedPropertiesFactory.replaceSystemAndEnvironmentVariables(properties);
                if (isEmptyReplacementValues) {
                    replacementValues.clear();
                    replacementValues.put("nodeGroupId", SymmetricUtils.substituteScripts(
                            properties.getProperty(ParameterConstants.NODE_GROUP_ID), replacementValues));
                    replacementValues.put("externalId", SymmetricUtils.substituteScripts(
                            properties.getProperty(ParameterConstants.EXTERNAL_ID), replacementValues));
                }
                if (engineName.equals(SymmetricUtils.substituteScripts(
                        properties.getProperty(ParameterConstants.ENGINE_NAME), replacementValues))) {
                    return file;
                }
            } catch (IOException ex) {
            }
        }
        return null;
    }

    public static File[] findEnginePropertiesFiles() {
        List<File> propFiles = new ArrayList<>();
        File enginesDir = new File(getEnginesDir());
        File[] files = enginesDir.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                if (file.getName().endsWith(".properties")) {
                    propFiles.add(file);
                }
            }
        }
        return propFiles.toArray(new File[propFiles.size()]);
    }

    public static ITypedPropertiesFactory createTypedPropertiesFactory(File propFile, Properties prop) {
        String propFactoryClassName = System.getProperties().getProperty(PROPERTIES_FACTORY_CLASS_NAME);
        ITypedPropertiesFactory factory = null;
        if (propFactoryClassName != null) {
            try {
                factory = (ITypedPropertiesFactory) Class.forName(propFactoryClassName).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            factory = new TypedPropertiesFactory();
        }
        factory.init(propFile, prop);
        return factory;
    }
}
