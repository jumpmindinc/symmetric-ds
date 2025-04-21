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
package org.jumpmind.symmetric.db.firebird;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.symmetric.service.IParameterService;

/*
 * Database dialect for Firebird version 2.1.
 */
public class Firebird21SymmetricDialect extends Firebird20SymmetricDialect {
    static final String SQL_DROP_FUNCTION = "DROP FUNCTION $(functionName)";

    public Firebird21SymmetricDialect(IParameterService parameterService, IDatabasePlatform platform) {
        super(parameterService, platform);
        this.triggerTemplate = new Firebird21TriggerTemplate(this);
    }

    @Override
    public void createRequiredDatabaseObjectsImpl(StringBuilder ddl) {
        String hex = this.parameterService.getTablePrefix() + "_" + "hex";
        if (!installed(SQL_FUNCTION_INSTALLED, hex)) {
            if (databaseMajorVersion >= 4) {
                String sql = "CREATE FUNCTION $(functionName)(b blob) RETURNS blob AS\n"
                        + "BEGIN\n"
                        + "    RETURN (SELECT HEX_ENCODE(:b) FROM rdb$database);\n"
                        + "END";
                install(sql, hex, ddl);
            } else {
                String sql = "declare external function $(functionName) blob                                                                                                                                                         "
                        +
                        "  returns cstring(32660) free_it entry_point 'sym_hex' module_name 'sym_udf'                                                                                             ";
                install(sql, hex, ddl);
            }
        }
    }

    @Override
    public void dropRequiredDatabaseObjects() {
        String hex = this.parameterService.getTablePrefix() + "_" + "hex";
        if (installed(SQL_FUNCTION_INSTALLED, hex)) {
        	if (databaseMajorVersion >= 5) {
        		uninstall(SQL_DROP_FUNCTION, hex);
        	} else {
        		uninstall(SQL_DROP_EXTERNAL_FUNCTION, hex);
        	}
        }
    }
}
