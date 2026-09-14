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
package org.jumpmind.symmetric.io.data.transform;

import java.util.ArrayList;
import java.util.List;

import org.jumpmind.db.model.Table;
import org.jumpmind.symmetric.io.data.CsvData;

public class CsvDataHier {
    private CsvData csvData;
    private List<CsvDataHier> children;
    private String catalog;
    private String schema;
    private String table;
    private String relationType;

    public CsvDataHier(CsvData csvData, Table table, String relationType) {
        this.csvData = csvData;
        this.catalog = table.getCatalog();
        this.schema = table.getSchema();
        this.table = table.getName();
        this.relationType = relationType;
    }

    public CsvData getCsvData() {
        return csvData;
    }

    public void setCsvData(CsvData csvData) {
        this.csvData = csvData;
    }

    public List<CsvDataHier> getChildren() {
        return children;
    }

    public void setChildren(List<CsvDataHier> children) {
        this.children = children;
    }

    public void addChild(CsvDataHier csvDataHier) {
        if (children == null) {
            children = new ArrayList<CsvDataHier>();
        }
        children.add(csvDataHier);
    }

    public String getCatalog() {
        return catalog;
    }

    public String getCatalogLowerCase() {
        return catalog != null ? catalog.toLowerCase() : null;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getSchema() {
        return schema;
    }

    public String getSchemaLowerCase() {
        return schema != null ? schema.toLowerCase() : null;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getTable() {
        return table;
    }

    public String getTableLowerCase() {
        return table != null ? table.toLowerCase() : null;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }
}
