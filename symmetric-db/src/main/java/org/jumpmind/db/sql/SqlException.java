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
package org.jumpmind.db.sql;

import java.sql.SQLException;

public class SqlException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SqlException() {
        super();
    }

    public SqlException(String message, Throwable cause) {
        super(message, cause);
    }

    public SqlException(String message) {
        super(message);
    }

    public SqlException(Throwable cause) {
        super(cause);
    }

    public int getErrorCode() {
        Throwable rootCause = getRootCause();
        if (rootCause instanceof SQLException) {
            return ((SQLException) rootCause).getErrorCode();
        } else {
            return -1;
        }
    }

    public String getSQLState() {
        Throwable rootCause = getRootCause();
        if (rootCause instanceof SQLException) {
            return ((SQLException) rootCause).getSQLState();
        } else {
            return null;
        }
    }

    public Throwable getRootCause() {
        Throwable rootCause = null;
        Throwable cause = getCause();
        while (cause != null && cause != rootCause) {
            rootCause = cause;
            cause = cause.getCause();
        }
        if (rootCause == null) {
            rootCause = this;
        }
        return rootCause;
    }

    public String getRootMessage() {
        return getRootCause().getMessage();
    }
}
