package org.p2p;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

@Getter
public class PeerInfo implements Serializable {

    private final String peerId;
    private final String ip;
    private final int port;
    private final Set<String> availableFiles;
    private long lastSeen;

    public PeerInfo(String peerId, String ip, int port) {
        this.peerId = peerId;
        this.ip = ip;
        this.port = port;
        this.availableFiles = ConcurrentHashMap.newKeySet();
        this.lastSeen = System.currentTimeMillis();
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format(
            "Peer[%s:%s:%d] - Files: %s",
            peerId, ip, port, availableFiles
        );
    }
}
