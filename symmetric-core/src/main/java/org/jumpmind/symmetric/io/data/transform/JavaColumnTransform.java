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
package org.jumpmind.symmetric.io.data.transform;

import java.util.Map;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.service.IExtensionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaColumnTransform extends AbstractColumnTransform implements ISingleValueColumnTransform {
    public final static String CODE_START = "import org.jumpmind.symmetric.io.data.transform.*;\n"
            + "import org.jumpmind.symmetric.io.data.*;\n"
            + "import org.jumpmind.db.platform.*;\n"
            + "import org.jumpmind.db.sql.*;\n"
            + "import java.util.*;\n"
            + "public class JavaColumnTransformExt extends JavaColumnTransform { \n"
            + "    public String transform(IDatabasePlatform platform, DataContext context, TransformColumn column, TransformedData data,\n"
            + "        Map<String, String> sourceValues, String newValue, String oldValue) throws IgnoreColumnException, IgnoreRowException {\n\n";
    public final static String CODE_END = "\n\n   }\n}\n";
    public static final String NAME = "java";
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final String TRANSFORM_KEY = String.format("%d.JavaRouter", hashCode());
    protected IExtensionService extensionService;

    public JavaColumnTransform() {
    }

    public void setExtensionService(IExtensionService extensionService) {
        this.extensionService = extensionService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isExtractColumnTransform() {
        return true;
    }

    @Override
    public boolean isLoadColumnTransform() {
        return true;
    }

    @Override
    public String transform(IDatabasePlatform platform, DataContext context, TransformColumn column, TransformedData data,
            Map<String, String> sourceValues, String newValue, String oldValue) throws IgnoreColumnException, IgnoreRowException {
        try {
            ISingleValueColumnTransform colTransform = getCompiledClass(context, column);
            return colTransform.transform(platform, context, column, data, sourceValues, newValue, oldValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected ISingleValueColumnTransform getCompiledClass(DataContext context, TransformColumn column) throws Exception {
        String columnKey = TRANSFORM_KEY + "." + column.getTransformId() + "." + column.getTargetColumnName() + "." + column.getIncludeOn();
        ISingleValueColumnTransform colTransform = (ISingleValueColumnTransform) context.get(columnKey);
        if (colTransform == null) {
            String javaCode = CODE_START + column.getTransformExpression() + CODE_END;
            colTransform = (ISingleValueColumnTransform) extensionService.getCompiledClass(javaCode);
            context.put(columnKey, colTransform);
        }
        return colTransform;
    }
}
