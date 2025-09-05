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
package org.jumpmind.symmetric.db.mssql;

import java.sql.Types;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.platform.mssql.MsSql2005DdlBuilder;
import org.jumpmind.symmetric.db.ISymmetricDialect;

public class MsSql2005TriggerTemplate extends MsSqlTriggerTemplate {
    public MsSql2005TriggerTemplate(ISymmetricDialect symmetricDialect) {
        super(symmetricDialect);
    }

    /***
     * Treats NVARCHAR(MAX), VARCHAR(MAX) columns as regular string type, not as "large objects".
     */
    @Override
    protected boolean isLob(Column column) {
        int mappedTypeCode = column.getMappedTypeCode();
        int size = column.getSizeAsInt();
        String jdbcTypeName = column.getJdbcTypeName();
        if (mappedTypeCode == Types.LONGVARCHAR
                && (jdbcTypeName.equalsIgnoreCase("NVARCHAR") && size > MsSql2005DdlBuilder.NVARCHARMAX_LIMIT
                        || jdbcTypeName.equalsIgnoreCase("VARCHAR") && size > MsSql2005DdlBuilder.VARCHARMAX_LIMIT)) {
            return false;
        }
        return super.isLob(column);
    }
}