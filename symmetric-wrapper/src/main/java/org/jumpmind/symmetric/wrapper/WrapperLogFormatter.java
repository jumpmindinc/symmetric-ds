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
package org.jumpmind.symmetric.wrapper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class WrapperLogFormatter extends Formatter {
    protected final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    protected static final String NEWLINE = System.getProperty("line.separator");

    @Override
    public String format(LogRecord record) {
        Object[] parms = record.getParameters();
        String source = "wrapper";
        if (parms != null && parms.length > 0) {
            source = parms[0].toString();
        }
        return DATE_FORMATTER.format(new Date(record.getMillis())) + " ["
                + String.format("%-7s", record.getLevel().getName()) + "] [" + String.format("%-7s", source) + "] "
                + record.getMessage() + NEWLINE;
    }
}