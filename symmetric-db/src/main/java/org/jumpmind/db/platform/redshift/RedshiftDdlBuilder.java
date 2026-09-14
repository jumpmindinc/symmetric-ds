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
package org.jumpmind.db.platform.redshift;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.AbstractDdlBuilder;
import org.jumpmind.db.platform.DatabaseNamesConstants;

public class RedshiftDdlBuilder extends AbstractDdlBuilder {
    public RedshiftDdlBuilder() {
        super(DatabaseNamesConstants.REDSHIFT);
        databaseInfo.setTriggersSupported(false);
        databaseInfo.setIndicesSupported(false);
        databaseInfo.setIndicesEmbedded(false);
        databaseInfo.setForeignKeysSupported(false);
        databaseInfo.setRequiresSavePointsInTransaction(true);
        databaseInfo.setRequiresAutoCommitForDdl(true);
        databaseInfo.setMaxIdentifierLength(127);
        databaseInfo.addNativeTypeMapping(Types.BIT, "BOOLEAN");
        databaseInfo.addNativeTypeMapping(Types.DOUBLE, "DOUBLE PRECISION", Types.DOUBLE);
        databaseInfo.addNativeTypeMapping(Types.FLOAT, "DOUBLE PRECISION");
        databaseInfo.addNativeTypeMapping(Types.LONGVARCHAR, "VARCHAR(65535)");
        databaseInfo.addNativeTypeMapping(Types.TINYINT, "SMALLINT", Types.SMALLINT);
        databaseInfo.addNativeTypeMapping(Types.TIME, "TIMESTAMP", Types.TIMESTAMP);
        databaseInfo.addNativeTypeMapping(Types.CLOB, "VARCHAR(65535)");
        databaseInfo.setMaxSize("TIMESTAMP", 6);
        databaseInfo.setMaxSize("TIMESTAMPTZ", 6);
        databaseInfo.setMaxSize("TIME", 6);
        databaseInfo.setMaxSize("TIMETZ", 6);
        databaseInfo.setDefaultSize(Types.CHAR, 256);
        databaseInfo.setDefaultSize(Types.VARCHAR, 256);
        databaseInfo.setNonBlankCharColumnSpacePadded(true);
        databaseInfo.setBlankCharColumnSpacePadded(true);
        databaseInfo.setCharColumnSpaceTrimmed(false);
        databaseInfo.setEmptyStringNulled(false);
        addEscapedCharSequence("\\", "\\\\");
        addEscapedCharSequence("\b", "\\b");
        addEscapedCharSequence("\f", "\\f");
        addEscapedCharSequence("\n", "\\n");
        addEscapedCharSequence("\r", "\\r");
        addEscapedCharSequence("\t", "\\t");
    }

    protected void writeColumnAutoIncrementStmt(Table table, Column column, StringBuilder ddl) {
    }

    @Override
    protected void writeCascadeAttributesForForeignKey(ForeignKey key, StringBuilder ddl) {
        // Redshift does not support cascade actions
        return;
    }
}
