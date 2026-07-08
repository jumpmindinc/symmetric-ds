package com.jumpmind.symmetric.cache;

import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class MdnsCachePeerServerDiscovery extends CachePeerServerDiscovery {
    private static final String SERVICE_TYPE = "_symds-cluster._tcp.local.";
    private volatile JmDNS jmdns;

    @Override
    public synchronized void start(DiscoveryContext context) {
        super.start(context);
        try {
            jmdns = JmDNS.create(InetAddress.getLocalHost());
            jmdns.registerService(ServiceInfo.create(SERVICE_TYPE, context.serverId(), context.port(), ""));
            jmdns.addServiceListener(SERVICE_TYPE, new ServiceListener() {
                public void serviceAdded(ServiceEvent e) {
                    jmdns.requestServiceInfo(e.getType(), e.getName());
                }

                public void serviceRemoved(ServiceEvent e) {
                    retractPeer(e.getName());
                }

                public void serviceResolved(ServiceEvent e) {
                    if (!e.getName().equals(context.serverId()) && e.getInfo().getInetAddresses().length > 0) {
                        announcePeer(e.getName(), e.getInfo().getInetAddresses()[0].getHostAddress());
                    }
                }
            });
        } catch (Exception ex) {
            log.warn("Unable to start mDNS discovery. serverId={}", context.serverId(), ex);
        }
    }

    @Override
    public synchronized void stop() {
        if (jmdns != null) {
            try {
                jmdns.unregisterAllServices();
                jmdns.close();
            } catch (Exception ex) {
                log.warn("Error closing jmDNS", ex);
            }
            jmdns = null;
        }
        super.stop();
    }
}
