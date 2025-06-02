package org.p2p;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class BitTorrentSystem {

    private static Tracker tracker;
    private static List<Peer> peers;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== SISTEMA BITTORRENT ===\n");

        tracker = new Tracker(8080);
        Thread trackerThread = new Thread(tracker::start);
        trackerThread.setDaemon(true);
        trackerThread.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        peers = new ArrayList<>();
        peers.add(new Peer("PEER1", "localhost", 8081, "localhost", 8080));
        peers.add(new Peer("PEER2", "localhost", 8082, "localhost", 8080));
        peers.add(new Peer("PEER3", "localhost", 8083, "localhost", 8080));

        for (Peer peer : peers) {
            Thread peerThread = new Thread(peer::start);
            peerThread.setDaemon(true);
            peerThread.start();
        }

        System.out.println("Sistema iniciado! Comandos disponíveis:");
        System.out.println("1 - Status do Tracker");
        System.out.println("2 - Status dos Peers");
        System.out.println("3 - Status de um Peer específico");
        System.out.println("0 - Sair");

        while (true) {
            System.out.print("\nComando: ");
            String command = scanner.nextLine().trim();

            switch (command) {
                case "1" -> tracker.printStatus();
                case "2" -> peers.forEach(Peer::printStatus);
                case "3" -> {
                    System.out.print("ID do Peer (PEER1, PEER2, PEER3): ");
                    String peerId = scanner.nextLine().trim();
                    peers.stream()
                        .filter(p -> p.getPeerId().equals(peerId))
                        .findFirst()
                        .ifPresentOrElse(Peer::printStatus,
                            () -> System.out.println("Peer não encontrado"));
                }
                case "0" -> {
                    System.out.println("Encerrando sistema...");
                    shutdown();
                    return;
                }

                default -> System.out.println("Comando inválido");
            }
        }
    }

    private static void shutdown() {
        if (tracker != null) {
            tracker.stop();
        }

        if (peers != null) {
            peers.forEach(Peer::stop);
        }

        scanner.close();
    }
}