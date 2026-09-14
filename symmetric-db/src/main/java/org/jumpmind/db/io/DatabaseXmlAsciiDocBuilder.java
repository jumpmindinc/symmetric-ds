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
package org.jumpmind.db.io;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jumpmind.db.model.Column;
import org.jumpmind.db.model.Database;
import org.jumpmind.db.model.ForeignKey;
import org.jumpmind.db.model.Reference;
import org.jumpmind.db.model.Table;

public class DatabaseXmlAsciiDocBuilder {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: <input_xml_file> <output_asciidoc_file>");
            System.exit(-1);
        }
        String[] paths = args[0].split(",");
        List<Table> allTables = new ArrayList<Table>();
        for (String path : paths) {
            Database db = DatabaseXmlUtil.read(new File(path));
            allTables.addAll(Arrays.asList(db.getTables()));
        }
        allTables.sort(null);
        PrintWriter out = new PrintWriter(new FileWriter(args[1]));
        for (Table table : allTables) {
            out.println("=== " + table.getName().toUpperCase());
            out.println();
            if (isNotBlank(table.getDescription())) {
                out.println(table.getDescription());
            }
            out.println();
            out.print(".");
            out.println(table.getName().toUpperCase());
            out.println("[cols=\"3,^1,^1,^1,^1,^1,5\"]");
            out.println("|===");
            out.println();
            out.println("|Name|Type|Size|Default|Keys|Not Null|Description");
            for (Column column : table.getColumns()) {
                out.print("|");
                out.print(column.getName().toUpperCase());
                out.print("|");
                out.print(column.getMappedType());
                out.print("|");
                out.print(isNotBlank(column.getSize()) ? column.getSize() : " ");
                out.print("|");
                out.print(isNotBlank(column.getDefaultValue()) ? column.getDefaultValue() : " ");
                out.print("|");
                if (column.isPrimaryKey()) {
                    out.print("PK ");
                }
                ForeignKey[] keys = table.getForeignKeys();
                boolean fk = false;
                for (ForeignKey foreignKey : keys) {
                    Reference[] references = foreignKey.getReferences();
                    for (Reference reference : references) {
                        if (reference.getLocalColumn().getName().equals(column.getName()) && !fk) {
                            out.print("FK");
                            fk = true;
                        }
                    }
                }
                out.print("|");
                if (column.isRequired()) {
                    out.print("X");
                }
                out.print("|");
                out.println(column.getDescription());
            }
            out.println("|===");
        }
        out.close();
    }

    public static String toTitle(String tableName) {
        String[] tokens = tableName.split("_");
        StringBuilder name = new StringBuilder();
        for (String string : tokens) {
            name.append(Character.toUpperCase(string.charAt(0)));
            name.append(string.substring(1));
            name.append(" ");
        }
        return name.toString();
    }
}
