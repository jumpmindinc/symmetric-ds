/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.vaadin.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.splitlayout.SplitLayout;

class SplitterResizedEventTest {
    @Test
    void constructor_parsesLeftWidthFromPixelString() {
        SplitterResizedEvent event = new SplitterResizedEvent(new SplitLayout(), false, "300.0px", "500.0px");
        assertEquals(300.0, event.getLeftWidth(), 0.001);
    }

    @Test
    void constructor_parsesRightWidthFromPixelString() {
        SplitterResizedEvent event = new SplitterResizedEvent(new SplitLayout(), false, "300.0px", "500.0px");
        assertEquals(500.0, event.getRightWidth(), 0.001);
    }

    @Test
    void constructor_withIntegerPixelValues_parsesCorrectly() {
        SplitterResizedEvent event = new SplitterResizedEvent(new SplitLayout(), false, "400px", "600px");
        assertEquals(400.0, event.getLeftWidth(), 0.001);
        assertEquals(600.0, event.getRightWidth(), 0.001);
    }

    @Test
    void constructor_withSmallValues_parsesCorrectly() {
        SplitterResizedEvent event = new SplitterResizedEvent(new SplitLayout(), false, "1.5px", "998.5px");
        assertEquals(1.5, event.getLeftWidth(), 0.001);
        assertEquals(998.5, event.getRightWidth(), 0.001);
    }
}
