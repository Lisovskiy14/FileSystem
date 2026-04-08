package org.example.file;

import lombok.Getter;
import lombok.Setter;
import org.example.common.FileType;
import org.example.file.exception.DirectoryNotFoundException;
import org.example.file.exception.FileDescriptorNotFoundException;
import org.example.file.exception.InvalidFileNameException;
import org.example.file.exception.NoFreeDescriptorException;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class DirectoryTree {
    private final FileDescriptor root;
    private FileDescriptor cwd;
    private FileDescriptor[] fileDescriptors;

    private DirectoryTree(FileDescriptor root, FileDescriptor cwd, FileDescriptor[] fileDescriptors) {
        this.root = root;
        this.cwd = cwd;
        this.fileDescriptors = fileDescriptors;
    }

    public static DirectoryTree init() {
        FileDescriptor[] fileDescriptors = new FileDescriptor[100];

        int descriptorId = 0;

        Map<String, DirectoryEntry> directoryEntries = new HashMap<>();
        directoryEntries.put(".", new DirectoryEntry(".", descriptorId));
        directoryEntries.put("..", new DirectoryEntry("..", descriptorId));

        FileDescriptor root = FileDescriptor.builder()
                .id(descriptorId)
                .type(FileType.DIRECTORY)
                .directoryEntries(directoryEntries)
                .build();
        fileDescriptors[descriptorId] = root;

        return new DirectoryTree(root, root, fileDescriptors);
    }

    public FileDescriptor getFileDescriptorById(int id) {
        FileDescriptor descriptor = fileDescriptors[id];
        if (descriptor == null) {
            throw new FileDescriptorNotFoundException("File descriptor with id %d not found.".formatted(id));
        }

        return descriptor;
    }

    public FileDescriptor resolvePath(String path) throws DirectoryNotFoundException {
        String[] directoryPath = path.split("/");
        if (directoryPath.length == 0) {
            throw new DirectoryNotFoundException("Path is empty.");
        }

        FileDescriptor current;
        if (path.startsWith("/") || directoryPath[0].equals(".") || directoryPath[0].equals("..")) {
            int skip = 1;
            if (directoryPath[0].equals("..")) {
                current = fileDescriptors[cwd.getDirectoryEntries().get("..").getDescriptorId()];
            } else if (directoryPath[0].equals(".")) {
                current = cwd;
            } else {
                current = root;
                skip++;
            }
            directoryPath = Arrays.stream(directoryPath)
                    .skip(skip)
                    .toArray(String[]::new);
        } else {
            current = cwd;
        }

        for (String directoryName : directoryPath) {
            DirectoryEntry currentDirectoryEntry = current.getDirectoryEntries().get(directoryName);
            if (currentDirectoryEntry == null) {
                throw new DirectoryNotFoundException("File %s not found in path %s"
                        .formatted(directoryName, path));
            }
            current = fileDescriptors[currentDirectoryEntry.getDescriptorId()];
        }

        return current;
    }

    public String getCwdPath() {
        if (cwd.getId() == root.getId()) {
            return "/root";
        }

        StringBuilder path = new StringBuilder();
        int currentDirectoryId = cwd.getId();

        while (currentDirectoryId != root.getId()) {
            FileDescriptor currentDirectory = fileDescriptors[currentDirectoryId];

            int parentDirectoryId = currentDirectory.getDirectoryEntries().get("..").getDescriptorId();
            FileDescriptor parentDirectory = fileDescriptors[parentDirectoryId];

            int currentDirectoryIdFinal = currentDirectoryId;
            String directoryName = parentDirectory.getDirectoryEntries().values().stream()
                    .filter(entry -> entry.getDescriptorId() == currentDirectoryIdFinal)
                    .filter(entry -> !entry.getName().equals(".") && !entry.getName().equals(".."))
                    .findFirst()
                    .map(DirectoryEntry::getName)
                    .orElse("unknown");

            path.insert(0, "/%s".formatted(directoryName));
            currentDirectoryId = parentDirectoryId;
        }

        path.insert(0, "/root");

        return path.toString();
    }

    public int findFreeDescriptorId() {
        for (int i = 1; i < fileDescriptors.length; i++) {
            if (fileDescriptors[i] == null) {
                return i;
            }
        }
        throw new NoFreeDescriptorException("No free descriptors available.");
    }

    public void addNewDescriptor(String path, FileDescriptor fileDescriptor) {
        int descriptorId = fileDescriptor.getId();
        fileDescriptors[descriptorId] = fileDescriptor;
        addHardLinkToDescriptor(path, descriptorId);
    }

    public void removeDescriptor(FileDescriptor fileDescriptor) {
        int descriptorId = fileDescriptor.getId();
        fileDescriptors[descriptorId] = null;
    }

    public void addHardLinkToDescriptor(String path, int descriptorId) {
        Path fullPath = Path.of(path);

        FileDescriptor directory = resolveDirectory(fullPath);
        String fileName = resolveFileName(fullPath);

        directory.getDirectoryEntries().put(
                fileName,
                new DirectoryEntry(fileName, descriptorId)
        );
    }

    public void removeHardLinkFromDescriptor(String path, int descriptorId) {
        Path fullPath = Path.of(path);

        FileDescriptor directory = resolveDirectory(fullPath);
        String fileName = resolveFileName(fullPath);

        directory.getDirectoryEntries().remove(fileName);
    }

    public FileDescriptor resolveDirectory(Path path) {
        Path directoryPath = path.getParent();

        FileDescriptor directory = cwd;
        if (directoryPath != null) {
            directory = resolvePath(directoryPath.toString());
        }

        return directory;
    }

    public String resolveFileName(Path path) {
        Path fileNamePath = path.getFileName();

        if (fileNamePath == null) {
            throw new InvalidFileNameException("Cannot determine file name from path %s".formatted(path));
        }

        return fileNamePath.toString();
    }
}
