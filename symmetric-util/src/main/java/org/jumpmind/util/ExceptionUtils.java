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
package org.jumpmind.util;

import java.sql.SQLException;
import java.util.List;

public class ExceptionUtils {
    public static SQLException unwrapSqlException(Throwable e) {
        List<Throwable> exs = org.apache.commons.lang3.exception.ExceptionUtils.getThrowableList(e);
        for (Throwable throwable : exs) {
            if (throwable instanceof SQLException) {
                return (SQLException) throwable;
            }
        }
        return null;
    }

    public static String getRootMessage(Throwable ex) {
        Throwable cause = org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(ex);
        if (cause == null) {
            cause = ex;
        }
        return cause.getMessage();
    }

    public static Throwable getRootCause(Throwable ex) {
        Throwable cause = org.apache.commons.lang3.exception.ExceptionUtils.getRootCause(ex);
        if (cause == null) {
            cause = ex;
        }
        return cause;
    }

    public static boolean is(Throwable e, Class<?>... exceptions) {
        if (e != null) {
            Throwable cause = getRootCause(e);
            for (Class<?> ex : exceptions) {
                if (ex.isInstance(e) || ex.isInstance(cause)) {
                    return true;
                }
            }
        }
        return false;
    }
}
