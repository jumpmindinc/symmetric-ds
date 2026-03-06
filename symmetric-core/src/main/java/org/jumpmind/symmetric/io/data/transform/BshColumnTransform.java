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

import static org.jumpmind.symmetric.common.Constants.DATA_CONTEXT_ENGINE;
import static org.jumpmind.symmetric.common.Constants.DATA_CONTEXT_SOURCE_NODE;
import static org.jumpmind.symmetric.common.Constants.DATA_CONTEXT_SOURCE_NODE_EXTERNAL_ID;
import static org.jumpmind.symmetric.common.Constants.DATA_CONTEXT_SOURCE_NODE_GROUP_ID;
import static org.jumpmind.symmetric.common.Constants.DATA_CONTEXT_SOURCE_NODE_ID;
import static org.jumpmind.symmetric.common.Constants.DATA_CONTEXT_TARGET_NODE;
import static org.jumpmind.symmetric.common.Constants.DATA_CONTEXT_TARGET_NODE_EXTERNAL_ID;
import static org.jumpmind.symmetric.common.Constants.DATA_CONTEXT_TARGET_NODE_GROUP_ID;
import static org.jumpmind.symmetric.common.Constants.DATA_CONTEXT_TARGET_NODE_ID;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.SymmetricException;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.common.ParameterConstants;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bsh.Interpreter;
import bsh.TargetError;

public class BshColumnTransform extends AbstractColumnTransform implements ISingleNewAndOldValueColumnTransform {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    final String INTERPRETER_KEY = String.format("%d.BshInterpreter", hashCode());
    public static final String NAME = "bsh";
    /*
     * Static context object used to maintain objects in memory for reference between BSH transforms.
     */
    private static Map<String, Object> bshContext = new HashMap<String, Object>();

