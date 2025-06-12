package org.p2p;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    private static final String FILES_PATH = "./peerFiles/";

    @Getter
    private final String id;
    private final String trackerIp;
    private final int trackerPort;

    private Map<String, PeerInfo> peerList;
    private final Map<String, PeerConnection> connections;
    private final Set<String> chokedPeers;
    private final Set<String> unchokedPeers;
    private String optimisticUnchokePeer;
    private final ScheduledExecutorService scheduler;

    private ServerSocket serverSocket;
    private int port;

    private final Map<String, Integer> uploadCounts;
    private final Map<String, Integer> downloadCounts;


    public Peer(String id, String trackerIp, int trackerPort) {
        this.id = id;
        this.trackerIp = trackerIp;
        this.trackerPort = trackerPort;
        this.connections = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(4);

        this.uploadCounts = new ConcurrentHashMap<>();
        this.downloadCounts = new ConcurrentHashMap<>();
        this.chokedPeers = ConcurrentHashMap.newKeySet();
        this.unchokedPeers = ConcurrentHashMap.newKeySet();
        this.optimisticUnchokePeer = null;
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
        logInfo("Arquivos possuídos: " + listOwnedFiles());
        logInfo("Conexões ativas: " + connections.size());
        logInfo("Peers choked: " + chokedPeers);
        logInfo("Optimistic unchoke: " + optimisticUnchokePeer);
        logInfo("Upload counts: " + uploadCounts);
        logInfo("Download counts: " + downloadCounts);
        logInfo("===============================\n");
    }

    public List<String> listOwnedFiles() {
        try {
            return FileUtils.listFilesInDirectory(buildFilepath(""));
        } catch (IOException e) {
            logError("Erro ao listar arquivos");
            return new ArrayList<>();
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

                InetAddress localAddress = socket.getLocalAddress();
                String ip = localAddress.getHostAddress();

                Message message = new Message(Message.Type.REGISTER, id);
                message.addData(Message.DataType.IP, ip);
                message.addData(Message.DataType.PORT, port);

                out.writeObject(message);
                Message response = (Message) in.readObject();

                Boolean success = response.getData(Message.DataType.SUCCESS);
                if (!success) throw new RuntimeException("Sem retorno de sucesso");

                this.peerList = response.getData(Message.DataType.FILES_PER_PEER);

                logInfo("Peer " + id + " registrado com sucesso no tracker");
                return;
            } catch (Exception e) {
                logError("Erro ao registrar com tracker: " + e);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void startPeriodicTasks() {
        scheduler.scheduleAtFixedRate(this::announceToTracker, 5, 3, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::runTitForTat, 5, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::optimisticUnchoke, 30, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::requestRarestFile, 10, 10, TimeUnit.SECONDS);
    }

    private void announceToTracker() {
        try (Socket socket = new Socket(trackerIp, trackerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            Message message = new Message(Message.Type.ANNOUNCE, id);
            message.addData(Message.DataType.FILES, listOwnedFiles());

            out.writeObject(message);
            Message response = (Message) in.readObject();

            Boolean success = response.getData(Message.DataType.SUCCESS);
            if (!success) throw new RuntimeException("Sem retorno de sucesso");

            this.peerList = response.getData(Message.DataType.FILES_PER_PEER);

        } catch (Exception e) {
            logError("Erro no announce: " + e);
        }
    }

    private void runTitForTat() {
        List<String> topUploaders = uploadCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .toList();

        for (String peerId : connections.keySet()) {
            if (!topUploaders.contains(peerId) && !peerId.equals(optimisticUnchokePeer)) {
                chokePeer(peerId);
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

    private void requestRarestFile() {
        List<String> unchokedList = new ArrayList<>(unchokedPeers);
        if (unchokedList.isEmpty()) {
            return;
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

    private void requestFileFromPeer(PeerInfo targetPeer, String fileName) {
        try (Socket socket = new Socket(targetPeer.getIp(), targetPeer.getPort());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            Message request = new Message(Message.Type.FILE_REQUEST, id);
            request.addData(Message.DataType.FILE_NAME, fileName);

            out.writeObject(request);
            Message response = (Message) in.readObject();

            if (response.getType() != Message.Type.FILE_RESPONSE) {
                logInfo("Peer " + targetPeer.getPeerId() + " falhou em enviar arquivo " + fileName);
                return;
            }

            Boolean success = response.getData(Message.DataType.SUCCESS);
            if (!success) return;

            FileUtils.createFileFromBytes(buildFilepath(fileName), response.getData(Message.DataType.FILE_DATA));

            downloadCounts.put(
                targetPeer.getPeerId(),
                downloadCounts.getOrDefault(targetPeer.getPeerId(), 0) + 1
            );

            logInfo("Peer " + id + " obteve arquivo " + fileName + " de " + targetPeer.getPeerId());
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
                logInfo("Choked por " + message.getSenderId());
                unchokedPeers.remove(message.getSenderId());
                return null;
            }
            case UNCHOKE -> {
                logInfo("Unchoked por " + message.getSenderId());
                unchokedPeers.add(message.getSenderId());
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private Message handleFileRequest(Message message) {
        String fileName = message.getData(Message.DataType.FILE_NAME);
        String requesterId = message.getSenderId();

        if (!listOwnedFiles().contains(fileName)) {
            return buildErrorFileResponse(fileName);
        }

        if (chokedPeers.contains(requesterId)) {
            return buildErrorFileResponse(fileName);
        }

        try {
            byte[] fileData = FileUtils.readFileAsBytes(buildFilepath(fileName));

            Message response = new Message(Message.Type.FILE_RESPONSE, this.id);
            response.addData(Message.DataType.SUCCESS, true);
            response.addData(Message.DataType.FILE_NAME, fileName);
            response.addData(Message.DataType.FILE_DATA, fileData);

            logInfo("Peer " + id + " enviou arquivo " + fileName + " para " + requesterId);
            uploadCounts.put(requesterId, uploadCounts.getOrDefault(requesterId, 0) + 1);

            return response;
        } catch (IOException e) {
            logError("Erro ao enviar arquivo " + fileName + " para " + requesterId);
            return buildErrorFileResponse(fileName);
        }
    }

    private Message buildErrorFileResponse(String fileName) {
        Message response = new Message(Message.Type.FILE_RESPONSE, this.id);
        response.addData(Message.DataType.SUCCESS, false);
        response.addData(Message.DataType.REASON, listOwnedFiles().contains(fileName) ? "choked" : "file not found");

        return response;
    }

    private String buildFilepath(String filename) {
        return Paths.get(FILES_PATH, "/" + this.id, filename).toString();
    }

}