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
package org.jumpmind.symmetric.io.data.reader;

import org.jumpmind.util.Statistics;

public class DataReaderStatistics extends Statistics {
    public static final String READ_BYTE_COUNT = "READ_BYTE_COUNT";
    public static final String READ_RECORD_COUNT = "READ_RECORD_COUNT";
    public static final String LOAD_FLAG = "LOAD_FLAG";
    public static final String EXTRACT_COUNT = "EXTRACT_COUNT";
    public static final String SENT_COUNT = "SENT_COUNT";
    public static final String LOAD_COUNT = "LOAD_COUNT";
    public static final String LOAD_ID = "LOAD_ID";
    public static final String COMMON_FLAG = "COMMON_FLAG";
    public static final String ROUTER_MILLIS = "ROUTER_MILLIS";
    public static final String EXTRACT_MILLIS = "EXTRACT_MILLIS";
    public static final String TRANSFORM_EXTRACT_MILLIS = "TRANSFORM_EXTRACT_MILLIS";
    public static final String TRANSFORM_LOAD_MILLIS = "TRANSFORM_LOAD_MILLIS";
    public static final String RELOAD_ROW_COUNT = "RELOAD_ROW_COUNT";
    public static final String OTHER_ROW_COUNT = "OTHER_ROW_COUNT";
    public static final String DATA_ROW_COUNT = "DATA_ROW_COUNT";
    public static final String DATA_INSERT_ROW_COUNT = "DATA_INSERT_ROW_COUNT";
    public static final String DATA_UPDATE_ROW_COUNT = "DATA_UPDATE_ROW_COUNT";
    public static final String DATA_DELETE_ROW_COUNT = "DATA_DELETE_ROW_COUNT";
    public static final String EXTRACT_ROW_COUNT = "EXTRACT_ROW_COUNT";
    public static final String EXTRACT_INSERT_ROW_COUNT = "EXTRACT_INSERT_ROW_COUNT";
    public static final String EXTRACT_UPDATE_ROW_COUNT = "EXTRACT_UPDATE_ROW_COUNT";
    public static final String EXTRACT_DELETE_ROW_COUNT = "EXTRACT_DELETE_ROW_COUNT";
    public static final String FAILED_DATA_ID = "FAILED_DATA_ID";
}
