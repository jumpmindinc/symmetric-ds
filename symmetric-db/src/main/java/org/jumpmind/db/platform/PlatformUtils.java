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
package org.jumpmind.db.platform;

import java.sql.Types;

import org.jumpmind.db.model.TypeMap;

abstract public class PlatformUtils {
    /**
     * Determines whether the system supports the Java 1.4 JDBC Types, DATALINK and BOOLEAN.
     * 
     * @return <code>true</code> if BOOLEAN and DATALINK are available
     */
    public static boolean supportsJava14JdbcTypes() {
        try {
            return (Types.class.getField(TypeMap.BOOLEAN) != null)
                    && (Types.class.getField(TypeMap.DATALINK) != null);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Determines the type code for the BOOLEAN JDBC type.
     * 
     * @return The type code
     * @throws UnsupportedOperationException
     *             If the BOOLEAN type is not supported
     */
    public static int determineBooleanTypeCode() throws UnsupportedOperationException {
        try {
            return Types.class.getField(TypeMap.BOOLEAN).getInt(null);
        } catch (Exception ex) {
            throw new UnsupportedOperationException("The jdbc type BOOLEAN is not supported", ex);
        }
    }

    /**
     * Determines the type code for the DATALINK JDBC type.
     * 
     * @return The type code
     * @throws UnsupportedOperationException
     *             If the DATALINK type is not supported
     */
    public static int determineDatalinkTypeCode() throws UnsupportedOperationException {
        try {
            return Types.class.getField(TypeMap.DATALINK).getInt(null);
        } catch (Exception ex) {
            throw new UnsupportedOperationException("The jdbc type DATALINK is not supported", ex);
        }
    }
}
