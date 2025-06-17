package org.p2p;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
        this.scheduler = Executors.newScheduledThreadPool(3);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);

            logInfo("Tracker iniciado");

            logInfo("Iniciando atividades periodicas");
            startPeriodicTasks();

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

    private void startPeriodicTasks() {
        scheduler.scheduleAtFixedRate(this::removeIdlePeers, 10, 10, TimeUnit.SECONDS);
    }

    private void removeIdlePeers() {
        final long twentySecondsInMillis = 20_000;

        for (PeerInfo peerInfo : peers.values()) {
            long lastSeenTimeInMillis = System.currentTimeMillis() - peerInfo.getLastSeen();
            if (lastSeenTimeInMillis >= twentySecondsInMillis) {
                logInfo("Removendo par inativo: " + peerInfo.getPeerId());
                peers.remove(peerInfo.getPeerId());
            }
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
        if (Objects.requireNonNull(message.getType()) == Message.Type.ANNOUNCE) {
            return handleAnnounce(message);
        }

        logError("Tipo de mensagem não suportado: " + message.getType());
        return null;
    }

    private Message handleAnnounce(Message message) {
        PeerInfo peerInfo = registerPeerIfNecessary(message);

        List<String> availableFiles = message.getData(Message.DataType.FILES);
        peerInfo.getAvailableFiles().clear();
        peerInfo.getAvailableFiles().addAll(availableFiles);
        peerInfo.updateLastSeen();

        Message response = new Message(Message.Type.ANNOUNCE, TRACKER_ID);
        response.addData(Message.DataType.SUCCESS, true);
        response.addData(Message.DataType.FILES_PER_PEER, listPeers(peerInfo.getPeerId()));

        return response;
    }

    private PeerInfo registerPeerIfNecessary(Message message) {
        String peerId = message.getSenderId();
        PeerInfo peerInfo = peers.get(peerId);
        if (peerInfo != null) return peerInfo;

        String peerIp = message.getData(Message.DataType.IP);
        Integer peerPort = message.getData(Message.DataType.PORT);

        peerInfo = new PeerInfo(peerId, peerIp, peerPort);
        peers.put(peerId, peerInfo);

        logInfo("Peer registrado: " + peerInfo);
        return peerInfo;
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

    // Métodos para melhorar logs

    @Override
    protected String buildInfo() {
        try {
            return String.format("Tracker[%s:%d] ", InetAddress.getLocalHost().getHostAddress(), this.port);
        } catch (IOException e) {
            logError("Erro ao obter endereço IP do Tracker: " + e);
            return String.format("Tracker[%d] ", this.port);
        }
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