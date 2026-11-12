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
package org.jumpmind.symmetric.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.model.Channel;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.Trigger;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultRoutersOnlyDetectorTest {
    private static final Channel CHANNEL = new Channel("test", 1);
    private static final Channel OTHER_CHANNEL = new Channel("other", 2);
    private static final String SOURCE_NODE_GROUP = "source";
    private static final String TARGET_NODE_GROUP = "target";
    private static final long CACHE_TIME_MS = 600000;
    private DefaultRoutersOnlyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DefaultRoutersOnlyDetector();
    }

    @Test
    void testHasOnlyDefaultRouters_withDefaultRouter() {
        assertTrue(hasOnlyDefaultRouters(CHANNEL, newTriggerRouters(newTriggerRouter(CHANNEL, "default"))));
    }

    @Test
    void testHasOnlyDefaultRouters_withColumnRouter() {
        assertFalse(hasOnlyDefaultRouters(CHANNEL, newTriggerRouters(newTriggerRouter(CHANNEL, "default"), newTriggerRouter(CHANNEL, "column"))));
    }

    @Test
    void testHasOnlyDefaultRouters_withNoTriggerRouters() {
        assertTrue(hasOnlyDefaultRouters(CHANNEL, null));
    }

    @Test
    void testHasOnlyDefaultRouters_ignoresColumnRouterOnAnotherChannel() {
        assertTrue(hasOnlyDefaultRouters(CHANNEL, newTriggerRouters(newTriggerRouter(CHANNEL, "default"), newTriggerRouter(OTHER_CHANNEL, "column"))));
    }

    @Test
    void testHasOnlyDefaultRouters_ignoresColumnRouterFromAnotherNodeGroup() {
        TriggerRouter incomingColumnRouter = new TriggerRouter(new Trigger("a", CHANNEL.getChannelId()),
                new Router("column", TARGET_NODE_GROUP, SOURCE_NODE_GROUP, "column"));
        assertTrue(hasOnlyDefaultRouters(CHANNEL, newTriggerRouters(newTriggerRouter(CHANNEL, "default"), incomingColumnRouter)));
    }

    @Test
    void testHasOnlyDefaultRouters_forConfigChannel() {
        Channel configChannel = new Channel(Constants.CHANNEL_CONFIG, 0);
        assertFalse(hasOnlyDefaultRouters(configChannel, newTriggerRouters(newTriggerRouter(configChannel, "default"))));
    }

    @Test
    void testHasOnlyDefaultRouters_forFileSyncChannel() {
        Channel fileSyncChannel = new Channel("filesync", 3);
        fileSyncChannel.setFileSyncFlag(true);
        assertFalse(hasOnlyDefaultRouters(fileSyncChannel, newTriggerRouters(newTriggerRouter(fileSyncChannel, "default"))));
    }

    @Test
    void testHasOnlyDefaultRouters_reusesAnswerForSameTriggerRouters() {
        List<TriggerRouter> triggerRouters = newTriggerRouters(newTriggerRouter(CHANNEL, "default"));
        hasOnlyDefaultRouters(CHANNEL, triggerRouters);
        triggerRouters.add(newTriggerRouter(CHANNEL, "column"));
        assertTrue(hasOnlyDefaultRouters(CHANNEL, triggerRouters));
    }

    @Test
    void testHasOnlyDefaultRouters_whenTriggerRoutersReloaded() {
        hasOnlyDefaultRouters(CHANNEL, newTriggerRouters(newTriggerRouter(CHANNEL, "default")));
        assertFalse(hasOnlyDefaultRouters(CHANNEL, newTriggerRouters(newTriggerRouter(CHANNEL, "column"))));
    }

    @Test
    void testHasOnlyDefaultRouters_whenCacheTimeExpired() {
        List<TriggerRouter> triggerRouters = newTriggerRouters(newTriggerRouter(CHANNEL, "default"));
        detector.hasOnlyDefaultRouters(CHANNEL, SOURCE_NODE_GROUP, triggerRouters, -1);
        triggerRouters.add(newTriggerRouter(CHANNEL, "column"));
        assertFalse(detector.hasOnlyDefaultRouters(CHANNEL, SOURCE_NODE_GROUP, triggerRouters, -1));
    }

    @Test
    void testClear_recalculatesEveryChannel() {
        List<TriggerRouter> triggerRouters = newTriggerRouters(newTriggerRouter(CHANNEL, "default"), newTriggerRouter(OTHER_CHANNEL, "default"));
        hasOnlyDefaultRouters(CHANNEL, triggerRouters);
        hasOnlyDefaultRouters(OTHER_CHANNEL, triggerRouters);
        triggerRouters.add(newTriggerRouter(CHANNEL, "column"));
        detector.clear();
        hasOnlyDefaultRouters(OTHER_CHANNEL, triggerRouters);
        assertFalse(hasOnlyDefaultRouters(CHANNEL, triggerRouters));
    }

    private boolean hasOnlyDefaultRouters(Channel channel, List<TriggerRouter> triggerRouters) {
        return detector.hasOnlyDefaultRouters(channel, SOURCE_NODE_GROUP, triggerRouters, CACHE_TIME_MS);
    }

    private TriggerRouter newTriggerRouter(Channel channel, String routerType) {
        return new TriggerRouter(new Trigger("a", channel.getChannelId()), new Router(routerType, SOURCE_NODE_GROUP, TARGET_NODE_GROUP, routerType));
    }

    private List<TriggerRouter> newTriggerRouters(TriggerRouter... triggerRouters) {
        return new ArrayList<TriggerRouter>(Arrays.asList(triggerRouters));
    }
}
