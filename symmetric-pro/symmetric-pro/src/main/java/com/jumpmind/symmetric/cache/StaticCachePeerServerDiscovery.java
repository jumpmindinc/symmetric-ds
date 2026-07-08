package com.jumpmind.symmetric.cache;

public class StaticCachePeerServerDiscovery extends CachePeerServerDiscovery {
    static final String STATIC_SERVERS_PROPERTY = "cluster.cache.discovery.static.servers";

    @Override
    public synchronized void start(DiscoveryContext context) {
        super.start(context);
        String servers = System.getProperty(STATIC_SERVERS_PROPERTY, "");
        for (String entry : servers.split(",")) {
            String host = entry.trim();
            if (!host.isEmpty()) {
                String address = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
                announcePeer(host, address);
            }
        }
    }
}
