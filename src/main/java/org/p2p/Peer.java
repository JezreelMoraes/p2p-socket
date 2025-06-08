package org.p2p;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.Getter;

class Peer extends Loggable {

    private static final int MIN_PORT_NUMBER = 5000;
    private static final int MAX_PORT_NUMBER = 6000;

    @Getter
    private final String id;
    private final String trackerIp;
    private final int trackerPort;
    private final Set<String> ownedFiles;
    private final Map<String, PeerConnection> connections;
    private final ScheduledExecutorService scheduler;

    private ServerSocket serverSocket;
    private int port;

    private final Map<String, Integer> uploadCounts;
    private final Map<String, Integer> downloadCounts;
    private final Set<String> chokedPeers;
    private final Set<String> interestedPeers;
    private String optimisticUnchokePeer;

    public static void main(String[] args) {
        new Peer("PAIR_1", "localhost", 4444).start();
    }

    public Peer(String id, String trackerIp, int trackerPort) {
        this(id, trackerIp, trackerPort, new HashSet<>());
    }

    public Peer(String id, String trackerIp, int trackerPort, Set<String> files) {
        this.id = id;
        this.trackerIp = trackerIp;
        this.trackerPort = trackerPort;
        this.connections = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(4);

        this.uploadCounts = new ConcurrentHashMap<>();
        this.downloadCounts = new ConcurrentHashMap<>();
        this.chokedPeers = ConcurrentHashMap.newKeySet();
        this.interestedPeers = ConcurrentHashMap.newKeySet();
        this.optimisticUnchokePeer = null;

        this.ownedFiles = files;
//        initializeRandomFiles();
    }

    private void initializeRandomFiles() {
        Random random = new Random();
        int numFiles = random.nextInt(5) + 2;

        for (int i = 0; i < numFiles; i++) {
            int fileNum = random.nextInt(10) + 1;
            ownedFiles.add(fileNum + ".txt");
        }

        logInfo("Peer " + id + " inicializado com arquivos: " + ownedFiles);
    }

    public void start() {
        try {
            findFreePort();

            this.serverSocket = new ServerSocket(this.port);
            String ip = serverSocket.getInetAddress().getHostAddress();
            logInfo("Peer " + id + " iniciado: " + ip + ":" + port);

            logInfo("Registrando no tracker");
            registerWithTracker();
            logInfo("Iniciando atividades periodicas");
            startPeriodicTasks();
            logInfo("Aceitando comunicação de pares");
            acceptConnections();
        } catch (Exception e) {
            logError("Erro ao iniciar Peer " + id + ": " + e);
        }
    }

