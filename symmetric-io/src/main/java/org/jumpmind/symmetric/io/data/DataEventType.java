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
package org.jumpmind.symmetric.io.data;

import org.jumpmind.db.sql.DmlStatement.DmlType;

/**
 * 
 */
public enum DataEventType {
    /**
     * Insert DML type.
     */
    INSERT("I"),
    /**
     * Update DML type.
     */
    UPDATE("U"),
    /**
     * Delete DML type.
     */
    DELETE("D"),
    /**
     * An event that indicates that a table needs to be reloaded.
     */
    RELOAD("R"),
    /**
     * An event that indicates that the data payload has a sql statement that needs to be executed. This is more of a remote control feature (that would have
     * been very handy in past lives).
     */
    SQL("S"),
    /**
     * An event that indicates that the data payload is a table creation.
     */
    CREATE("C"),
    /**
     * An event the indicates that the data payload is going to be a Java bean shell script that is to be run at the client.
     */
    BSH("B"), UNKNOWN("U");

    private String code;

    DataEventType(String code) {
        this.code = code;
    }

    public boolean isDml() {
        return this == INSERT || this == DELETE || this == UPDATE;
    }

    public String getCode() {
        return this.code;
    }

    public DmlType getDmlType() {
        switch (this) {
            case INSERT:
                return DmlType.INSERT;
            case UPDATE:
                return DmlType.UPDATE;
            case DELETE:
                return DmlType.DELETE;
            default:
                return DmlType.UNKNOWN;
        }
    }

    public static DataEventType getEventType(String s) {
        if (s.equals(INSERT.getCode())) {
            return INSERT;
        } else if (s.equals(UPDATE.getCode())) {
            return UPDATE;
        } else if (s.equals(DELETE.getCode())) {
            return DELETE;
        } else if (s.equals(RELOAD.getCode())) {
            return RELOAD;
        } else if (s.equals(SQL.getCode())) {
            return SQL;
        } else if (s.equals(CREATE.getCode())) {
            return CREATE;
        } else if (s.equals(RELOAD.getCode())) {
            return RELOAD;
        } else if (s.equals(BSH.getCode())) {
            return BSH;
        } else {
            throw new IllegalStateException(String.format("Invalid data event type of %s", s));
        }
    }
}