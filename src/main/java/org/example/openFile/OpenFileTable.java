package org.example.openFile;

import lombok.Getter;
import org.example.openFile.exception.FdNotAvailableException;
import org.example.openFile.exception.InvalidFdRangeException;
import org.example.openFile.exception.OpenFileNotFoundException;

import java.util.Arrays;

@Getter
public class OpenFileTable {
    private final OpenFile[] openFiles = new OpenFile[100];

    public OpenFile getOpenFileByFd(int fd) {
        if (fd < 0 || fd >= openFiles.length) {
            throw new InvalidFdRangeException("FD is out of range (0, %d).".formatted(openFiles.length - 1));
        }

        OpenFile openFile = openFiles[fd];
        if (openFile == null) {
            throw new OpenFileNotFoundException("FD %d not found.".formatted(fd));
        }

        return openFile;
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
        openFiles[fd] = openFile;
        return fd;
    }

    public void removeOpenFile(int fd) {
        if (fd < 0 || fd >= openFiles.length) {
            throw new InvalidFdRangeException("FD is out of range (0, %d).".formatted(openFiles.length - 1));
        }
        openFiles[fd] = null;
    }

    private int findFreeFd() {
        for (int i = 0; i < openFiles.length; i++) {
            if (openFiles[i] == null) {
                return i;
            }
        }
        throw new FdNotAvailableException("No free FDs available.");
    }
}
