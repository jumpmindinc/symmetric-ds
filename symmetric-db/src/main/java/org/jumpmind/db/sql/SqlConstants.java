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
package org.jumpmind.db.sql;

import org.jumpmind.db.sql.mapper.StringMapper;

abstract public class SqlConstants {
    public static final String ALWAYS_TRUE_CONDITION = "1=1";
    public static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_STREAMING_FETCH_SIZE = 1000;
    public static final StringMapper STRING_MAPPER = new StringMapper();
    public static final String POSTGRES_CONVERT_INFINITY_DATE_TO_NULL = "postgres.convert.infinity.date.to.null";
    /** See the parameter of the same name: continue past a DDL statement that failed only because the object already exists. */
    public static final String TOLERATE_OBJECT_ALREADY_EXISTS_ON_DDL = "db.tolerate.object.already.exists.on.ddl";
}