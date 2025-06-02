package org.p2p;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Getter;

class PeerConnection {

    @Getter
    private final String remotePeerId;
    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final AtomicBoolean connected;

    public PeerConnection(String remotePeerId, Socket socket) throws IOException {
        this.remotePeerId = remotePeerId;
        this.socket = socket;
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
            System.err.println("Erro ao enviar mensagem para " + remotePeerId + ": " + e.getMessage());
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
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected.get() && !socket.isClosed();
    }

}