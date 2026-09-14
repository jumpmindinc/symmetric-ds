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
package org.jumpmind.symmetric.io.data.writer;

abstract public class DataWriterStatisticConstants {
    public static final String BYTECOUNT = "BYTECOUNT";
    public static final String TRANSFORMMILLIS = "TRANSFORMMILLIS";
    public static final String FILTERMILLIS = "FILTERMILLIS";
    public static final String LOADMILLIS = "LOADMILLIS";
    public static final String ROWCOUNT = "STATEMENTCOUNT";
    public static final String INSERTCOUNT = "INSERTCOUNT";
    public static final String DELETECOUNT = "DELETECOUNT";
    public static final String UPDATECOUNT = "UPDATECOUNT";
    public static final String SQLCOUNT = "SQLCOUNT";
    public static final String CREATECOUNT = "CREATECOUNT";
    public static final String SCRIPTCOUNT = "SCRIPTCOUNT";
    public static final String SQLROWSAFFECTEDCOUNT = "SQLROWSAFFECTEDCOUNT";
    public static final String FALLBACKINSERTCOUNT = "FALLBACKINSERTCOUNT";
    public static final String FALLBACKUPDATECOUNT = "FALLBACKUPDATECOUNT";
    public static final String CONFLICTWINCOUNT = "CONFLICTWINCOUNT";
    public static final String CONFLICTLOSECOUNT = "CONFLICTLOSECOUNT";
    public static final String MISSINGDELETECOUNT = "MISSINGDELETECOUNT";
    public static final String IGNORECOUNT = "IGNORECOUNT";
    public static final String IGNOREROWCOUNT = "IGNOREROWCOUNT";
    public static final String LINENUMBER = "LINENUMBER";
    public static final String STARTTIME = "STARTTIME";
}
