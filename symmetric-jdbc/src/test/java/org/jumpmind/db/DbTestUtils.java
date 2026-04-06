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
package org.jumpmind.db;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.JdbcDatabasePlatformFactory;
import org.jumpmind.db.sql.SqlTemplateSettings;
import org.jumpmind.db.util.DataSourceFactory;
import org.jumpmind.properties.EnvironmentSpecificProperties;
import org.jumpmind.security.SecurityServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class DbTestUtils {
    Logger logger = LoggerFactory.getLogger(getClass());
    public final static String DB_TEST_PROPERTIES = "/db-test.properties";
    public static final String ROOT = "root";
    public static final String CLIENT = "client";

    public static EnvironmentSpecificProperties getEnvironmentSpecificProperties(String name) {
        return new EnvironmentSpecificProperties(
                DatabasePlatformTest.class.getResource(DB_TEST_PROPERTIES), String.format(
                        "test.%s", name), name);
    }

    public static IDatabasePlatform createDatabasePlatform(String name) throws Exception {
        File f = new File(String.format("target/%sdbs", name));
        FileUtils.deleteDirectory(f);
        f.mkdir();
        EnvironmentSpecificProperties properties = getEnvironmentSpecificProperties(name);
        return JdbcDatabasePlatformFactory.getInstance().create(
                DataSourceFactory.create(properties, SecurityServiceFactory.create()),
                new SqlTemplateSettings(), true, false);
    }
}
