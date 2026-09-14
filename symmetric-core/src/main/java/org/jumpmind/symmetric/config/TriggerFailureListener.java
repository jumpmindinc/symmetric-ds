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

import java.util.HashMap;
import java.util.Map;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerHistory;

/**
 * A listener that was built specifically to 'listen' for failures.
 *
 * 
 */
public class TriggerFailureListener extends TriggerCreationAdapter implements IBuiltInExtensionPoint {
    private Map<Trigger, Exception> failures;

    public TriggerFailureListener() {
        this.failures = new HashMap<Trigger, Exception>();
    }

    @Override
    public void triggerCreated(int triggersToSync, int triggersSynced, Trigger trigger, TriggerHistory history) {
        failures.remove(trigger);
    }

    @Override
    public void triggerInactivated(int triggersToSync, int triggersSynced, Trigger trigger, TriggerHistory oldHistory) {
        this.failures.remove(trigger);
    }

    @Override
    public void triggerFailed(int triggersToSync, int triggersSynced, Trigger trigger, Exception ex) {
        this.failures.put(trigger, ex);
    }

    public Map<Trigger, Exception> getFailures() {
        return failures;
    }
}