package org.example.openFile;

import lombok.Getter;

import java.util.Arrays;

@Getter
public class OpenFileTable {
    private final OpenFile[] openFiles = new OpenFile[100];

    public OpenFile getOpenFileByFd(int fd) {
        if (fd < 0 || fd >= openFiles.length) {
            return null;
        }
        return openFiles[fd];
    }

    public OpenFile getOpenFileByDescriptorId(int descriptorId) {
        return Arrays.stream(openFiles)
                .filter(openFile -> openFile != null && openFile.getDescriptorId() == descriptorId)
                .findFirst()
                .orElse(null);
    }

    public int addOpenFile(int descriptorId) {
        OpenFile openFile = new OpenFile(descriptorId, 0);
        int fd = findFreeFd();

        if (fd != -1) {
            openFiles[fd] = openFile;
        }

        return fd;
    }

    public boolean removeOpenFile(int fd) {
        if (fd < 0 || fd >= openFiles.length) {
            return false;
        }
        openFiles[fd] = null;
        return true;
    }

    private int findFreeFd() {
        for (int i = 0; i < openFiles.length; i++) {
            if (openFiles[i] == null) {
                return i;
            }
        }
        return -1;
    }
}
