package com.jumpmind.symmetric.cache;

import java.util.Properties;

public class UdpCachePeerServerDiscovery extends CachePeerServerDiscovery {
    @Override
    public void enrichJcsProperties(Properties jcsProperties, String lateralAuxAttributesPrefix) {
        jcsProperties.setProperty(lateralAuxAttributesPrefix + ".UdpDiscoveryEnabled", "true");
    }

    @Override
    public synchronized void start(DiscoveryContext context) {
        super.start(context);
        getUdpDiscoveryService();
    }
}
