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
package org.jumpmind.vaadin.ui.sqlexplorer;

import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.SQL_EXPLORER_SHOW_ROW_NUMBERS;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.jumpmind.db.sql.IConnectionCallback;
import org.jumpmind.db.sql.JdbcSqlTemplate;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.vaadin.ui.common.ColumnVisibilityToggler;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.component.grid.Grid;

abstract public class AbstractMetaDataGridCreator {
    final Logger log = LoggerFactory.getLogger(getClass());
    JdbcSqlTemplate sqlTemplate;
    org.jumpmind.db.model.Table table;
    String folder;
    Settings settings;
    protected final String[] TABLE_NAME_METADATA_COLUMNS = new String[] { "TABLE_NAME",
            "TABLE_CATALOG", "TABLE_SCHEMA", "PKTABLE_NAME", "PKTABLE_CATALOG", "PKTABLE_SCHEMA",
            "TABLE_CAT", "TABLE_SCHEM" };

    public AbstractMetaDataGridCreator(JdbcSqlTemplate sqlTemplate, org.jumpmind.db.model.Table table,
            Settings settings) {
        this.sqlTemplate = sqlTemplate;
        this.table = table;
        this.settings = settings;
    }

    public Grid<List<Object>> create(ColumnVisibilityToggler columnVisibilityToggler) {
        return sqlTemplate.execute(new IConnectionCallback<Grid<List<Object>>>() {
            public Grid<List<Object>> execute(Connection con) throws SQLException {
                TypedProperties properties = settings.getProperties();
                ResultSet rs = null;
                Grid<List<Object>> g = null;
                try {
                    DatabaseMetaData metadata = con.getMetaData();
                    rs = getMetaDataResultSet(metadata);
                    g = CommonUiUtils.putResultsInGrid(columnVisibilityToggler, rs, Integer.MAX_VALUE,
                            properties.is(SQL_EXPLORER_SHOW_ROW_NUMBERS), getColumnsToExclude());
                    g.setSizeFull();
                    return g;
                } catch (Exception ex) {
                    log.info(
                            "Failed to retrieve meta data.  It might be that this driver doesn't support it.  Turn on debug logging to see the resulting stacktrace");
                    log.debug("", ex);
                    return new Grid<List<Object>>();
                } finally {
                    JdbcSqlTemplate.close(rs);
                }
            }
        });
    }

    protected String[] getColumnsToExclude() {
        return new String[0];
    }

    abstract protected ResultSet getMetaDataResultSet(DatabaseMetaData metadata)
            throws SQLException;
}