    public BshColumnTransform() {
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isParameterServiceRequired() {
        return true;
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
    public NewAndOldValue transform(IDatabasePlatform platform,
            DataContext context,
            TransformColumn column, TransformedData data, Map<String, String> sourceValues,
            String newValue, String oldValue) throws IgnoreColumnException, IgnoreRowException {
        String transformBeanshellInfo = "";
        try {
            transformBeanshellInfo = String.format("transform script for column %s, transform_id=%s, BSH=", column.getTargetColumnName(), column
                    .getTransformId());
            Interpreter interpreter = getInterpreter(context);
            interpreter.set("currentValue", newValue);
            interpreter.set("oldValue", oldValue);
            interpreter.set("channelId", context.getBatch().getChannelId());
            interpreter.set("includeOn", column.getIncludeOn());
            interpreter.set("sourceDmlType", data.getSourceDmlType());
            interpreter.set("sourceDmlTypeString", data.getSourceDmlType().toString());
            interpreter.set("transformedData", data);
            interpreter.set("transformColumn", column);
            Data csvData = (Data) context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA);
            if (csvData != null && csvData.getTriggerHistory() != null) {
                interpreter.set("sourceSchemaName", csvData.getTriggerHistory().getSourceSchemaName());
                interpreter.set("sourceCatalogName", csvData.getTriggerHistory().getSourceCatalogName());
                interpreter.set("sourceTableName", csvData.getTriggerHistory().getSourceTableName());
            }
            for (String columnName : sourceValues.keySet()) {
                interpreter.set(columnName.toUpperCase(), sourceValues.get(columnName));
            }
            String transformExpression = column.getTransformExpression();
            if (StringUtils.isEmpty(transformExpression)) {
                throw new SymmetricException("transformExpression cannot be empty. Check "
                        + "configuration for transform '" + column.getTransformId() + "'");
            }
            String globalScript = parameterService.getString(ParameterConstants.BSH_TRANSFORM_GLOBAL_SCRIPT);
            String methodName = String.format("transform_%d()",
                    Math.abs(transformExpression.hashCode() + (globalScript == null ? 0 : globalScript.hashCode())));
            if (context.get(methodName) == null) {
                interpreter.set("log", log);
                interpreter.set("sqlTemplate", platform.getSqlTemplate());
                interpreter.set("context", context);
                interpreter.set("bshContext", bshContext);
                interpreter.set(DATA_CONTEXT_ENGINE, context.get(DATA_CONTEXT_ENGINE));
                interpreter.set(DATA_CONTEXT_TARGET_NODE, context.get(DATA_CONTEXT_TARGET_NODE));
                interpreter.set(DATA_CONTEXT_TARGET_NODE_ID, context.get(DATA_CONTEXT_TARGET_NODE_ID));
                interpreter.set(DATA_CONTEXT_TARGET_NODE_GROUP_ID, context.get(DATA_CONTEXT_TARGET_NODE_GROUP_ID));
                interpreter.set(DATA_CONTEXT_TARGET_NODE_EXTERNAL_ID, context.get(DATA_CONTEXT_TARGET_NODE_EXTERNAL_ID));
                interpreter.set(DATA_CONTEXT_SOURCE_NODE, context.get(DATA_CONTEXT_SOURCE_NODE));
                interpreter.set(DATA_CONTEXT_SOURCE_NODE_ID, context.get(DATA_CONTEXT_SOURCE_NODE_ID));
                interpreter.set(DATA_CONTEXT_SOURCE_NODE_GROUP_ID, context.get(DATA_CONTEXT_SOURCE_NODE_GROUP_ID));
                interpreter.set(DATA_CONTEXT_SOURCE_NODE_EXTERNAL_ID, context.get(DATA_CONTEXT_SOURCE_NODE_EXTERNAL_ID));
                if (StringUtils.isNotBlank(globalScript)) {
                    transformBeanshellInfo += globalScript;
                    interpreter.eval(globalScript);
                }
                String transformBeanshellBody = String.format("%s {\n%s\n}", methodName, transformExpression);
                interpreter.eval(transformBeanshellBody);
                transformBeanshellInfo += "\n" + transformBeanshellBody;
                log.debug("Combined {}", transformBeanshellInfo);
                context.put(methodName, Boolean.TRUE);
            }
            Object result = interpreter.eval(methodName);
            if (csvData != null && csvData.getTriggerHistory() != null) {
                interpreter.unset("sourceSchemaName");
                interpreter.unset("sourceCatalogName");
                interpreter.unset("sourceTableName");
            }
            for (String columnName : sourceValues.keySet()) {
                interpreter.unset(columnName.toUpperCase());
            }
            if (result instanceof String) {
                if (data.getTargetDmlType().equals(DataEventType.DELETE) && data.getOldSourceValues() != null) {
                    return new NewAndOldValue(null, (String) result);
                } else {
                    return new NewAndOldValue((String) result, null);
                }
            } else if (result instanceof NewAndOldValue) {
                return (NewAndOldValue) result;
            } else if (result != null) {
                return new NewAndOldValue(result.toString(), null);
            } else {
                return null;
            }
        } catch (TargetError evalEx) {
            Throwable ex = evalEx.getTarget();
            if (ex instanceof IgnoreColumnException) {
                throw (IgnoreColumnException) ex;
            } else if (ex instanceof IgnoreRowException) {
                throw (IgnoreRowException) ex;
            } else {
                log.error("TargetError detected for " + transformBeanshellInfo, ex);
                throw new TransformColumnException(String.format("Beanshell script error on line %d for target column %s on transform %s", evalEx
                        .getErrorLineNumber(), column.getTargetColumnName(),
                        column.getTransformId()), ex);
            }
        } catch (Exception ex) {
            log.error("Exception detected for " + transformBeanshellInfo, ex);
            if (ex instanceof IgnoreColumnException) {
                throw (IgnoreColumnException) ex;
            } else if (ex instanceof IgnoreRowException) {
                throw (IgnoreRowException) ex;
            } else {
                log.error(String.format("Beanshell script error for target column %s on transform %s", column.getTargetColumnName(),
                        column.getTransformId()), ex);
                log.error("Beanshell script failed.\n%s", transformBeanshellInfo);
                throw new TransformColumnException(ex);
            }
        }
    }

    protected Interpreter getInterpreter(Context context) {
        Interpreter interpreter = (Interpreter) context.get(INTERPRETER_KEY);
        if (interpreter == null) {
            interpreter = new Interpreter();
            context.put(INTERPRETER_KEY, interpreter);
        }
        return interpreter;
    }
}
