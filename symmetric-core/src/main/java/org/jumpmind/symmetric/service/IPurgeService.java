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
package org.jumpmind.symmetric.service;

import java.util.Calendar;

/**
 * This service provides an API to kick off purge processes with or without specific dates.
 * <p/>
 * This service will never purge data that has not been delivered to a target node that is still enabled.
 */
public interface IPurgeService {
    public long purgeOutgoing(boolean force);

    public long purgeIncoming(boolean force);

    public long purgeOutgoing(Calendar retentionCutoff, boolean force);

    public long purgeIncoming(Calendar retentionCutoff, boolean force);

    public void purgeAllIncomingEventsForNode(String nodeId);

    public void purgeStats(boolean force);
}