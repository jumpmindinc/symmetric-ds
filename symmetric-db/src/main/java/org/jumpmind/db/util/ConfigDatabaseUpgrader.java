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
package org.jumpmind.db.util;

import org.jumpmind.db.model.Database;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.IDdlBuilder;
import org.jumpmind.db.sql.LogSqlResultsListener;
import org.jumpmind.db.sql.SqlScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigDatabaseUpgrader {
    private static final Logger log = LoggerFactory.getLogger(ConfigDatabaseUpgrader.class);
    protected String tablePrefix = "SYM";
    protected IDatabasePlatform configDatabasePlatform;
    protected boolean logOutput = true;
    protected String schemaXml;

    public ConfigDatabaseUpgrader(String schemaXml, IDatabasePlatform configDatabasePlatform,
            boolean logOutput, String tablePrefix) {
        this.configDatabasePlatform = configDatabasePlatform;
        this.logOutput = logOutput;
        this.schemaXml = schemaXml;
        this.tablePrefix = tablePrefix;
    }

    public ConfigDatabaseUpgrader() {
    }

    public boolean upgrade() {
        try {
            if (logOutput) {
                log.info("Checking if config tables need created or altered");
            }
            Database modelFromXml = configDatabasePlatform.readDatabaseFromXml(schemaXml, true);
            configDatabasePlatform.prefixDatabase(tablePrefix, modelFromXml);
            Database modelFromDatabase = configDatabasePlatform.readFromDatabase(modelFromXml
                    .getTables());
            IDdlBuilder builder = configDatabasePlatform.getDdlBuilder();
            if (builder.isAlterDatabase(modelFromDatabase, modelFromXml)) {
                if (logOutput) {
                    log.info("There are config tables that needed altered");
                }
                String delimiter = configDatabasePlatform.getDatabaseInfo()
                        .getSqlCommandDelimiter();
                String alterSql = builder.alterDatabase(modelFromDatabase, modelFromXml);
                log.debug("Alter SQL generated: {}", alterSql);
                SqlScript script = new SqlScript(alterSql, configDatabasePlatform.getSqlTemplate(),
                        true, false, false, delimiter, null);
                if (logOutput) {
                    script.setListener(new LogSqlResultsListener());
                }
                script.execute(configDatabasePlatform.getDatabaseInfo()
                        .isRequiresAutoCommitForDdl());
                if (logOutput) {
                    log.info("Done with auto update of config tables");
                }
                return true;
            } else {
                return false;
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void setLogOutput(boolean logOutput) {
        this.logOutput = logOutput;
    }

    public boolean isLogOutput() {
        return logOutput;
    }

    public void setConfigDatabasePlatform(IDatabasePlatform configDatabasePlatform) {
        this.configDatabasePlatform = configDatabasePlatform;
    }

    public IDatabasePlatform getConfigDatabasePlatform() {
        return configDatabasePlatform;
    }

    public void setSchemaXml(String schemaXml) {
        this.schemaXml = schemaXml;
    }

    public String getSchemaXml() {
        return schemaXml;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }
}
