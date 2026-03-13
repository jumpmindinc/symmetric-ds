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

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import org.jumpmind.db.sql.SqlScript;
import org.jumpmind.db.util.BasicDataSourcePropertyConstants;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.symmetric.ClientSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.common.TableConstants;
import org.jumpmind.symmetric.io.data.DbExportUtils;
import org.jumpmind.symmetric.model.AbstractBatch.Status;
import org.jumpmind.symmetric.model.IncomingBatch;

public class ConfigImportHelper implements AutoCloseable {
    private ISymmetricEngine tempEngine;
    private final String tablePrefix;
    private final String engineName;

    public ConfigImportHelper(String tablePrefix) {
        this.tablePrefix = tablePrefix;
        String uuid = UUID.randomUUID().toString();
        engineName = "import-" + uuid;
        TypedProperties engineProperties = new TypedProperties();
        engineProperties.setProperty(BasicDataSourcePropertyConstants.DB_POOL_DRIVER, "org.h2.Driver");
        engineProperties.setProperty(BasicDataSourcePropertyConstants.DB_POOL_URL,
                "jdbc:h2:mem:" + engineName + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        engineProperties.setProperty(BasicDataSourcePropertyConstants.DB_POOL_VALIDATION_QUERY, "select 1");
        engineProperties.setProperty(BasicDataSourcePropertyConstants.DB_POOL_INIT_SQL, "SET MODE LEGACY");
        engineProperties.setProperty(BasicDataSourcePropertyConstants.DB_POOL_USER, "");
        engineProperties.setProperty(BasicDataSourcePropertyConstants.DB_POOL_PASSWORD, "");
        engineProperties.setProperty(ParameterConstants.NODE_GROUP_ID, engineName);
        engineProperties.setProperty(ParameterConstants.EXTERNAL_ID, engineName);
        engineProperties.setProperty(ParameterConstants.ENGINE_NAME, engineName);
        String syncUrl = "http://localhost/sync/" + engineName;
        engineProperties.setProperty(ParameterConstants.SYNC_URL, syncUrl);
        engineProperties.setProperty(ParameterConstants.REGISTRATION_URL, syncUrl);
        tempEngine = new ClientSymmetricEngine(engineProperties, false);
        tempEngine.setup();
    }

    public void loadContent(String content, boolean isCsv) throws IOException {
        if (isCsv) {
            List<IncomingBatch> batches = tempEngine.getDataLoaderService().loadDataBatch(content);
            for (IncomingBatch batch : batches) {
                if (batch.getStatus() == null || batch.getStatus() == Status.ER) {
                    throw new IOException("Failed to load CSV configuration: batch " + batch.getBatchId() + " is in error");
                }
            }
        } else {
            new SqlScript(content, tempEngine.getSqlTemplate(), true, null).execute();
        }
        tempEngine.getParameterService().rereadParameters();
        tempEngine.getNodeService().deleteNode(engineName, false);
        tempEngine.getConfigurationService().deleteNodeGroup(engineName);
    }

    public ISymmetricEngine getEngine() {
        return tempEngine;
    }

    public String exportConfigAsSql() throws IOException {
        StringWriter writer = new StringWriter();
        DbExportUtils.extractConfigurationStandalone(tempEngine.getDatabasePlatform(),
                TableConstants.getConfigTablesForExport(tablePrefix), writer);
        return writer.toString();
    }

    @Override
    public void close() {
        if (tempEngine != null) {
            tempEngine.stop();
        }
    }
}
