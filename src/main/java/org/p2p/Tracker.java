package org.p2p;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class Tracker extends Loggable {

    private static final String TRACKER_ID = "tracker";

    private final int port;
    private final Map<String, PeerInfo> peers;
    private final ScheduledExecutorService scheduler;
    private ServerSocket serverSocket;

    public Tracker(int port) {
        this.port = port;
        this.peers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);

            String ip = serverSocket.getInetAddress().getHostAddress();
            logInfo("Tracker iniciado: " + ip + ":" + port);

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                } catch (IOException e) {
                    logError("Erro ao aceitar conexão: " + e);
                }
            }
        } catch (IOException e) {
            logError("Erro ao iniciar Tracker: " + e);
        }
    }

    private void handleClient(Socket clientSocket) {
        scheduler.submit(() -> {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

                Message message = (Message) in.readObject();
                Message response = processMessage(message);

                if (response != null) {
                    out.writeObject(response);
                }
            } catch (Exception e) {
                logError("Erro ao processar cliente: " + e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logError("Erro ao fechar socket: " + e);
                }
            }
        });
    }

    private Message processMessage(Message message) {
        switch (message.getType()) {
            case REGISTER -> {
                return handleRegister(message);
            }
            case ANNOUNCE -> {
                return handleAnnounce(message);
            }
            default -> {
                logError("Tipo de mensagem não suportado: " + message.getType());
                return null;
            }
        }
    }

    private Message handleRegister(Message message) {
        String peerId = message.getSenderId();
        String ip = message.getData(Message.DataType.IP);
        Integer port = message.getData(Message.DataType.PORT);
        List<String> availableFiles = message.getData(Message.DataType.FILES);

        PeerInfo peer = new PeerInfo(peerId, ip, port);
        peer.getAvailableFiles().addAll(availableFiles);

        peers.put(peerId, peer);

        logInfo("Peer registrado: " + peer);

        Message response = new Message(Message.Type.REGISTER, TRACKER_ID);
        response.addData(Message.DataType.SUCCESS, true);
        response.addData(Message.DataType.FILES_PER_PEER, listPeers(peerId));

        return response;
    }

    private Message handleAnnounce(Message message) {
        String peerId = message.getSenderId();
        List<String> availableFiles = message.getData(Message.DataType.FILES);

        PeerInfo peer = peers.get(peerId);
        if (peer != null) {
            peer.updateLastSeen();
            peer.getAvailableFiles().clear();
            peer.getAvailableFiles().addAll(availableFiles);
        }

        Message response = new Message(Message.Type.ANNOUNCE, TRACKER_ID);
        response.addData(Message.DataType.SUCCESS, true);
        response.addData(Message.DataType.FILES_PER_PEER, listPeers(peerId));

        return response;
    }

    private Map<String, PeerInfo> listPeers(String peerId) {
        return peers.entrySet()
            .stream()
            .filter(entry -> !entry.getValue().getPeerId().equals(peerId))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
    }

    @Override
    protected String buildInfo() {
        return String.format("Tracker[%d] ",
            this.port
        );
    }

    public void stop() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logError("Erro ao fechar servidor: " + e);
            }
        }

        scheduler.shutdown();
    }

    public void printStatus() {
        System.out.println("\n=== STATUS DO TRACKER ===");
        System.out.println("Peers conectados: " + peers.size());

        for (PeerInfo peer : peers.values()) {
            System.out.println("  " + peer);
        }

        System.out.println("========================\n");
    }

}