package org.p2p;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import lombok.Getter;

class PeerConnection extends Loggable {

    public static final int PEER_CONNECTION_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;

    @Getter
    private String remotePeerId;

    public PeerConnection(PeerInfo peerInfo) throws IOException {
        this.remotePeerId = peerInfo.getPeerId();
        this.socket = new Socket(peerInfo.getIp(), peerInfo.getPort());
        this.socket.setSoTimeout(PEER_CONNECTION_TIMEOUT_MS);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public PeerConnection(Socket peerSocket) throws IOException {
        this.socket = peerSocket;
        this.socket.setSoTimeout(PEER_CONNECTION_TIMEOUT_MS);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public void sendMessage(Message message) {
        if (this.socket.isClosed()) return;

        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            logError("Erro ao enviar mensagem para " + remotePeerId + ": " + e);
            disconnect();
        }
    }

    public Message receiveMessage() throws IOException, ClassNotFoundException {
        if (this.socket.isClosed()) return null;

        try {
            Object obj = in.readObject();

            if (obj instanceof Message message) {
                return message;
            } else {
                System.err.println("Objeto recebido não é do tipo Message: " + obj.getClass());
                return null;
            }

        } catch (EOFException e) {
            logError("Conexão encerrada pelo peer " + remotePeerId);
            disconnect();
            return null;
        } catch (IOException | ClassNotFoundException e) {
            logError("Erro ao receber mensagem de " + remotePeerId + ": " + e);
            disconnect();
            throw e;
        }
    }

    public void disconnect() {
        try {
            if (socket == null) return;
            if (socket.isClosed()) return;

            socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e);
        }
    }

    @Override
    String buildInfo() {
        return "[PeerConnection] ";
    }
}