    private void findFreePort() {
        for (int port = MIN_PORT_NUMBER; port <= MAX_PORT_NUMBER; port++) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                this.port = port;
                break;
            } catch (IOException ignored) {}
        }
    }

    private void registerWithTracker() {
        while (!this.serverSocket.isClosed()) {
            try (Socket socket = new Socket(trackerIp, trackerPort);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                logInfo("Tentando registrar no tracker");

                InetAddress localAddress = socket.getLocalAddress();
                String ip = localAddress.getHostAddress();

                Message message = new Message(Message.Type.REGISTER, id);
                message.addData("ip", ip);
                message.addData("port", port);

                out.writeObject(message);
                Message response = (Message) in.readObject();

                if ("success".equals(response.getData("status"))) {
                    logInfo("Peer " + id + " registrado com sucesso no tracker");
                    return;
                }

            } catch (Exception e) {
                logError("Erro ao registrar com tracker: " + e);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }

    }

    private void startPeriodicTasks() {
        scheduler.scheduleAtFixedRate(this::announceToTracker, 10, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::runTitForTat, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::optimisticUnchoke, 15, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::searchForMissingFiles, 5, 20, TimeUnit.SECONDS);
    }

    private void announceToTracker() {
        try (Socket socket = new Socket(trackerIp, trackerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            Message message = new Message(Message.Type.ANNOUNCE, id);
            message.addData("files", new ArrayList<>(ownedFiles));

            out.writeObject(message);
            in.readObject();
        } catch (Exception e) {
            logError("Erro no announce: " + e);
        }
    }

    private void runTitForTat() {
        List<String> topUploaders = uploadCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3) // Unchoke top 3 uploaders
            .map(Map.Entry::getKey)
            .toList();

        for (String peer : connections.keySet()) {
            if (!topUploaders.contains(peer) && !peer.equals(optimisticUnchokePeer)) {
                chokePeer(peer);
            }
        }

        for (String peer : topUploaders) {
            unchokePeer(peer);
        }

        if (optimisticUnchokePeer != null) {
            unchokePeer(optimisticUnchokePeer);
        }
    }

    private void optimisticUnchoke() {
        List<String> chokedList = new ArrayList<>(chokedPeers);
        if (!chokedList.isEmpty()) {
            Random random = new Random();
            optimisticUnchokePeer = chokedList.get(random.nextInt(chokedList.size()));
            unchokePeer(optimisticUnchokePeer);
            logInfo("Peer " + id + " - Optimistic unchoke: " + optimisticUnchokePeer);
        }
    }

    private void chokePeer(String targetPeerId) {
        chokedPeers.add(targetPeerId);
        PeerConnection connection = connections.get(targetPeerId);
        if (connection != null) {
            connection.sendMessage(new Message(Message.Type.CHOKE, id));
        }
    }

    private void unchokePeer(String targetPeerId) {
        chokedPeers.remove(targetPeerId);
        PeerConnection connection = connections.get(targetPeerId);
        if (connection != null) {
            connection.sendMessage(new Message(Message.Type.UNCHOKE, id));
        }
    }

    private void searchForMissingFiles() {
        Set<String> allFiles = Set.of(
            "1.txt", "2.txt", "3.txt", "4.txt", "5.txt",
            "6.txt", "7.txt", "8.txt", "9.txt", "10.txt"
        );

        Set<String> missingFiles = new HashSet<>(allFiles);
        missingFiles.removeAll(ownedFiles);

        if (!missingFiles.isEmpty()) {
            String targetFile = missingFiles.iterator().next();
            requestFileFromTracker(targetFile);
        }
    }

    private void requestFileFromTracker(String fileName) {
        try (Socket socket = new Socket(trackerIp, trackerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            Message message = new Message(Message.Type.REQUEST_PEERS, id);
            message.addData("fileName", fileName);

            out.writeObject(message);
            Message response = (Message) in.readObject();

            List<PeerInfo> peersWithFile = response.getData("peers");

            if (!peersWithFile.isEmpty()) {
                PeerInfo targetPeer = peersWithFile.get(0);
                if (!targetPeer.getPeerId().equals(id)) {
                    requestFileFromPeer(targetPeer, fileName);
                }
            }

        } catch (Exception e) {
            logError("Erro ao buscar peers para " + fileName + ": " + e);
        }
    }

    private void requestFileFromPeer(PeerInfo targetPeer, String fileName) {
        try (Socket socket = new Socket(targetPeer.getIp(), targetPeer.getPort());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            Message request = new Message(Message.Type.FILE_REQUEST, id);
            request.addData("fileName", fileName);

            out.writeObject(request);
            Message response = (Message) in.readObject();

            if (response.getType() == Message.Type.FILE_RESPONSE) {
                Boolean success = response.getData("success");
                if (Boolean.TRUE.equals(success)) {
                    ownedFiles.add(fileName);
                    downloadCounts.put(targetPeer.getPeerId(),
                        downloadCounts.getOrDefault(targetPeer.getPeerId(), 0) + 1);
                    logInfo("Peer " + id + " obteve arquivo " + fileName +
                        " de " + targetPeer.getPeerId());
                }
            }

        } catch (Exception e) {
            logError("Erro ao solicitar arquivo de peer " + targetPeer.getIp() + ":" + targetPeer.getPort() + " - " + e);
        }
    }

    private void acceptConnections() {
        scheduler.submit(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handlePeerConnection(clientSocket);
                } catch (IOException e) {
                    logError("Erro ao aceitar conexão: " + e);
                }
            }
        });
    }

    private void handlePeerConnection(Socket clientSocket) {
        scheduler.submit(() -> {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

                Message message = (Message) in.readObject();
                Message response = processPeerMessage(message);

                if (response != null) {
                    out.writeObject(response);
                }

            } catch (Exception e) {
                logError("Erro ao processar conexão de peer: " + e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logError("Erro ao fechar socket: " + e);
                }
            }
        });
    }

    private Message processPeerMessage(Message message) {
        switch (message.getType()) {
            case FILE_REQUEST -> {
                return handleFileRequest(message);
            }
            case CHOKE -> {
                logInfo("Peer " + id + " foi choked por " + message.getSenderId());
                return null;
            }
            case UNCHOKE -> {
                logInfo("Peer " + id + " foi unchoked por " + message.getSenderId());
                return null;
            }
            case INTERESTED -> {
                interestedPeers.add(message.getSenderId());
                return null;
            }
            case NOT_INTERESTED -> {
                interestedPeers.remove(message.getSenderId());
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private Message handleFileRequest(Message message) {
        String fileName = message.getData("fileName");
        String requesterId = message.getSenderId();

        Message response = new Message(Message.Type.FILE_RESPONSE, id);

        if (ownedFiles.contains(fileName) && !chokedPeers.contains(requesterId)) {
            uploadCounts.put(requesterId, uploadCounts.getOrDefault(requesterId, 0) + 1);
            response.addData("success", true);
            response.addData("fileName", fileName);

            logInfo("Peer " + id + " enviou arquivo " + fileName +
                " para " + requesterId);
        } else {
            response.addData("success", false);
            response.addData("reason", ownedFiles.contains(fileName) ? "choked" : "file not found");
        }

        return response;
    }

    @Override
    protected String buildInfo() {
        return String.format("Peer[%s:%d] ",
            this.id,
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
        logInfo("\n=== STATUS DO PEER " + id + " ===");
        logInfo("Arquivos possuídos: " + ownedFiles);
        logInfo("Conexões ativas: " + connections.size());
        logInfo("Peers choked: " + chokedPeers);
        logInfo("Peers interessados: " + interestedPeers);
        logInfo("Optimistic unchoke: " + optimisticUnchokePeer);
        logInfo("Upload counts: " + uploadCounts);
        logInfo("Download counts: " + downloadCounts);
        logInfo("===============================\n");
    }

}