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
package org.jumpmind.vaadin.ui.common;

import org.apache.commons.lang3.Strings;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.splitlayout.SplitLayout;

@DomEvent("splitter-resized")
public class SplitterResizedEvent extends ComponentEvent<SplitLayout> {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private final double leftWidth;
    private final double rightWidth;

    /**
     * Creates a new event using the given source and indicator whether the event originated from the client side or the server side.
     *
     * @param source
     *            the source component
     * @param fromClient
     *            <code>true</code> if the event originated from the client
     */
    public SplitterResizedEvent(SplitLayout source, boolean fromClient, @EventData("event.detail.leftWidth") String leftWidth,
            @EventData("event.detail.rightWidth") String rightWidth) {
        super(source, fromClient);
        this.leftWidth = Double.parseDouble(Strings.CS.remove(leftWidth, "px"));
        this.rightWidth = Double.parseDouble(Strings.CS.remove(rightWidth, "px"));
    }

    public double getLeftWidth() {
        return leftWidth;
    }

    public double getRightWidth() {
        return rightWidth;
    }
}
