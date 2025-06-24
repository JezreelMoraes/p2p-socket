package org.p2p;

import java.util.HashSet;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

public class PeerMain {

    // Constantes configuráveis
    private static final String DEFAULT_TRACKER_HOST = "localhost";
    private static final int DEFAULT_TRACKER_PORT = 4444;

    private static final String DEFAULT_PEER_ID = "PEER_DEFAULT_";

    private static Peer peer;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        String trackerHost = DEFAULT_TRACKER_HOST;
        int trackerPort = DEFAULT_TRACKER_PORT;

        Random random = new Random();

        String peerId = DEFAULT_PEER_ID + random.nextInt(100000);
        Set<String> initialFiles = new HashSet<>();

        if (args.length >= 1) {
            trackerHost = args[0];
        }
        if (args.length >= 2) {
            try {
                trackerPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Porta inválida, usando porta padrão: " + DEFAULT_TRACKER_PORT);
            }
        }
        if (args.length >= 3) {
            peerId = args[2];
        }
        if (args.length >= 4) {
            String filesArg = args[3];
            if (!filesArg.isEmpty() && !filesArg.equals("none")) {
                String[] files = filesArg.split(",");
                for (String file : files) {
                    initialFiles.add(file.trim());
                }
            }
        }

        System.out.println("=== PEER BITTORRENT ===\n");
        System.out.println("Peer iniciado! Comandos disponíveis:");
        System.out.println("1 - Status do Peer");
        System.out.println("2 - Listar arquivos disponíveis");
        System.out.println("0 - Sair\n");

        System.out.println("Configurações do Peer:");
        System.out.println("- ID: " + peerId);
        System.out.println("- Tracker: " + trackerHost + ":" + trackerPort);
        System.out.println("- Arquivos iniciais: " + (initialFiles.isEmpty() ? "nenhum" : initialFiles) + "\n");

        try {
            peer = new Peer(trackerHost, trackerPort);
            Thread peerThread = new Thread(peer::start);
            peerThread.setDaemon(true);
            peerThread.start();

            Thread.sleep(500);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Erro ao inicializar peer: " + e.getMessage());
            return;
        } catch (Exception e) {
            System.err.println("Erro ao conectar com o tracker: " + e.getMessage());
            return;
        }

        while (true) {
            String command = scanner.nextLine().trim();

            switch (command) {
                case "1" -> peer.printStatus();
                case "2" -> System.out.println("\nArquivos do peer: " + peer.listOwnedFiles() + "\n\n");
                case "0" -> {
                    System.out.println("Encerrando Peer...");
                    shutdown();
                    return;
                }
                default -> System.out.println("Comando inválido");
            }
        }
    }

    private static void shutdown() {
        if (peer != null) {
            peer.stop();
        }
        scanner.close();
        System.out.println("Peer encerrado.");
    }
}