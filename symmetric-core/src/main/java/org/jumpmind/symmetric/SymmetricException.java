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
package org.jumpmind.symmetric;

/**
 * This is a {@link RuntimeException} that supports using the SymmetricDS {@link Message} infrastructure
 */
public class SymmetricException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SymmetricException() {
        super();
    }

    public SymmetricException(Throwable cause) {
        super(cause);
    }

    public SymmetricException(String message, Object... args) {
        super(String.format(message, args));
    }

    public SymmetricException(String message, Throwable cause) {
        super(message, cause);
    }

    public SymmetricException(String message, Throwable cause, Object... args) {
        super(String.format(message, args), cause);
    }

    public Throwable getRootCause() {
        Throwable rootCause = null;
        Throwable cause = getCause();
        while (cause != null && cause != rootCause) {
            rootCause = cause;
            cause = cause.getCause();
        }
        return rootCause;
    }
}