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
package org.jumpmind.symmetric.io;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.jumpmind.db.model.Column;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.writer.AbstractDatabaseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HbaseDatabaseWriter extends AbstractDatabaseWriter {
    private static final Logger log = LoggerFactory.getLogger(HbaseDatabaseWriter.class);
    private Configuration config;
    private Connection connection;
    private Table table;
    private String hbaseSiteXmlPath;

    public HbaseDatabaseWriter(String hbaseSiteXmlPath) {
        this.hbaseSiteXmlPath = hbaseSiteXmlPath;
    }

    protected void setup() {
        try {
            if (config == null) {
                config = HBaseConfiguration.create();
                config.addResource(new Path(this.hbaseSiteXmlPath));
            }
            if (connection == null) {
                log.debug("Establishing connection to HBase");
                connection = ConnectionFactory.createConnection(config);
            }
            if (table == null) {
                log.debug("Connected to HBase, now looking up table " + this.targetTable.getName());
                table = connection.getTable(TableName.valueOf(this.targetTable.getName()));
            }
        } catch (IOException e) {
            log.error("Unable to connect to HBase ", e);
        }
    }

    protected LoadStatus put(CsvData data) {
        try {
            setup();
            Put put = new Put(data.getPkData(this.targetTable)[0].getBytes(Charset.defaultCharset()));
            String[] values = data.getParsedData(CsvData.ROW_DATA);
            Column[] columns = sourceTable.getColumns();
            List<Put> putList = new ArrayList<Put>();
            for (int i = 0; i < columns.length; i++) {
                if (columns[i].getName().contains(":")) {
                    log.debug("Preparing put statement into Hbase.");
                    String[] split = columns[i].getName().split(":");
                    byte[] columnFamily = split[0].getBytes(Charset.defaultCharset());
                    byte[] columnName = split[1].getBytes(Charset.defaultCharset());
                    byte[] value = StringUtils.isEmpty(values[i]) ? new byte[0] : values[i].getBytes(Charset.defaultCharset());
                    put.addColumn(columnFamily, columnName, value);
                    putList.add(put);
                }
            }
            log.debug("Put list for HBase complete with a size of " + putList.size());
            table.put(putList);
            log.debug("Put rows into HBase now closing connection");
            table.close();
        } catch (IOException e) {
            log.error("Unable to load data into HBase ", e);
            throw new RuntimeException(e);
        }
        return LoadStatus.SUCCESS;
    }

    @Override
    protected LoadStatus insert(CsvData data) {
        return put(data);
    }

    @Override
    protected LoadStatus delete(CsvData data, boolean useConflictDetection) {
        setup();
        String[] pkData = data.getParsedData(CsvData.PK_DATA);
        if (pkData != null && pkData.length == 1) {
            Delete delete = new Delete(pkData[0].getBytes(Charset.defaultCharset()));
            try {
                table.delete(delete);
            } catch (IOException e) {
                log.error("Unable to delete data for table " + this.targetTable.getName() +
                        ", for primary key " + pkData[0]);
                throw new RuntimeException(e);
            }
        }
        return LoadStatus.SUCCESS;
    }

    @Override
    protected LoadStatus update(CsvData data, boolean applyChangesOnly, boolean useConflictDetection) {
        return put(data);
    }

    @Override
    protected boolean create(CsvData data) {
        return false;
    }

    @Override
    protected boolean sql(CsvData data) {
        return false;
    }

    @Override
    protected void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails) {
    }

    @Override
    protected void logFailureDetails(Throwable e, CsvData data, boolean logLastDmlDetails, Object[] values) {
    }
}
