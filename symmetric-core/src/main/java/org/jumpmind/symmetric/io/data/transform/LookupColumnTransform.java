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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.model.Data;
import org.jumpmind.util.FormatUtils;
import org.jumpmind.util.LinkedCaseInsensitiveMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LookupColumnTransform extends AbstractColumnTransform implements ISingleNewAndOldValueColumnTransform {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    public static final String NAME = "lookup";
    protected static final StringMapper lookupColumnRowMapper = new StringMapper();

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
    public NewAndOldValue transform(IDatabasePlatform platform,
            DataContext context,
            TransformColumn column, TransformedData data, Map<String, String> sourceValues,
            String newValue, String oldValue) throws IgnoreColumnException, IgnoreRowException {
        String sql = doTokenReplacementOnSql(context, column.getTransformExpression());
        String lookupValue = null;
        if (StringUtils.isNotBlank(sql)) {
            ISqlTransaction transaction = context.findTransaction();
            List<String> values = null;
            LinkedCaseInsensitiveMap<Object> namedParams = new LinkedCaseInsensitiveMap<Object>(sourceValues);
            if (data.getOldSourceValues() != null && sql.contains(":OLD_")) {
                for (Map.Entry<String, String> oldColumn : data.getOldSourceValues().entrySet()) {
                    namedParams.put("OLD_" + oldColumn.getKey().toUpperCase(), oldColumn.getValue());
                }
            }
            if (data.getTargetValues() != null && sql.contains(":TRM_")) {
                for (Map.Entry<String, String> transformedCol : data.getTargetValues().entrySet()) {
                    namedParams.put("TRM_" + transformedCol.getKey().toUpperCase(), transformedCol.getValue());
                }
            }
            if (transaction != null) {
                values = transaction.query(sql, lookupColumnRowMapper, namedParams);
            } else {
                values = platform.getSqlTemplate().query(sql, lookupColumnRowMapper, namedParams);
            }
            int rowCount = values.size();
            if (rowCount == 1) {
                lookupValue = values.get(0);
            } else if (rowCount > 1) {
                lookupValue = values.get(0);
                log.warn(
                        "Expected a single row, but returned multiple rows from lookup for target column {} on transform {} ",
                        column.getTargetColumnName(), column.getTransformId());
            } else if (values.size() == 0) {
                log.info(
                        "Expected a single row, but returned no rows from lookup for target column {} on transform {}",
                        column.getTargetColumnName(), column.getTransformId());
            }
        } else {
            log.warn(
                    "Expected SQL expression for lookup transform, but no expression was found for target column {} on transform {}",
                    column.getTargetColumnName(), column.getTransformId());
        }
        return new NewAndOldValue(column, data, lookupValue);
    }

    protected String doTokenReplacementOnSql(DataContext context, String sql) {
        if (isNotBlank(sql)) {
            Data csvData = (Data) context.get(Constants.DATA_CONTEXT_CURRENT_CSV_DATA);
            if (csvData != null && csvData.getTriggerHistory() != null) {
                sql = FormatUtils.replaceToken(sql, "sourceCatalogName", csvData
                        .getTriggerHistory().getSourceCatalogName(), true);
            }
            if (csvData != null && csvData.getTriggerHistory() != null) {
                sql = FormatUtils.replaceToken(sql, "sourceSchemaName", csvData.getTriggerHistory()
                        .getSourceSchemaName(), true);
            }
        }
        return sql;
    }
}
