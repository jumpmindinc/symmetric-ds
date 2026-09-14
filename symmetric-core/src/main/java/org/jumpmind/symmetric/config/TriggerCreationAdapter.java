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
package org.jumpmind.symmetric.config;

import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;

/**
 * An adapter for the trigger listener interface so you need only implement the methods you are interested in.
 *
 * 
 */
public class TriggerCreationAdapter implements ITriggerCreationListener {
    public void syncTriggersStarted() {
    }

    public void tableDoesNotExist(int triggersToSync, int triggersSynced, Trigger trigger) {
    }

    public void triggerCreated(int triggersToSync, int triggersSynced, Trigger trigger, TriggerHistory history) {
    }

    public void triggerChecked(int triggersToSync, int triggersSynced) {
    }

    public void triggerFailed(int triggersToSync, int triggersSynced, Trigger trigger, Exception ex) {
    }

    public void triggerInactivated(int triggersToSync, int triggersSynced, Trigger trigger, TriggerHistory oldHistory) {
    }

    public void syncTriggersEnded() {
    }
}