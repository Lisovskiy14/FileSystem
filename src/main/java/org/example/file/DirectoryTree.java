package org.example.file;

import lombok.Setter;
import org.example.common.FileType;
import org.example.file.exception.DirectoryNotFoundException;

import java.util.Arrays;
import java.util.HashMap;

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

        FileDescriptor root = FileDescriptor.builder()
                .id(0)
                .type(FileType.DIRECTORY)
                .directoryEntries(new HashMap<>())
                .parentDirectoryId(0)
                .build();
        fileDescriptors[0] = root;

        return new DirectoryTree(root, root, fileDescriptors);
    }

    public FileDescriptor resolvePath(String path) {
        String[] directoryPath = path.split("/");
        if (directoryPath.length == 0) {
            throw new DirectoryNotFoundException("Path is empty.");
        }

        FileDescriptor current;
        if (path.startsWith("/") || directoryPath[0].equals(".") || directoryPath[0].equals("..")) {
            int skip = 1;
            if (directoryPath[0].equals("..")) {
                current = fileDescriptors[cwd.getParentDirectoryId()];
            } else if (directoryPath[0].equals(".")) {
                current = cwd;
            } else {
                current = root;
                skip++;
            }
            directoryPath = Arrays.stream(directoryPath).skip(skip).toArray(String[]::new);
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
        StringBuilder path = new StringBuilder();

        int currentDirectoryId = cwd.getId();
        while (currentDirectoryId != root.getId()) {
            FileDescriptor currentDirectory = fileDescriptors[currentDirectoryId];
            path.insert(0, "/%s".formatted(currentDirectory.getDirectoryName()));
            currentDirectoryId = currentDirectory.getParentDirectoryId();
        }
        path.insert(0, "/root");

        return path.toString();
    }
}
