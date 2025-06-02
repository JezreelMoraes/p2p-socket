package org.p2p;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

@Getter
class FileInfo {

    private final String fileName;
    private final long fileSize;
    private final String checksum;
    private final Set<String> seeders;

    public FileInfo(String fileName, long fileSize, String checksum) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.seeders = ConcurrentHashMap.newKeySet();
    }

    public void addSeeder(String peerId) {
        seeders.add(peerId);
    }

    public void removeSeeder(String peerId) {
        seeders.remove(peerId);
    }

}
