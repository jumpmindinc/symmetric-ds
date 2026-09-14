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

import java.sql.Time;
import java.util.Calendar;

import static org.junit.Assert.*;
import org.junit.jupiter.api.Test;

public class NodeGroupChannelWindowTest {
    @Test
    public void testInWindowEnabled() {
        NodeGroupChannelWindow window = new NodeGroupChannelWindow();
        window.setEnabled(true);
        window.setStartTime(Time.valueOf("13:00:00"));
        window.setEndTime(Time.valueOf("14:00:00"));
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 13);
        cal.set(Calendar.MINUTE, 5);
        assertTrue(window.inTimeWindow(cal.getTime()));
        cal.add(Calendar.DATE, 1);
        assertTrue(window.inTimeWindow(cal.getTime()));
        cal.add(Calendar.DATE, -100);
        assertTrue(window.inTimeWindow(cal.getTime()));
    }

    @Test
    public void testInWindowEnabledCrossDayBoundary() {
        NodeGroupChannelWindow window = new NodeGroupChannelWindow();
        window.setEnabled(true);
        window.setStartTime(Time.valueOf("21:00:00"));
        window.setEndTime(Time.valueOf("03:00:00"));
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 13);
        cal.set(Calendar.MINUTE, 5);
        assertFalse(window.inTimeWindow(cal.getTime()));
        cal.set(Calendar.HOUR_OF_DAY, 22);
        assertTrue(window.inTimeWindow(cal.getTime()));
        cal.set(Calendar.HOUR_OF_DAY, 2);
        assertTrue(window.inTimeWindow(cal.getTime()));
    }

    @Test
    public void testInWindowDisabled() {
        NodeGroupChannelWindow window = new NodeGroupChannelWindow();
        window.setEnabled(false);
        window.setStartTime(Time.valueOf("13:00:00"));
        window.setEndTime(Time.valueOf("14:00:00"));
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 13);
        cal.set(Calendar.MINUTE, 5);
        assertTrue(window.inTimeWindow(cal.getTime()));
        cal.add(Calendar.DATE, 1);
        assertTrue(window.inTimeWindow(cal.getTime()));
        cal.add(Calendar.DATE, -100);
        assertTrue(window.inTimeWindow(cal.getTime()));
    }

    @Test
    public void testOutOfWindowDisabled() {
        NodeGroupChannelWindow window = new NodeGroupChannelWindow();
        window.setEnabled(false);
        window.setStartTime(Time.valueOf("13:00:00"));
        window.setEndTime(Time.valueOf("14:00:00"));
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 17);
        cal.set(Calendar.MINUTE, 5);
        assertTrue(window.inTimeWindow(cal.getTime()));
        cal.add(Calendar.DATE, 1);
        assertTrue(window.inTimeWindow(cal.getTime()));
        cal.add(Calendar.DATE, -100);
        assertTrue(window.inTimeWindow(cal.getTime()));
    }

    @Test
    public void testOutOfWindowEnabled() {
        NodeGroupChannelWindow window = new NodeGroupChannelWindow();
        window.setEnabled(true);
        window.setStartTime(Time.valueOf("09:00:00"));
        window.setEndTime(Time.valueOf("09:30:00"));
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 8);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 30);
        assertFalse(window.inTimeWindow(cal.getTime()));
        cal.add(Calendar.DATE, 423);
        assertFalse(window.inTimeWindow(cal.getTime()));
        cal.add(Calendar.DATE, -1);
        assertFalse(window.inTimeWindow(cal.getTime()));
    }
}