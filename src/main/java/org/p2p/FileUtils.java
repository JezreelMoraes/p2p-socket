package org.p2p;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {

    public static void createFile(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);
        Path parentDir = path.getParent();

        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        if (!Files.exists(path)) {
            Files.createFile(path);
        }

        Files.write(path, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static String readFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new FileNotFoundException("Arquivo não encontrado: " + filePath);
        }

        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public static byte[] readFileAsBytes(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new FileNotFoundException("Arquivo não encontrado: " + filePath);
        }

        return Files.readAllBytes(path);
    }

    public static void createFileFromBytes(String filePath, byte[] data) throws IOException {
        Path path = Paths.get(filePath);
        Path parentDir = path.getParent();

        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    public static List<String> listFilesInDirectory(String dirPath) throws IOException {
        Path directory = Paths.get(dirPath);

        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }

        if (!Files.isDirectory(directory)) {
            throw new IOException("O caminho não é um diretório: " + dirPath);
        }

        List<String> fileNames = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    fileNames.add(path.getFileName().toString());
                }
            }
        }

        return fileNames;
    }

    public static void createDirectory(String dirPath) throws IOException {
        Path dir = Paths.get(dirPath);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }
}
