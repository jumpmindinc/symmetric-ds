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
package org.jumpmind.symmetric.wrapper;

public class WrapperException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private int errorCode;
    private int nativeErrorCode;
    private String message;
    private Throwable cause;

    public WrapperException(int errorCode, int nativeErrorCode, String message) {
        this.errorCode = errorCode;
        this.nativeErrorCode = nativeErrorCode;
        this.message = message;
    }

    public WrapperException(int errorCode, int nativeErrorCode, String message, Throwable cause) {
        this.cause = cause;
    }

    @Override
    public String toString() {
        String s = "Error " + errorCode + " [" + message + "]";
        if (nativeErrorCode > 0) {
            s += " with native error " + nativeErrorCode;
        }
        if (cause != null) {
            s += " caused by " + cause.getClass().getSimpleName() + " [" + cause.getMessage() + "]";
        }
        return s;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getNativeErrorCode() {
        return nativeErrorCode;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public Throwable getCause() {
        return cause;
    }
}
