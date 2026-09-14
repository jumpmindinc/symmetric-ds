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
package org.jumpmind.symmetric.extract;

import org.jumpmind.symmetric.model.Data;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerHistory;
import org.jumpmind.symmetric.model.TriggerRouter;

public class SelectFromTableEvent {
    private TriggerRouter triggerRouter;
    private TriggerHistory triggerHistory;
    private Node node;
    private Data data;
    private String initialLoadSelect;

    public SelectFromTableEvent(Node node, TriggerRouter triggerRouter, TriggerHistory triggerHistory, String initialLoadSelect) {
        this.node = node;
        this.triggerRouter = triggerRouter;
        this.initialLoadSelect = initialLoadSelect;
        this.triggerHistory = triggerHistory;
    }

    public SelectFromTableEvent(Data data, TriggerRouter triggerRouter) {
        this.data = data;
        this.triggerHistory = data.getTriggerHistory();
        this.triggerRouter = triggerRouter;
    }

    public TriggerHistory getTriggerHistory() {
        return triggerHistory;
    }

    public TriggerRouter getTriggerRouter() {
        return triggerRouter;
    }

    public Data getData() {
        return data;
    }

    public Node getNode() {
        return node;
    }

    public boolean containsData() {
        return data != null;
    }

    public String getInitialLoadSelect() {
        return initialLoadSelect;
    }
}
