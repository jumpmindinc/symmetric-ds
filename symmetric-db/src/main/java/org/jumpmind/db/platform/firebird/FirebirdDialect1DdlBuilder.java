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
package org.jumpmind.db.platform.firebird;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.Collection;
import java.util.Iterator;

import org.jumpmind.db.alter.ColumnDataTypeChange;
import org.jumpmind.db.alter.ColumnDefaultValueChange;
import org.jumpmind.db.alter.IModelChange;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.platform.DatabaseNamesConstants;

/*
 * The SQL builder for Firebird running in SQL dialect 1 mode
 */
public class FirebirdDialect1DdlBuilder extends FirebirdDdlBuilder {
    public FirebirdDialect1DdlBuilder() {
        databaseInfo.addNativeTypeMapping(Types.BIGINT, "NUMERIC(18)", Types.NUMERIC);
        databaseInfo.addNativeTypeMapping(Types.TIME, "TIMESTAMP", Types.TIMESTAMP);
        databaseName = DatabaseNamesConstants.FIREBIRD_DIALECT1;
        databaseInfo.setDelimitedIdentifiersSupported(false);
        databaseInfo.setDelimiterToken("");
    }

    protected void processTableStructureChanges(Database currentModel, Database desiredModel,
            Collection<IModelChange> changes, StringBuilder ddl) {
        Iterator<IModelChange> iter = changes.iterator();
        while (iter.hasNext()) {
            IModelChange change = iter.next();
            if (change instanceof ColumnDataTypeChange) {
                ColumnDataTypeChange dataTypeChange = (ColumnDataTypeChange) change;
                if (dataTypeChange.getNewTypeCode() == Types.BIGINT &&
                        dataTypeChange.getChangedColumn().getMappedTypeCode() == Types.DOUBLE) {
                    iter.remove();
                }
            } else if (change instanceof ColumnDefaultValueChange) {
                ColumnDefaultValueChange defaultValueChange = (ColumnDefaultValueChange) change;
                if (defaultValueChange.getChangedColumn().getMappedTypeCode() == Types.DOUBLE &&
                        new BigDecimal(defaultValueChange.getNewDefaultValue()).compareTo(
                                new BigDecimal(defaultValueChange.getChangedColumn().getDefaultValue())) == 0) {
                    iter.remove();
                }
            }
        }
        super.processTableStructureChanges(currentModel, desiredModel, changes, ddl);
    }
}
