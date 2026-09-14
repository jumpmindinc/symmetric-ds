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
package org.jumpmind.symmetric.model;

import java.io.Serializable;
import java.sql.Time;
import java.util.Date;

import org.apache.commons.lang3.time.FastDateFormat;
import org.jumpmind.util.AppUtils;

/**
 * 
 */
public class NodeGroupChannelWindow implements Serializable {
    private static final long serialVersionUID = 1L;
    private String nodeGroupId;
    private String channelId;
    private Time startTime;
    private Time endTime;
    private boolean enabled;
    final FastDateFormat HHmmss = FastDateFormat.getInstance("HH:mm:ss");

    public String getNodeGroupId() {
        return nodeGroupId;
    }

    public void setNodeGroupId(String nodeGroupId) {
        this.nodeGroupId = nodeGroupId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public Time getStartTime() {
        return startTime;
    }

    public void setStartTime(Time startTime) {
        this.startTime = Time.valueOf(HHmmss.format(startTime));
    }

    public Time getEndTime() {
        return endTime;
    }

    public void setEndTime(Time endTime) {
        this.endTime = Time.valueOf(HHmmss.format(endTime));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean inTimeWindow(Date date) {
        Time time = Time.valueOf(HHmmss.format(date));
        return inTimeWindow(time);
    }

    public boolean inTimeWindow(Time time) {
        if (enabled) {
            return (startTime.before(time) && endTime.after(time))
                    || (startTime.before(time) && endTime.before(startTime))
                    || (endTime.after(time) && startTime.after(endTime));
        } else {
            return true;
        }
    }

    public boolean inTimeWindow(String timezoneOffset) {
        return inTimeWindow(AppUtils.getLocalDateForOffset(timezoneOffset));
    }
}