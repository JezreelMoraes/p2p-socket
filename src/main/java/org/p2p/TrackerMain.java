package org.p2p;

import java.util.Scanner;

public class TrackerMain {

    // Constantes configuráveis
    private static final int DEFAULT_TRACKER_PORT = 4444;

    private static Tracker tracker;
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== TRACKER BITTORRENT ===\n");

        // Permite definir porta via argumentos ou usa padrão
        int port = DEFAULT_TRACKER_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Porta inválida, usando porta padrão: " + DEFAULT_TRACKER_PORT);
            }
        }

        System.out.println("Iniciando Tracker na porta: " + port);

        tracker = new Tracker(port);
        Thread trackerThread = new Thread(tracker::start);
        trackerThread.setDaemon(true);
        trackerThread.start();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Tracker iniciado! Comandos disponíveis:");
        System.out.println("1 - Status do Tracker");
        System.out.println("0 - Sair");

        while (true) {
            System.out.print("\nComando: ");
            String command = scanner.nextLine().trim();

            switch (command) {
                case "1" -> tracker.printStatus();
                case "0" -> {
                    System.out.println("Encerrando Tracker...");
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
        scanner.close();
        System.out.println("Tracker encerrado.");
    }
}