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
package org.jumpmind.db.model;

final public class ColumnTypes {
    private ColumnTypes() {
    }

    public static final int SQLXML = 2009;
    public static final int NCHAR = -15;
    public static final int NCLOB = 2011;
    public static final int NVARCHAR = -9;
    public static final int LONGNVARCHAR = -16;
    public static final int ORACLE_TIMESTAMPTZ = -101;
    public static final int ORACLE_TIMESTAMPLTZ = -102;
    public static final int TIBERO_TIMESTAMPTZ = -101;
    public static final int TIBERO_TIMESTAMPLTZ = -102;
    public static final int MSSQL_SQL_VARIANT = -150;
    public static final int MSSQL_NTEXT = -10;
    public static final int TIMESTAMPTZ = -101;
    public static final int TIMESTAMPLTZ = -102;
    public static final int TIMETZ = -103;
}
