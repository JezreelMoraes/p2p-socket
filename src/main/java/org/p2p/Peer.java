package org.p2p;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
    private static final String FILES_PATH = "./peerFiles/";

    @Getter
    private String id = "PEER_";
    private final String trackerIp;
    private final int trackerPort;

    private Map<String, PeerInfo> peers;
    private final Set<String> chokedPeers;
    private final Set<String> unchokedPeers;
    private String optimisticUnchokePeer;
    private final ScheduledExecutorService scheduler;

    private ServerSocket serverSocket;
    private String ip;
    private int port;

    private final Map<String, Integer> uploadFileToPeerCounts;
    private final Map<String, Integer> downloadFileFromPeerCounts;

    public Peer(String trackerIp, int trackerPort) {
        this.trackerIp = trackerIp;
        this.trackerPort = trackerPort;
        this.scheduler = Executors.newScheduledThreadPool(4);

        this.uploadFileToPeerCounts = new ConcurrentHashMap<>();
        this.downloadFileFromPeerCounts = new ConcurrentHashMap<>();
        this.chokedPeers = ConcurrentHashMap.newKeySet();
        this.unchokedPeers = ConcurrentHashMap.newKeySet();
        this.optimisticUnchokePeer = null;
    }

    public void start() {
        try {
            this.port = findFreePort();
            this.serverSocket = new ServerSocket(port);
            this.ip = InetAddress.getLocalHost().getHostAddress();
            this.id += ip + "_" + port;
            logInfo("Peer " + id + " iniciado");

            logInfo("Aceitando comunicação de pares");
            acceptConnections();

            logInfo("Registrando no tracker");
            registerWithTracker();

            logInfo("Iniciando atividades periodicas");
            startPeriodicTasks();
        } catch (Exception e) {
            logError("Erro ao iniciar Peer " + id + ": " + e);
        }
    }

    private int findFreePort() {
        for (int port = MIN_PORT_NUMBER; port <= MAX_PORT_NUMBER; port++) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException ignored) {}
        }

        throw new RuntimeException("Nenhum porta disponivel encontrada");
    }

    private void registerWithTracker() {
        while (!this.serverSocket.isClosed()) {
            try (Socket socket = new Socket(trackerIp, trackerPort);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                Message message = new Message(Message.Type.REGISTER, id);
                message.addData(Message.DataType.IP, ip);
                message.addData(Message.DataType.PORT, port);
                message.addData(Message.DataType.FILES, listOwnedFiles());

                out.writeObject(message);
                Message response = (Message) in.readObject();

                Boolean success = response.getData(Message.DataType.SUCCESS);
                if (!success) throw new RuntimeException("Sem retorno de sucesso");

                this.peers = response.getData(Message.DataType.FILES_PER_PEER);
                setPeersAsChocked();

                logInfo("Registrado com sucesso no tracker");
                return;
            } catch (Exception e) {
                logError("Erro ao registrar com tracker: " + e);
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void setPeersAsChocked() {
        for (String peerId : this.peers.keySet()) {
            chokePeer(peerId);
        }
    }

    private void startPeriodicTasks() {
        scheduler.scheduleAtFixedRate(this::announceToTracker, 5, 3, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::runTitForTat, 5, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::optimisticUnchoke, 30, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::requestRarestFile, 10, 10, TimeUnit.SECONDS);
    }

    private void announceToTracker() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);

            Message message = new Message(Message.Type.ANNOUNCE, id);
            message.addData(Message.DataType.FILES, listOwnedFiles());

            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(byteOut);
            out.writeObject(message);
            byte[] sendData = byteOut.toByteArray();

            InetAddress address = InetAddress.getByName(trackerIp);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, trackerPort);
            socket.send(sendPacket);

            byte[] receiveBuffer = new byte[65535];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);

            ByteArrayInputStream byteIn = new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength());
            ObjectInputStream in = new ObjectInputStream(byteIn);
            Message response = (Message) in.readObject();

            Boolean success = response.getData(Message.DataType.SUCCESS);
            if (!success) throw new RuntimeException("Sem retorno de sucesso");

            this.peers = response.getData(Message.DataType.FILES_PER_PEER);
        } catch (SocketTimeoutException e) {
            logError("Timeout ao aguardar resposta do tracker.");
        } catch (Exception e) {
            logError("Erro no announce via UDP: " + e);
        }
    }

    private void runTitForTat() {
        Map<String, Integer> uploaders = new HashMap<>();
        for (String peerId : unchokedPeers) {
            uploaders.put(peerId, downloadFileFromPeerCounts.getOrDefault(peerId, 0));
        }

        uploaders.remove(optimisticUnchokePeer);

        List<String> topUploaders = uploaders.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .toList();

        for (String peerId : unchokedPeers) {
            if (!topUploaders.contains(peerId) && !peerId.equals(optimisticUnchokePeer)) {
                chokePeer(peerId);
            }
        }

        for (String peerId : topUploaders) {
            unchokePeer(peerId);
        }
    }

    private void optimisticUnchoke() {
        List<String> chokedList = new ArrayList<>(chokedPeers);
        if (chokedList.isEmpty()) return;

        Random random = new Random();
        optimisticUnchokePeer = chokedList.get(random.nextInt(chokedList.size()));
        unchokePeer(optimisticUnchokePeer);

        logInfo("Optimistic unchoked: " + optimisticUnchokePeer);
    }

    private void requestRarestFile() {
        List<String> unchokedList = new ArrayList<>(unchokedPeers);
        if (unchokedList.isEmpty()) return;

        Map<String, Set<PeerInfo>> fileUnchokedPeersMap = new HashMap<>();
        Map<String, Set<String>> unchokedPeerFilesMap = new HashMap<>();
        Map<String, Integer> fileCounts = new HashMap<>();

        for (String peerId : unchokedList) {
            PeerInfo peerInfo = peers.get(peerId);
            if (peerInfo == null) continue;

            Set<String> files = peerInfo.getAvailableFiles();
            unchokedPeerFilesMap.putIfAbsent(peerId, files);

            for (String file : files) {
                fileUnchokedPeersMap.computeIfAbsent(file, k -> new HashSet<>()).add(peerInfo);
                fileCounts.put(file, fileCounts.getOrDefault(file, 0) + 1);
            }
        }

        List<String> rarestFilesSort = fileCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByValue()) // arquivos mais raros primeiro
            .map(Map.Entry::getKey)
            .toList();

        Set<String> currentOwnedFiles = new HashSet<>(listOwnedFiles());
        Set<String> peerAlreadyRequested = new HashSet<>();
        Set<String> filesAlreadyRequested = new HashSet<>();

        for (String file : rarestFilesSort) {
            if (currentOwnedFiles.contains(file)) continue;
            if (filesAlreadyRequested.contains(file)) continue;

            Set<PeerInfo> peers = fileUnchokedPeersMap.get(file);
            if (peers == null || peers.isEmpty()) continue;

            PeerInfo bestChoicePeer = null;
            int minRemainingFilesCount = Integer.MAX_VALUE;

            for (PeerInfo peer : peers) {
                String peerId = peer.getPeerId();
                if (peerAlreadyRequested.contains(peerId)) continue;

                int remainingFilesCount = unchokedPeerFilesMap.get(peerId).size();
                if (remainingFilesCount < minRemainingFilesCount) {
                    minRemainingFilesCount = remainingFilesCount;
                    bestChoicePeer = peer;
                }
            }

            if (bestChoicePeer != null) {
                requestFileFromPeer(bestChoicePeer, file);
                peerAlreadyRequested.add(bestChoicePeer.getPeerId());
                filesAlreadyRequested.add(file);
            }
        }
    }

    private void chokePeer(String targetPeerId) {
        try {
            PeerConnection connection = new PeerConnection(peers.get(targetPeerId));
            connection.sendMessage(new Message(Message.Type.CHOKE, id));
        } catch (IOException e) {
            logError("Erro ao enviar mensagem de choked para par " + targetPeerId + " " + e);
        }

        chokedPeers.add(targetPeerId);
        unchokedPeers.remove(targetPeerId);
    }

    private void unchokePeer(String targetPeerId) {
        try {
            PeerConnection connection = new PeerConnection(peers.get(targetPeerId));
            connection.sendMessage(new Message(Message.Type.UNCHOKE, id));

            chokedPeers.remove(targetPeerId);
            unchokedPeers.add(targetPeerId);
        } catch (IOException e) {
            logError("Erro ao enviar mensagem de unchoked para par " + targetPeerId + " " + e);
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

            downloadFileFromPeerCounts.put(
                targetPeer.getPeerId(),
                downloadFileFromPeerCounts.getOrDefault(targetPeer.getPeerId(), 0) + 1
            );

            logInfo("< Obteve arquivo " + fileName + " de " + targetPeer.getPeerId());
        } catch (Exception e) {
            logError("Erro ao solicitar arquivo de peer " + targetPeer.getPeerId() + " - " + e);
        }
    }

    private void acceptConnections() {
        scheduler.submit(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    PeerConnection peerConnection = new PeerConnection(serverSocket.accept());
                    handlePeerConnection(peerConnection);
                } catch (IOException e) {
                    logError("Erro ao aceitar conexão: " + e);
                }
            }
        });
    }

    private void handlePeerConnection(PeerConnection peerConnection) {
        scheduler.submit(() -> {
            try {
                Message message = peerConnection.receiveMessage();
                Message response = processPeerMessage(message);

                if (response != null) {
                    peerConnection.sendMessage(response);
                }
            } catch (Exception e) {
                logError("Erro ao processar conexão de peer: " + peerConnection.getRemotePeerId() +  " - " + e);
                e.printStackTrace();
            } finally {
                peerConnection.disconnect();
            }
        });
    }

    private Message processPeerMessage(Message message) {
        if (message == null) {
            return null;
        }

        switch (message.getType()) {
            case FILE_REQUEST -> {
                return handleFileRequest(message);
            }
            case CHOKE -> {
                logInfo("Choked por " + message.getSenderId());
                unchokedPeers.remove(message.getSenderId());
                chokedPeers.add(message.getSenderId());
                return null;
            }
            case UNCHOKE -> {
                logInfo("Unchoked por " + message.getSenderId());
                chokedPeers.remove(message.getSenderId());
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
        String requesterPeerId = message.getSenderId();

        if (!listOwnedFiles().contains(fileName)) {
            return buildErrorFileResponse(fileName);
        }

        if (chokedPeers.contains(requesterPeerId)) {
            return buildErrorFileResponse(fileName);
        }

        try {
            byte[] fileData = FileUtils.readFileAsBytes(buildFilepath(fileName));

            Message response = new Message(Message.Type.FILE_RESPONSE, this.id);
            response.addData(Message.DataType.SUCCESS, true);
            response.addData(Message.DataType.FILE_NAME, fileName);
            response.addData(Message.DataType.FILE_DATA, fileData);

            logInfo("> Enviou arquivo " + fileName + " para " + requesterPeerId);
            uploadFileToPeerCounts.put(requesterPeerId, uploadFileToPeerCounts.getOrDefault(requesterPeerId, 0) + 1);

            return response;
        } catch (IOException e) {
            logError("Erro ao enviar arquivo " + fileName + " para " + requesterPeerId);
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

    // Métodos para melhorar logs

    @Override
    protected String buildInfo() {
        return String.format("%s[%s] ",
                this.id,
                new Date()
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
        System.out.println("\n=== STATUS DO PEER " + id + " ===");
        System.out.println("Arquivos possuídos: " + listOwnedFiles());
        System.out.println("Peers choked: " + chokedPeers);
        System.out.println("Peers unchoked: " + unchokedPeers);
        System.out.println("Optimistic unchoke: " + optimisticUnchokePeer);
        System.out.println("Upload counts: " + uploadFileToPeerCounts);
        System.out.println("Download counts: " + downloadFileFromPeerCounts);
        System.out.println("===============================\n");
    }

    public List<String> listOwnedFiles() {
        try {
            return FileUtils.listFilesInDirectory(buildFilepath(""));
        } catch (IOException e) {
            logError("Erro ao listar arquivos");
            return new ArrayList<>();
        }
    }

}