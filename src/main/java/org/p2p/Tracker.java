package org.p2p;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Tracker extends Loggable {

    private final int port;
    private final Map<String, PeerInfo> peers;
    private final Map<String, FileInfo> files;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private ServerSocket serverSocket;

    public static void main(String[] args) {
        new Tracker(4444).start();
    }

    public Tracker(int port) {
        this.port = port;
        this.peers = new ConcurrentHashMap<>();
        this.files = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.running = new AtomicBoolean(false);

        initializeFiles();
    }

    private void initializeFiles() {
        for (int i = 1; i <= 10; i++) {
            String fileName = i + ".txt";
            files.put(fileName, new FileInfo(fileName, 1024 * i, "checksum" + i));
        }
    }

    public void start() {
        if (running.get()) return;

        try {
            serverSocket = new ServerSocket(port);
            running.set(true);

            String ip = serverSocket.getInetAddress().getHostAddress();
            logInfo("Tracker iniciado: " + ip + ":" + port);
            startPeerCleanup();

            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                } catch (IOException e) {
                    if (running.get()) {
                        logError("Erro ao aceitar conexão: " + e);
                    }
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
            case REQUEST_PEERS -> {
                return handleRequestPeers(message);
            }
            default -> {
                logInfo("Tipo de mensagem não suportado: " + message.getType());
                return null;
            }
        }
    }

    private Message handleRegister(Message message) {
        String peerId = message.getSenderId();
        String ip = message.getData("ip");
        Integer port = message.getData("port");

        PeerInfo peer = new PeerInfo(peerId, ip, port);
        peers.put(peerId, peer);

        logInfo("Peer registrado: " + peer);

        Message response = new Message(Message.Type.REGISTER, "tracker");
        response.addData("status", "success");
        response.addData("availableFiles", new ArrayList<>(files.keySet()));
        return response;
    }

    private Message handleAnnounce(Message message) {
        String peerId = message.getSenderId();
        List<String> availableFiles = message.getData("files");

        PeerInfo peer = peers.get(peerId);
        if (peer != null) {
            peer.updateLastSeen();
            peer.getAvailableFiles().clear();
            peer.getAvailableFiles().addAll(availableFiles);

            for (String fileName : availableFiles) {
                FileInfo fileInfo = files.get(fileName);
                if (fileInfo != null) {
                    fileInfo.addSeeder(peerId);
                }
            }

            logInfo("Announce recebido de " + peerId + ": " + availableFiles);
        }

        Message response = new Message(Message.Type.ANNOUNCE, "tracker");
        response.addData("status", "success");
        return response;
    }

    private Message handleRequestPeers(Message message) {
        String fileName = message.getData("fileName");
        List<PeerInfo> peersWithFile = new ArrayList<>();

        for (PeerInfo peer : peers.values()) {
            if (peer.getAvailableFiles().contains(fileName)) {
                peersWithFile.add(peer);
            }
        }

        Message response = new Message(Message.Type.PEER_LIST, "tracker");
        response.addData("fileName", fileName);
        response.addData("peers", peersWithFile);

        logInfo("Lista de peers para " + fileName + ": " + peersWithFile.size() + " peers");

        return response;
    }

    private void startPeerCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            List<String> inactivePeers = new ArrayList<>();

            for (Map.Entry<String, PeerInfo> entry : peers.entrySet()) {
                if (currentTime - entry.getValue().getLastSeen() > 60000) { // 1 minuto
                    inactivePeers.add(entry.getKey());
                }
            }

            for (String peerId : inactivePeers) {
                peers.remove(peerId);
                for (FileInfo fileInfo : files.values()) {
                    fileInfo.removeSeeder(peerId);
                }

                logInfo("Peer inativo removido: " + peerId);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    protected String buildInfo() {
        return String.format("Tracker[%d] ",
            this.port
        );
    }

    public void stop() {
        running.set(false);

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
        logInfo("\n=== STATUS DO TRACKER ===");
        logInfo("Peers conectados: " + peers.size());

        for (PeerInfo peer : peers.values()) {
            logInfo("  " + peer);
        }

        logInfo("Arquivos disponíveis: " + files.size());
        for (FileInfo file : files.values()) {
            logInfo("  " + file.getFileName() + " - Seeders: " + file.getSeeders().size());
        }

        logInfo("========================\n");
    }

}