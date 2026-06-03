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
package org.jumpmind.symmetric.extract;

import java.util.List;
import java.util.Map;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Relation;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.data.Batch;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.reader.IExtractDataReaderSource;
import org.jumpmind.symmetric.service.IConfigurationService;
import org.jumpmind.symmetric.service.IDataService;
import org.jumpmind.symmetric.service.IExtensionService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.service.ITriggerRouterService;

public abstract class SelectFromSource implements IExtractDataReaderSource {
    protected ISymmetricEngine engine;
    protected IDatabasePlatform platform;
    protected ISymmetricDialect symmetricDialect;
    protected IParameterService parameterService;
    protected IDataService dataService;
    protected ITriggerRouterService triggerRouterService;
    protected IConfigurationService configurationService;
    protected INodeService nodeService;
    protected IExtensionService extensionService;
    protected Batch batch;
    protected Relation sourceRelation;
    protected Relation targetRelation;

    public SelectFromSource(ISymmetricEngine engine) {
        this.engine = engine;
        platform = engine.getDatabasePlatform();
        symmetricDialect = engine.getSymmetricDialect();
        parameterService = engine.getParameterService();
        dataService = engine.getDataService();
        triggerRouterService = engine.getTriggerRouterService();
        configurationService = engine.getConfigurationService();
        nodeService = engine.getNodeService();
        extensionService = engine.getExtensionService();
    }

    @Override
    public Batch getBatch() {
        return batch;
    }

    @Override
    public Relation getSourceRelation() {
        return sourceRelation;
    }

    @Override
    public Relation getTargetRelation() {
        return targetRelation;
    }

    protected boolean hasLobsThatNeedExtract(Relation relation, CsvData data) {
        if (relation.containsLobColumns(platform)) {
            String[] colNames = relation.getColumnNames();
            Map<String, String> colMap = data.toColumnNameValuePairs(colNames, CsvData.ROW_DATA);
            List<Column> lobColumns = relation.getLobColumns(platform);
            for (Column c : lobColumns) {
                String value = colMap.get(c.getName());
                if (value != null && (value.equals("\b") || value.equals("08"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
