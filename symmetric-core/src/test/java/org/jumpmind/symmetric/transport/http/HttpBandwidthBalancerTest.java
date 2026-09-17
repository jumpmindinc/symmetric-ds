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
package org.jumpmind.symmetric.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IBandwidthService;
import org.jumpmind.symmetric.service.impl.BandwidthService;
import org.jumpmind.symmetric.service.impl.MockNodeService;
import org.jumpmind.symmetric.transport.http.HttpBandwidthUrlSelector.SyncUrl;
import org.junit.jupiter.api.Test;

class HttpBandwidthBalancerTest {
    @Test
    void testResolveUrl_returnsUriStringWhenNotExtProtocol() throws Exception {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        URI uri = new URI("http://plain.example.com/sync");
        assertEquals(uri.toString(), ext.resolveUrl(uri));
    }

    @Test
    void testResolveUrl_sortsByBandwidthWhenInitialLoadNotCompleted() throws Exception {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(false);
        URI uri = new URI("ext://balancer?10=100&100=50&"
                + HttpBandwidthUrlSelector.PARAM_PRELOAD_ONLY + "=true");
        assertEquals("50", ext.resolveUrl(uri));
    }

    @Test
    void testResolveUrl_sortsByListOrderWhenInitialLoadCompleted() throws Exception {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        URI uri = new URI("ext://balancer?10=100&100=50&"
                + HttpBandwidthUrlSelector.PARAM_PRELOAD_ONLY + "=true");
        assertEquals("100", ext.resolveUrl(uri));
    }

    @Test
    void testResolveUrl_cachesUrlsAndSampleWithinTTL() throws Exception {
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

    @Test
    void testGetSampleSize_withValidValue() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put(HttpBandwidthUrlSelector.PARAM_SAMPLE_SIZE, "500");
        assertEquals(500L, ext.getSampleSize(params));
    }

    @Test
    void testGetSampleSize_withInvalidValueReturnsDefault() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put(HttpBandwidthUrlSelector.PARAM_SAMPLE_SIZE, "not-a-number");
        assertEquals(1000L, ext.getSampleSize(params));
    }

    @Test
    void testGetSampleSize_withMissingValueReturnsDefault() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        assertEquals(1000L, ext.getSampleSize(new HashMap<>()));
    }

    @Test
    void testGetMaxSampleDuration_withValidValue() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put(HttpBandwidthUrlSelector.PARAM_MAX_SAMPLE_DURATION, "5000");
        assertEquals(5000L, ext.getMaxSampleDuration(params));
    }

    @Test
    void testGetMaxSampleDuration_withInvalidValueReturnsDefault() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put(HttpBandwidthUrlSelector.PARAM_MAX_SAMPLE_DURATION, "not-a-number");
        assertEquals(2000L, ext.getMaxSampleDuration(params));
    }

    @Test
    void testGetMaxSampleDuration_withMissingValueReturnsDefault() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        assertEquals(2000L, ext.getMaxSampleDuration(new HashMap<>()));
    }

    @Test
    void testGetSampleTTL_withValidValue() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put(HttpBandwidthUrlSelector.PARAM_SAMPLE_TTL, "5000");
        assertEquals(5000L, ext.getSampleTTL(params));
    }

    @Test
    void testGetSampleTTL_withInvalidValueReturnsDefault() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put(HttpBandwidthUrlSelector.PARAM_SAMPLE_TTL, "not-a-number");
        assertEquals(60000L, ext.getSampleTTL(params));
    }

    @Test
    void testGetSampleTTL_withMissingValueReturnsDefault() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        assertEquals(60000L, ext.getSampleTTL(new HashMap<>()));
    }

    @Test
    void testIsInitialLoadOnly_withTrueValueIsCaseInsensitive() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put(HttpBandwidthUrlSelector.PARAM_PRELOAD_ONLY, "TRUE");
        assertTrue(ext.isInitialLoadOnly(params));
    }

    @Test
    void testIsInitialLoadOnly_withFalseValue() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put(HttpBandwidthUrlSelector.PARAM_PRELOAD_ONLY, "false");
        assertFalse(ext.isInitialLoadOnly(params));
    }

    @Test
    void testIsInitialLoadOnly_withMissingValueReturnsFalse() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        assertFalse(ext.isInitialLoadOnly(new HashMap<>()));
    }

    @Test
    void testGetUrls_parsesNumericKeysOnly() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put("2", "http://second.example.com");
        params.put("1", "http://first.example.com");
        params.put(HttpBandwidthUrlSelector.PARAM_PRELOAD_ONLY, "true");
        List<SyncUrl> urls = ext.getUrls(params);
        assertEquals(2, urls.size());
        for (SyncUrl syncUrl : urls) {
            if (syncUrl.order == 1) {
                assertEquals("http://first.example.com", syncUrl.url);
            } else {
                assertEquals(2, syncUrl.order);
                assertEquals("http://second.example.com", syncUrl.url);
            }
        }
    }

    @Test
    void testGetUrls_withNoNumericKeysReturnsEmptyList() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = new HashMap<>();
        params.put(HttpBandwidthUrlSelector.PARAM_PRELOAD_ONLY, "true");
        assertTrue(ext.getUrls(params).isEmpty());
    }

    @Test
    void testGetParameters_parsesQueryString() throws Exception {
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
    void testGetParameters_ignoresParamsWithoutValue() throws Exception {
        URI uri = new URI("ext://plugin/?flag&1=http://rgn.com/sync");
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        Map<String, String> params = ext.getParameters(uri);
        assertEquals(1, params.size());
        assertEquals("http://rgn.com/sync", params.get("1"));
    }

    @Test
    void testSetDefaultSampleSize_appliesToGetSampleSize() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        ext.setDefaultSampleSize(5000);
        assertEquals(5000L, ext.getSampleSize(new HashMap<>()));
    }

    @Test
    void testSetDefaultSampleTTL_appliesToGetSampleTTL() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        ext.setDefaultSampleTTL(5000);
        assertEquals(5000L, ext.getSampleTTL(new HashMap<>()));
    }

    @Test
    void testSetDefaultMaxSampleDuration_appliesToGetMaxSampleDuration() {
        HttpBandwidthUrlSelector ext = getMockBandwidthBalancer(true);
        ext.setDefaultMaxSampleDuration(5000);
        assertEquals(5000L, ext.getMaxSampleDuration(new HashMap<>()));
    }

    private HttpBandwidthUrlSelector getMockBandwidthBalancer(final boolean dataLoadCompleted) {
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