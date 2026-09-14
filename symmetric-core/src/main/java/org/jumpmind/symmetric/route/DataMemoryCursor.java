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
package org.jumpmind.symmetric.route;

import static org.jumpmind.symmetric.common.Constants.LOG_PROCESS_SUMMARY_THRESHOLD;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

import org.jumpmind.db.sql.ISqlReadCursor;
import org.jumpmind.symmetric.model.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataMemoryCursor implements ISqlReadCursor<Data> {
    private final static Logger log = LoggerFactory.getLogger(DataMemoryCursor.class);
    public final static Comparator<Data> SORT_BY_TIME = new DataByTimeComparator();
    public final static Comparator<Data> SORT_BY_ID = new DataByIdComparator();
    protected Iterator<Data> iter;

    public DataMemoryCursor(ISqlReadCursor<Data> cursor, ChannelRouterContext context, Comparator<Data> comparator) {
        ArrayList<Data> datas = new ArrayList<Data>();
        Data data = null;
        long ts = System.currentTimeMillis();
        try {
            while ((data = cursor.next()) != null) {
                datas.add(data);
                long totalTimeInMs = System.currentTimeMillis() - ts;
                if (totalTimeInMs > LOG_PROCESS_SUMMARY_THRESHOLD) {
                    log.info(
                            "Reading data to route for channel '{}' has been processing for {} seconds. The following stats have been gathered: dataCount={}",
                            context.getChannel().getChannelId(), (System.currentTimeMillis() - context.getCreatedTimeInMs()) / 1000,
                            datas.size());
                    ts = System.currentTimeMillis();
                }
            }
        } finally {
            cursor.close();
        }
        if (comparator != null) {
            log.debug("Sorting in memory with {}", comparator.getClass().getSimpleName());
            datas.sort(comparator);
        }
        this.iter = datas.iterator();
    }

    @Override
    public Data next() {
        if (this.iter.hasNext()) {
            return this.iter.next();
        }
        return null;
    }

    @Override
    public void close() {
        this.iter = null;
    }

    static protected class DataByTimeComparator implements Comparator<Data> {
        @Override
        public int compare(Data o1, Data o2) {
            return o1.getCreateTime().compareTo(o2.getCreateTime());
        }
    }

    static protected class DataByIdComparator implements Comparator<Data> {
        @Override
        public int compare(Data o1, Data o2) {
            return o1.getDataId() > o2.getDataId() ? 1 : -1;
        }
    }
}
