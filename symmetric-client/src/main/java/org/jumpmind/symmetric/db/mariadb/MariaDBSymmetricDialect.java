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
package org.jumpmind.symmetric.db.mariadb;

import java.util.List;

import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.db.sql.mapper.StringMapper;
import org.jumpmind.symmetric.Version;
import org.jumpmind.symmetric.db.mysql.MySqlSymmetricDialect;
import org.jumpmind.symmetric.service.IParameterService;

public class MariaDBSymmetricDialect extends MySqlSymmetricDialect {
    public MariaDBSymmetricDialect(IParameterService parameterService,
            IDatabasePlatform platform) {
        super(parameterService, platform);
        platform.getDatabaseInfo().setGeneratedColumnsSupported(!Version.isOlderThanVersion(getProductVersion(), "5.2"));
        platform.getDatabaseInfo().setPersistedGeneratedColumnsSupported(!Version.isOlderThanVersion(getProductVersion(), "5.2"));
        platform.getDatabaseInfo().setNonPersistedGeneratedColumnsIndexSupported(!Version.isOlderThanVersion(getProductVersion(), "10.2.3"));
        platform.getDatabaseInfo().setExpressionsAsDefaultValuesSupported(false);
    }

    @Override
    public String getTransactionId(ISqlTransaction transaction) {
        String xid = null;
        if (supportsTransactionId()) {
            List<String> list = transaction.query("select @@last_gtid", new StringMapper(), null, null);
            return list != null && list.size() > 0 ? list.get(0) : null;
        }
        return xid;
    }
}
