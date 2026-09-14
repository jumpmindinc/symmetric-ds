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
package org.jumpmind.driver;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.properties.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatementBypassInterceptor extends StatementInterceptor {
    private final static Logger log = LoggerFactory.getLogger(StatementBypassInterceptor.class);

    public StatementBypassInterceptor(Object wrapped, TypedProperties systemPlusEngineProperties) {
        super(wrapped, systemPlusEngineProperties);
    }

    @Override
    protected InterceptResult preparedStatementPreExecute(PreparedStatementWrapper ps, String methodName, Object[] parameters) {
        if (methodName.equals("getUpdateCount")) {
            InterceptResult result = new InterceptResult();
            result.setIntercepted(true);
            result.setInterceptResult(Integer.valueOf(1));
            return result;
        }
        if (methodName.startsWith("execute")) {
            String statementLower = ps.getStatement().toLowerCase();
            if ((statementLower.startsWith("insert") || statementLower.startsWith("update")) && !statementLower.contains("sym_")) {
                InterceptResult result = new InterceptResult();
                result.setIntercepted(true);
                if (methodName.equals("execute")) {
                    result.setInterceptResult(Boolean.FALSE);
                } else if (methodName.equals("executeUpdate")) {
                    result.setInterceptResult(Integer.valueOf(1));
                }
                String sql = sqlBuilder.buildDynamicSqlForLog(ps.getStatement(), psArgs.toArray(), null);
                log.info("PreparedStatement." + methodName + " *BYPASSED* " + StringUtils.abbreviate(sql, 128));
                return result;
            }
        }
        return super.preparedStatementPreExecute(ps, methodName, parameters);
    }

    @Override
    public void preparedStatementExecute(String methodName, long elapsed, String sql) {
        // no op.
    }
}
