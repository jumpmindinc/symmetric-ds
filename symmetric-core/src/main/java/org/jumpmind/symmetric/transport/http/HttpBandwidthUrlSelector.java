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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.common.Constants;
import org.jumpmind.symmetric.service.IBandwidthService;
import org.jumpmind.symmetric.service.INodeService;
import org.jumpmind.symmetric.transport.ISyncUrlExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link ISyncUrlExtension} is capable of measuring the bandwidth of a list of urls in order to select the one with the most bandwidth for use.
 * <p/>
 * Use the URI notation of: ext://httpBandwidthUrlSelector?1=http://url.1.com&2=http://url.2.com&param=value
 * <p/>
 * Valid parameters are constants on this class that start with PARAM_. Any parameter that is a numeral will be designated a possible URL.
 */
public class HttpBandwidthUrlSelector implements ISyncUrlExtension, IBuiltInExtensionPoint {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    public static String PARAM_PRELOAD_ONLY = "initialLoadOnly";
    public static String PARAM_SAMPLE_SIZE = "sampleSize";
    public static String PARAM_SAMPLE_TTL = "sampleTTL";
    public static String PARAM_MAX_SAMPLE_DURATION = "maxSampleDuration";
    private long defaultSampleSize = 1000;
    private long defaultSampleTTL = 60000;
    private long defaultMaxSampleDuration = 2000;
    protected long lastSampleTs;
    private Map<URI, List<SyncUrl>> cachedUrls = new HashMap<URI, List<SyncUrl>>();
    private INodeService nodeService;
    private IBandwidthService bandwidthService;

    public HttpBandwidthUrlSelector(INodeService nodeService,
            IBandwidthService bandwidthService) {
        this.nodeService = nodeService;
        this.bandwidthService = bandwidthService;
    }

    public String resolveUrl(URI uri) {
        if (uri.toASCIIString().startsWith(Constants.PROTOCOL_EXT)) {
            Map<String, String> params = getParameters(uri);
            List<SyncUrl> urls = null;
            if (!cachedUrls.containsKey(uri)) {
                urls = getUrls(params);
                cachedUrls.put(uri, urls);
            } else {
                urls = cachedUrls.get(uri);
            }
            boolean initialLoadOnly = isInitialLoadOnly(params);
            if ((initialLoadOnly && nodeService != null && !nodeService.isDataLoadCompleted()) || !initialLoadOnly) {
                long ts = System.currentTimeMillis();
                if (ts - getSampleTTL(params) > lastSampleTs) {
                    for (SyncUrl syncUrl : urls) {
                        syncUrl.kbps = bandwidthService.getDownloadKbpsFor(syncUrl.url, getSampleSize(params),
                                getMaxSampleDuration(params));
                    }
                    lastSampleTs = ts;
                    Collections.sort(urls, new BestBandwidthSorter());
                }
                return urls.get(0).url;
            } else {
                Collections.sort(urls, new ListOrderSorter());
                return urls.get(0).url;
            }
        } else
            return uri.toString();
    }

    protected long getSampleSize(Map<String, String> params) {
        long sampleSize = this.defaultSampleSize;
        String val = params.get(PARAM_SAMPLE_SIZE);
        if (val != null) {
            try {
                sampleSize = Long.parseLong(val);
            } catch (NumberFormatException e) {
                log.error("Unable to parse sampleSize of {}", val);
            }
        }
        return sampleSize;
    }

    protected long getMaxSampleDuration(Map<String, String> params) {
        long maxSampleDuration = this.defaultMaxSampleDuration;
        String val = params.get(PARAM_MAX_SAMPLE_DURATION);
        if (val != null) {
            try {
                maxSampleDuration = Long.parseLong(val);
            } catch (NumberFormatException e) {
                log.error("Unable to parse sampleSize of {}", val);
            }
        }
        return maxSampleDuration;
    }

    protected long getSampleTTL(Map<String, String> params) {
        long sampleTTL = this.defaultSampleTTL;
        String val = params.get(PARAM_SAMPLE_TTL);
        if (val != null) {
            try {
                sampleTTL = Long.parseLong(val);
            } catch (NumberFormatException e) {
                log.error("Unable to parse sampleTTL of {}", val);
            }
        }
        return sampleTTL;
    }

    protected boolean isInitialLoadOnly(Map<String, String> params) {
        String val = params.get(PARAM_PRELOAD_ONLY);
        return val != null && val.equalsIgnoreCase("true");
    }

    protected List<SyncUrl> getUrls(Map<String, String> params) {
        List<SyncUrl> urls = new ArrayList<SyncUrl>();
        for (String key : params.keySet()) {
            try {
                int order = Integer.parseInt(key);
                urls.add(new SyncUrl(params.get(key), order));
            } catch (NumberFormatException e) {
            }
        }
        return urls;
    }

    protected Map<String, String> getParameters(URI uri) {
        String query = uri.getQuery();
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<String, String>();
        for (String param : params) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                String name = pair[0];
                String value = pair[1];
                map.put(name, value);
            }
        }
        return map;
    }

    public void setDefaultSampleSize(long sampleSize) {
        this.defaultSampleSize = sampleSize;
    }

    public void setDefaultSampleTTL(long sampleTTL) {
        this.defaultSampleTTL = sampleTTL;
    }

    public void setDefaultMaxSampleDuration(long defaultMaxSampleDuration) {
        this.defaultMaxSampleDuration = defaultMaxSampleDuration;
    }

    static class SyncUrl {
        String url;
        int order;
        double kbps;

        public SyncUrl(String url, int order) {
            super();
            this.url = url;
            this.order = order;
        }
    }

    static class ListOrderSorter implements Comparator<SyncUrl> {
        public int compare(SyncUrl o1, SyncUrl o2) {
            int thisVal = o1.order;
            int anotherVal = o2.order;
            return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
        }
    }

    static class BestBandwidthSorter implements Comparator<SyncUrl> {
        public int compare(SyncUrl o1, SyncUrl o2) {
            double thisVal = o1.kbps;
            double anotherVal = o2.kbps;
            return (thisVal > anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
        }
    }
}
