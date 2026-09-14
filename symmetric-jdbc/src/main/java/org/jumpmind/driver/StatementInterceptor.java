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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jumpmind.db.sql.LogSqlBuilder;
import org.jumpmind.properties.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatementInterceptor extends WrapperInterceptor {
    private final static Logger log = LoggerFactory.getLogger(StatementInterceptor.class);
    protected List<Object> psArgs = new ArrayList<Object>();
    protected LogSqlBuilder sqlBuilder = new LogSqlBuilder();

    public StatementInterceptor(Object wrapped, TypedProperties systemPlusEngineProperties) {
        super(wrapped);
    }

    @Override
    public InterceptResult preExecute(String methodName, Object... parameters) {
        if (getWrapped() instanceof PreparedStatementWrapper) {
            return preparedStatementPreExecute((PreparedStatementWrapper) getWrapped(), methodName, parameters);
        }
        return new InterceptResult();
    }

    protected InterceptResult preparedStatementPreExecute(PreparedStatementWrapper ps, String methodName, Object[] parameters) {
        if (methodName.startsWith("set") && (parameters != null && parameters.length > 1)) {
            psArgs.add(parameters[1]);
        }
        return new InterceptResult();
    }

    @Override
    public InterceptResult postExecute(String methodName, Object result, long startTime, long endTime, Object... parameters) {
        if (getWrapped() instanceof PreparedStatementWrapper) {
            return preparedStatementPostExecute((PreparedStatementWrapper) getWrapped(), methodName, result, startTime, endTime, parameters);
        } else if (getWrapped() instanceof StatementWrapper) {
            return statementPostExecute((StatementWrapper) getWrapped(), methodName, result, startTime, endTime, parameters);
        } else {
            return new InterceptResult();
        }
    }

    public InterceptResult preparedStatementPostExecute(PreparedStatementWrapper ps, String methodName, Object result, long startTime, long endTime,
            Object... parameters) {
        if (methodName.startsWith("execute")) {
            long elapsed = endTime - startTime;
            String sql = sqlBuilder.buildDynamicSqlForLog(ps.getStatement(), psArgs.toArray(), null);
            preparedStatementExecute(methodName, elapsed, sql);
        }
        return new InterceptResult();
    }

    public InterceptResult statementPostExecute(StatementWrapper ps, String methodName, Object result, long startTime, long endTime, Object... parameters) {
        if (methodName.startsWith("execute")) {
            long elapsed = endTime - startTime;
            statementExecute(methodName, elapsed, parameters);
        }
        return new InterceptResult();
    }

    public void preparedStatementExecute(String methodName, long elapsed, String sql) {
        log.info("PreparedStatement." + methodName + " (" + elapsed + "ms.) " + sql);
    }

    public void statementExecute(String methodName, long elapsed, Object... parameters) {
        log.info("Statement." + methodName + " (" + elapsed + "ms.) " + Arrays.toString(parameters));
    }
}
