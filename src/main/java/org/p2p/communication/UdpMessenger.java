package org.p2p.communication;

import java.io.IOException;

public class UdpMessenger extends Messenger {

    public UdpMessenger(String host, int port) throws IOException {
        super(host, port);
    }

}
