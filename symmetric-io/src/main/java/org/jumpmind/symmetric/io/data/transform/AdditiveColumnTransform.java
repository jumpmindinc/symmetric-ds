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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.SqlUtils;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdditiveColumnTransform implements ISingleValueColumnTransform, IBuiltInExtensionPoint {
    private static final Logger log = LoggerFactory.getLogger(AdditiveColumnTransform.class);
    public static final String NAME = "additive";

    public String getName() {
        return NAME;
    }

    public boolean isExtractColumnTransform() {
        return false;
    }

    public boolean isLoadColumnTransform() {
        return true;
    }

    public String getFullyQualifiedTableName(IDatabasePlatform platform, String schema, String catalog, String tableName) {
        String quote = platform.getDdlBuilder().isDelimitedIdentifierModeOn() ? platform
                .getDatabaseInfo().getDelimiterToken() : "";
        tableName = quote + tableName + quote;
        if (!StringUtils.isBlank(schema)) {
            tableName = schema + "." + tableName;
        }
        if (!StringUtils.isBlank(catalog)) {
            tableName = catalog + "." + tableName;
        }
        return tableName;
    }

    public String transform(IDatabasePlatform platform, DataContext context,
            TransformColumn column, TransformedData data, Map<String, String> sourceValues, String newValue, String oldValue) throws IgnoreColumnException,
            IgnoreRowException {
        BigDecimal multiplier = new BigDecimal(1.00);
        if (StringUtils.isNotBlank(column.getTransformExpression())) {
            multiplier = new BigDecimal(column.getTransformExpression());
        }
        Table table = platform.getTableFromCache(data.getCatalogName(), data.getSchemaName(),
                data.getTableName(), false);
        if (table == null) {
            if (log.isDebugEnabled()) {
                log.debug("Could not find the target table '{}'", data.getFullyQualifiedTableName());
            }
            throw new IgnoreColumnException();
        } else if (table.getColumnWithName(column.getTargetColumnName()) == null) {
            if (log.isDebugEnabled()) {
                log.debug("Could not find the target column '{}'", column.getTargetColumnName());
            }
            throw new IgnoreColumnException();
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Old, new, transform expression as received: " + oldValue + ", " + newValue + ", " + column.getTransformExpression());
            }
            if (!StringUtils.isNotBlank(newValue) ||
                    data.getSourceDmlType() == DataEventType.DELETE) {
                newValue = "0";
            }
            if (!StringUtils.isNotBlank(oldValue)) {
                oldValue = "0";
            }
            BigDecimal delta = new BigDecimal(newValue);
            delta = delta.subtract(new BigDecimal(oldValue));
            delta = delta.multiply(multiplier);
            newValue = delta.toString();
            String quote = platform.getDdlBuilder().isDelimitedIdentifierModeOn() ? platform
                    .getDatabaseInfo().getDelimiterToken() : "";
            StringBuilder sql = new StringBuilder(String.format("update %s set %s=%s+(%s) where ",
                    getFullyQualifiedTableName(platform, SqlUtils.sanitizeIdentifier(data.getSchemaName()),
                            SqlUtils.sanitizeIdentifier(data.getCatalogName()), SqlUtils.sanitizeIdentifier(data.getTableName())),
                    quote + SqlUtils.sanitizeIdentifier(column.getTargetColumnName()) + quote,
                    quote + SqlUtils.sanitizeIdentifier(column.getTargetColumnName()) + quote,
                    newValue));
            String[] keyNames = data.getKeyNames();
            List<Column> columns = new ArrayList<Column>();
            List<String> keyValuesList = new ArrayList<String>();
            boolean addedFirstKey = false;
            for (int i = 0; i < keyNames.length; i++) {
                Column targetCol = table.getColumnWithName(keyNames[i]);
                if (targetCol != null) {
                    columns.add(targetCol);
                    String value = sourceValues.get(keyNames[i]);
                    if (value != null) {
                        keyValuesList.add(value);
                    }
                    if (addedFirstKey) {
                        sql.append("and ");
                    } else {
                        addedFirstKey = true;
                    }
                    sql.append(quote);
                    sql.append(SqlUtils.sanitizeIdentifier(keyNames[i]));
                    sql.append(quote);
                    if (value == null) {
                        sql.append("is null ");
                    } else {
                        sql.append("=? ");
                    }
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("SQL: " + sql);
            }
            ISqlTransaction transaction = context.findTransaction();
            if (0 < transaction.prepareAndExecute(
                    sql.toString(),
                    platform.getObjectValues(context.getBatch().getBinaryEncoding(),
                            keyValuesList.toArray(new String[keyValuesList.size()]),
                            columns.toArray(new Column[columns.size()])))) {
                throw new IgnoreColumnException();
            }
        }
        return newValue;
    }
}
