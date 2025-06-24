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
package org.jumpmind.symmetric.transport.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IBandwidthService;
import org.jumpmind.symmetric.service.impl.BandwidthService;
import org.jumpmind.symmetric.service.impl.MockNodeService;
import org.junit.jupiter.api.Test;

public class HttpBandwidthBalancerTest {
    @Test
    public void testUriParsing() throws Exception {
        URI uri = new URI(
                "ext://plugin/?1=http://rgn.com/sync&2=http://rgn2.com/sync&sampleBytes=1000&sampleTTL=200&initialLoadOnly=true");
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = ext.getParameters(uri);
        assertEquals("http://rgn.com/sync", params.get("1"));
        assertEquals("http://rgn2.com/sync", params.get("2"));
        assertEquals("1000", params.get("sampleBytes"));
        assertEquals("200", params.get("sampleTTL"));
        assertEquals("true", params.get("initialLoadOnly"));
    }

    @Test
    public void testResolveUrl() throws Exception {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(false);
        URI uri = new URI("ext://balancer?10=100&100=50&"
                + HttpBandwidthUrlSelector.PARAM_PRELOAD_ONLY + "=true");
        assertEquals("50", ext.resolveUrl(uri));
        ext = getMockBandwidthBalancer(true);
        assertEquals("100", ext.resolveUrl(uri));
    }

    @Test
    public void testSampleTTL() throws Exception {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        URI uri = new URI("ext://balancer?1=100&2=1&" + HttpBandwidthUrlSelector.PARAM_SAMPLE_TTL
                + "=1000");
        assertEquals("1", ext.resolveUrl(uri));
        long ts = ext.lastSampleTs;
        assertEquals("1", ext.resolveUrl(uri));
        assertEquals(ts, ext.lastSampleTs);
        Thread.sleep(1000);
        assertEquals("1", ext.resolveUrl(uri));
        assertNotSame(ts, ext.lastSampleTs);
    }

    protected HttpBandwidthUrlSelector getMockBandwidthBalancer(final boolean dataLoadCompleted) {
        HttpBandwidthUrlSelector ext = new HttpBandwidthUrlSelector(
                new MockNodeService() {
                    @Override
                    public boolean isDataLoadCompleted() {
                        return dataLoadCompleted;
                    }
                }, new IBandwidthService() {
                    public double getDownloadKbpsFor(String url, long sampleSize,
                            long maxTestDuration) {
                        return sampleSize / Double.parseDouble(url);
                    }

                    @Override
                    public double getDownloadKbpsFor(Node remoteNode, Node localNode, long sampleSize,
                            long maxTestDuration) {
                        return 0;
                    }

                    public double getUploadKbpsFor(Node remoteNode, Node localNode, long sampleSize, long maxTestDuration) {
                        return -1.0d;
                    }

                    public List<BandwidthService.BandwidthResults> diagnoseDownloadBandwidth(Node localNode, Node remoteNode) {
                        return null;
                    }

                    public List<BandwidthService.BandwidthResults> diagnoseUploadBandwidth(Node localNode, Node remoteNode) {
                        return null;
                    }
                });
        return ext;
    }
}