package org.p2p;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Tracker extends Loggable {

    private static final String TRACKER_ID = "tracker";

    private final int port;
    private final Map<String, PeerInfo> peers;
    private final ScheduledExecutorService scheduler;
    private DatagramSocket datagramSocket;

    public Tracker(int port) {
        this.port = port;
        this.peers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(3);
    }

    public void start() {
        try {
            this.datagramSocket = new DatagramSocket(port);
            logInfo("Tracker UDP iniciado no ip: " + InetAddress.getLocalHost().getHostAddress() + " porta: " + port);

            byte[] receiveBuffer = new byte[65535];

            logInfo("Iniciando atividades periodicas");
            startPeriodicTasks();

            while (!datagramSocket.isClosed()) {
                try {
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    datagramSocket.receive(receivePacket);

                    byte[] data = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
                    InetAddress clientAddress = receivePacket.getAddress();
                    int clientPort = receivePacket.getPort();

                    scheduler.submit(() -> handleClientUDP(datagramSocket, data, clientAddress, clientPort));

                } catch (IOException e) {
                    logError("Erro ao receber pacote UDP: " + e);
                }
            }
        } catch (IOException e) {
            logError("Erro ao iniciar Tracker UDP: " + e);
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

    private void handleClientUDP(DatagramSocket socket, byte[] data, InetAddress address, int port) {
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(data);
             ObjectInputStream in = new ObjectInputStream(byteIn)) {

            Message message = (Message) in.readObject();
            Message response = processMessage(message);

            if (response != null) {
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(byteOut);
                out.writeObject(response);
                out.flush();
                byte[] responseData = byteOut.toByteArray();

                DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, address, port);
                socket.send(responsePacket);
            }
        } catch (Exception e) {
            logError("Erro ao processar cliente UDP: " + e);
        }
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

    @Override
    protected String buildInfo() {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        return String.format("Tracker [%s]: ", timestamp);
    }


    public void stop() {
        if (datagramSocket != null && !datagramSocket.isClosed()) {
            datagramSocket.close();
        }

        scheduler.shutdown();
    }

    public void printStatus() {
        System.out.println("\n=== STATUS DO TRACKER ===");

        List<Map> peersInfo = new ArrayList<>();
        for (PeerInfo peer : peers.values()) {
            Map<String, Object> peerData = new HashMap<>();
            peerData.put("PEER_IP", peer.getIp());
            peerData.put("PEER_PORT", peer.getPort());
            peerData.put("FILES_SIZE", peer.getAvailableFiles().size());
            peerData.put("LAST_SEEN_IN_SECONDS", (long) (System.currentTimeMillis() - peer.getLastSeen()) / 1000);

            peersInfo.add(peerData);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        System.out.println(gson.toJson(peersInfo));

        System.out.println("========================\n");
    }

}