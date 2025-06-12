package org.p2p;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

class PeerConnection {

    public static final int PEER_CONNECTION_TIMEOUT_MS = 1000;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final AtomicBoolean connected;

    private String remotePeerId;

    public PeerConnection(PeerInfo peerInfo) throws IOException {
        try (Socket peerSocket = new Socket(peerInfo.getIp(), peerInfo.getPort())) {
            this.remotePeerId = peerInfo.getPeerId();
            this.socket = peerSocket;
            this.socket.setSoTimeout(PEER_CONNECTION_TIMEOUT_MS);
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.in = new ObjectInputStream(socket.getInputStream());
            this.connected = new AtomicBoolean(true);
        }
    }

    public PeerConnection(Socket peerSocket) throws IOException {
        this.socket = peerSocket;
        this.socket.setSoTimeout(PEER_CONNECTION_TIMEOUT_MS);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
        this.connected = new AtomicBoolean(true);
    }

    public void sendMessage(Message message) {
        if (!connected.get()) return;

        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem para " + remotePeerId + ": " + e);
            disconnect();
        }
    }

    public Message receiveMessage() throws IOException, ClassNotFoundException {
        if (!connected.get()) return null;
        return (Message) in.readObject();
    }

    public void disconnect() {
        connected.set(false);

        try {
            if (socket == null) return;
            if (socket.isClosed()) return;

            socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e);
        }
    }

    public boolean isConnected() {
        return connected.get() && !socket.isClosed();
    }

}