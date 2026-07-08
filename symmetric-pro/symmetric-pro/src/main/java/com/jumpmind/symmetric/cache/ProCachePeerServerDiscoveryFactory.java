package com.jumpmind.symmetric.cache;

public class ProCachePeerServerDiscoveryFactory extends CachePeerServerDiscoveryFactory {
    @Override
    public ICachePeerServerDiscovery create(String mode) {
        if (mode != null) {
            switch (mode.toLowerCase()) {
                case "udp":
                    return new UdpCachePeerServerDiscovery();
                case "mdns":
                    return new MdnsCachePeerServerDiscovery();
                case "static":
                    return new StaticCachePeerServerDiscovery();
                default:
                    break;
            }
        }
        return super.create(mode);
    }
}
