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
package org.jumpmind.db.platform.ase;

import org.jumpmind.db.AbstractDdlTypesTest;
import org.jumpmind.db.platform.DatabaseNamesConstants;

public class AseDdlTypesTest extends AbstractDdlTypesTest {
    @Override
    protected String getName() {
        return DatabaseNamesConstants.ASE;
    }

    @Override
    protected String[] getDdlTypes() {
        return new String[] { "tinyint", "smallint", "integer", "bigint", "numeric", "decimal",
                "float", "double precision", "real", // "smallmoney", "money",
                "smalldatetime", "datetime", "date", "time", "bigdatetime", "bigtime",
                "char(10)", "varchar(10)", "text", // "nchar(10)", "nvarchar(10)", "unitext", "unichar(10)", "univarchar(10)"
                "binary(10)", "varbinary(10)", "image", "bit"
        };
    }

    @Override
    protected boolean hasNullDefault(String columnType) {
        return !columnType.equals("bit");
    }
}
