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
package org.jumpmind.util;

import java.nio.charset.Charset;
import java.security.SecureRandom;

/**
 * Use runtime configuration specific seeding to get a random number for use in time slotting nodes to help stagger load.
 */
public class RandomTimeSlot {
    protected int maxValue = -1;
    protected SecureRandom random;

    public RandomTimeSlot() {
        random = new SecureRandom();
    }

    public RandomTimeSlot(String externalId, int maxValue) {
        this.maxValue = maxValue;
        if (externalId != null) {
            random = new SecureRandom(externalId.getBytes(Charset.defaultCharset()));
        }
    }

    public int getRandomValueSeededByExternalId() {
        int nextValue = random.nextInt(maxValue);
        return nextValue == 0 ? 1 : nextValue;
    }
}