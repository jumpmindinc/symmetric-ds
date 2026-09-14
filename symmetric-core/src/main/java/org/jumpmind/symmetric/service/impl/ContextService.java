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
package org.jumpmind.symmetric.service.impl;

import java.util.Date;

import org.jumpmind.db.sql.ISqlTransaction;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.service.IContextService;
import org.jumpmind.symmetric.service.IParameterService;

public class ContextService extends AbstractService implements IContextService {
    public ContextService(IParameterService parameterService, ISymmetricDialect dialect) {
        super(parameterService, dialect);
        setSqlMap(new ContextServiceSqlMap(symmetricDialect.getPlatform(), createSqlReplacementTokens()));
    }

    public boolean is(String name) {
        return Boolean.parseBoolean(getString(name));
    }

    public int getInt(String name) {
        return Integer.parseInt(getString(name));
    }

    public int getInt(String name, int defaultVal) {
        String val = getString(name);
        if (val != null) {
            return Integer.parseInt(val);
        }
        return defaultVal;
    }

    public long getLong(String name) {
        return getLong(name, 0);
    }

    public long getLong(String name, long defaultVal) {
        String val = getString(name);
        if (val != null) {
            return Long.parseLong(val);
        }
        return defaultVal;
    }

    public String getString(String name) {
        return sqlTemplate.queryForString(getSql("selectSql"), name);
    }

    public int insert(ISqlTransaction transaction, String name, String value) {
        return transaction.prepareAndExecute(getSql("insertSql"), name, value, new Date());
    }

    public int update(ISqlTransaction transaction, String name, String value) {
        return transaction.prepareAndExecute(getSql("updateSql"), value, new Date(), name);
    }

    public int delete(ISqlTransaction transaction, String name) {
        return transaction.prepareAndExecute(getSql("deleteSql"), name);
    }

    public int delete(String name) {
        return sqlTemplate.update(getSql("deleteSql"), name);
    }

    public void save(String name, String value) {
        save(null, name, value);
    }

    @Override
    public void save(ISqlTransaction transaction, String name, String value) {
        if (transaction != null) {
            if (update(transaction, name, value) <= 0) {
                insert(transaction, name, value);
            }
        } else {
            int count = sqlTemplate.update(getSql("updateSql"), value, new Date(), name);
            if (count <= 0) {
                sqlTemplate.update(getSql("insertSql"), name, value, new Date());
            }
        }
    }
}
