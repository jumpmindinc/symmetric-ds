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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jumpmind.exception.IoException;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ITypedPropertiesFactory;
import org.jumpmind.util.AppUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class TypedPropertiesFactory implements ITypedPropertiesFactory {
    protected File propertiesFile;
    protected Properties properties;

    public TypedPropertiesFactory() {
    }

    public void init(File propertiesFile, Properties properties) {
        this.propertiesFile = propertiesFile;
        this.properties = properties;
    }

    public TypedProperties reload() {
        PropertiesFactoryBean factoryBean = new PropertiesFactoryBean();
        factoryBean.setIgnoreResourceNotFound(true);
        factoryBean.setLocalOverride(true);
        factoryBean.setSingleton(false);
        factoryBean.setProperties(properties);
        factoryBean.setLocations(buildLocations(propertiesFile));
        try {
            TypedProperties properties = new TypedProperties(factoryBean.getObject());
            SymmetricUtils.replaceSystemAndEnvironmentVariables(properties);
            return properties;
        } catch (IOException e) {
            throw new IoException(e);
        }
    }

    @Override
    public TypedProperties reload(File propFile) {
        TypedProperties typedProperties = new TypedProperties(propFile);
        return typedProperties;
    }

    @Override
    public TypedProperties reload(Properties properties) {
        TypedProperties typedProperties = new TypedProperties(properties);
        return typedProperties;
    }

    @Override
    public void save(Properties props, File propFile, String comments) throws IOException {
        try (FileOutputStream os = new FileOutputStream(propFile)) {
            props.store(os, comments);
        }
    }

    protected Resource[] buildLocations(File propertiesFile) {
        /*
         * System properties always override the properties found in these files. System properties are merged in the parameter service.
         */
        List<Resource> resources = new ArrayList<Resource>();
        resources.add(new ClassPathResource("/symmetric-default.properties"));
        resources.add(new ClassPathResource("/symmetric-console-default.properties"));
        resources.add(new FileSystemResource(AppUtils.getSymHome() + "/conf/symmetric.properties"));
        resources.add(new ClassPathResource("/symmetric.properties"));
        resources.add(new ClassPathResource("/symmetric-console-default.properties"));
        resources.add(new ClassPathResource("/symmetric-override.properties"));
        if (propertiesFile != null && propertiesFile.exists()) {
            resources.add(new FileSystemResource(propertiesFile.getAbsolutePath()));
        }
        return resources.toArray(new Resource[resources.size()]);
    }
}
