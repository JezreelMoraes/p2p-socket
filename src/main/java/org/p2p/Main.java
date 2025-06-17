package org.p2p;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import lombok.Getter;

@Getter
class Main {

    private static final String TRACKER_HOST = "localhost";
    private static final int TRACKER_PORT = 4444;

    private static Tracker tracker;
    private static List<Peer> peers;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== SISTEMA BITTORRENT ===\n");

        tracker = new Tracker(TRACKER_PORT);
        Thread trackerThread = new Thread(tracker::start);
        trackerThread.setDaemon(true);
        trackerThread.start();

        System.out.println("Sistema iniciado! Comandos disponíveis:");
        System.out.println("1 - Status do Tracker");
        System.out.println("2 - Status dos Peers");
        System.out.println("3 - Status de um Peer específico");
        System.out.println("0 - Sair\n");

        peers = new ArrayList<>();
        peers.add(new Peer(TRACKER_HOST, TRACKER_PORT));
        peers.add(new Peer(TRACKER_HOST, TRACKER_PORT));
        peers.add(new Peer(TRACKER_HOST, TRACKER_PORT));

        try {
            for (int i = 1; i <= 10; i++) {
                Peer peer = new Peer(TRACKER_HOST, TRACKER_PORT);
                peers.add(peer);
                Thread peerThread = new Thread(peer::start);
                peerThread.setDaemon(true);
                peerThread.start();

                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        while (true) {
            String command = scanner.nextLine().trim();

            switch (command) {
                case "1" -> tracker.printStatus();
                case "2" -> peers.forEach(Peer::printStatus);
                case "3" -> {
                    System.out.print("ID do Peer (PEER1, PEER2, PEER3): ");
                    String peerId = scanner.nextLine().trim();
                    peers.stream()
                        .filter(p -> p.getId().equals(peerId))
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
