/**
 * Licensed to Jumpmind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  Jumpmind Inc licenses this file
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
package org.jumpmind.symmetric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.h2.jdbcx.JdbcDataSource;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.properties.DefaultParameterParser.ParameterMetaData;
import org.jumpmind.properties.TypedProperties;
import org.junit.jupiter.api.Test;

class ClientSymmetricEnginePoolParametersTest {
    private final Map<String, ParameterMetaData> parameterMetaData = new HashMap<>();

    @Test
    void findChangedPoolParameters_onlyDefaults_returnsNothing() {
        withDefault("db.pool.max.active", "50");
        TypedProperties properties = new TypedProperties();
        properties.setProperty("db.pool.max.active", "50");
        assertEquals(List.of(), ClientSymmetricEngine.findChangedPoolParameters(properties, parameterMetaData));
    }

    @Test
    void findChangedPoolParameters_changedAndUnknownPoolKeys_returnsThemSorted() {
        withDefault("db.pool.max.active", "50");
        withDefault("db.pool.initial.size", "5");
        TypedProperties properties = new TypedProperties();
        properties.setProperty("db.pool.max.active", "200");
        properties.setProperty("db.pool.initial.size", "5");
        properties.setProperty("db.pool.custom.setting", "x");
        assertEquals(List.of("db.pool.custom.setting", "db.pool.max.active"),
                ClientSymmetricEngine.findChangedPoolParameters(properties, parameterMetaData));
    }

    @Test
    void findChangedPoolParameters_ignoresOtherDatabaseParameters() {
        TypedProperties properties = new TypedProperties();
        properties.setProperty("db.url", "jdbc:h2:mem:test");
        properties.setProperty("db.user", "sa");
        assertEquals(List.of(), ClientSymmetricEngine.findChangedPoolParameters(properties, parameterMetaData));
    }

    private void withDefault(String key, String defaultValue) {
        ParameterMetaData metaData = new ParameterMetaData();
        metaData.setKey(key);
        metaData.setDefaultValue(defaultValue);
        parameterMetaData.put(key, metaData);
    }

    @Test
    void createDatabasePlatform_externalDataSource_usesItInsteadOfBuildingAPool() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pool-params-external;DB_CLOSE_DELAY=-1");
        TypedProperties properties = new TypedProperties();
        properties.setProperty("db.pool.max.active", "200");
        IDatabasePlatform platform = ClientSymmetricEngine.createDatabasePlatform(null, properties, dataSource, false);
        assertSame(dataSource, platform.getDataSource());
    }
}
