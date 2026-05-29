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

import java.util.ArrayList;
import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.extension.IExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.reader.ExtractDataReader;
import org.jumpmind.symmetric.io.data.reader.IExtractDataFilter;
import org.jumpmind.symmetric.io.data.reader.IRelationExtractDataFilter;
import org.jumpmind.symmetric.io.data.reader.IExtractDataReaderSource;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IParameterService;

public class ExtractDataReaderFactory implements IExtractDataReaderFactory {
    protected ISymmetricEngine engine;

    public ExtractDataReaderFactory(ISymmetricEngine engine) {
        this.engine = engine;
    }

    @SuppressWarnings("removal")
    @Override
    public ExtractDataReader getReader(IDatabasePlatform platform, IExtractDataReaderSource source, Node sourceNode, Node targetNode,
            IDatabasePlatform targetPlatform) {
        IParameterService parameterService = engine.getSymmetricDialect().getParameterService();
        boolean isUsingUnitypes = parameterService.is(ParameterConstants.DBDIALECT_SYBASE_ASE_CONVERT_UNITYPES_FOR_SYNC);
        List<IExtensionPoint> filters = new ArrayList<>();
        if (parameterService.is(ParameterConstants.EXTENSION_USE_LEGACY_INTERFACE)) {
            engine.getExtensionService().getExtensionPointList(IExtractDataFilter.class).forEach(filters::add);
        } else {
            engine.getExtensionService().getExtensionPointList(IRelationExtractDataFilter.class).forEach(filters::add);
        }
        return new ExtractDataReader(platform, source, filters, isUsingUnitypes, targetPlatform);
    }
}
