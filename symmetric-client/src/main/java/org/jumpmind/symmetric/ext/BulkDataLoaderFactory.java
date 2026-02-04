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
package org.jumpmind.symmetric.ext;

import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.platform.kafka.KafkaPlatform;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.io.JdbcBatchBulkDatabaseWriter;
import org.jumpmind.symmetric.io.data.IDataWriter;
import org.jumpmind.symmetric.io.data.writer.Conflict;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterErrorHandler;
import org.jumpmind.symmetric.io.data.writer.IDatabaseWriterFilter;
import org.jumpmind.symmetric.io.data.writer.ResolvedData;
import org.jumpmind.symmetric.io.data.writer.TransformWriter;
import org.jumpmind.symmetric.load.AbstractDataLoaderFactory;
import org.jumpmind.symmetric.load.IDataLoaderFactory;
import org.jumpmind.symmetric.model.Channel;

public class BulkDataLoaderFactory extends AbstractDataLoaderFactory implements IDataLoaderFactory, ISymmetricEngineAware, IBuiltInExtensionPoint {
    protected ISymmetricEngine engine;

    @Override
    public String getTypeName() {
        return "bulk";
    }

    @Override
    public IDataWriter getDataWriter(String sourceNodeId, Channel channel, ISymmetricDialect symmetricDialect, TransformWriter transformWriter,
            List<IDatabaseWriterFilter> filters, List<IDatabaseWriterErrorHandler> errorHandlers,
            List<? extends Conflict> conflictSettings, List<ResolvedData> resolvedData) {
        IDatabasePlatform platform = engine.getTargetDialect().getPlatform();
        if (engine.getParameterService().is(ParameterConstants.JDBC_EXECUTE_BULK_BATCH_OVERRIDE, false)) {
            return new JdbcBatchBulkDatabaseWriter(symmetricDialect.getPlatform(), platform,
                    symmetricDialect.getTablePrefix(), buildParameterDatabaseWriterSettings(symmetricDialect, conflictSettings));
        } else {
            return new JdbcBatchBulkDatabaseWriter(symmetricDialect.getPlatform(), platform,
                    symmetricDialect.getTablePrefix(), buildParameterDatabaseWriterSettings(symmetricDialect, conflictSettings));
        }
    }

    @Override
    public boolean isPlatformSupported(IDatabasePlatform platform) {
        if (platform instanceof KafkaPlatform) {
            return false;
        }
        return true;
    }

    @Override
    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
        this.parameterService = engine.getParameterService();
    }
}
