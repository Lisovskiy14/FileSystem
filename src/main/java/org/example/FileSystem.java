package org.example;

import org.example.common.FileType;
import org.example.data.VirtualDisk;
import org.example.exception.InvalidFileTypeException;
import org.example.file.DirectoryEntry;
import org.example.file.DirectoryTree;
import org.example.file.FileDescriptor;
import org.example.openFile.OpenFile;
import org.example.openFile.OpenFileTable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public class FileSystem {
    private static DirectoryTree directoryTree;
    private static OpenFileTable openFileTable;
    private static VirtualDisk virtualDisk;

    private static void init() {
        directoryTree = DirectoryTree.init();
        openFileTable = new OpenFileTable();
        virtualDisk = new VirtualDisk(100);
    }

    public static void main(String[] args) {
        init();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                System.out.printf("%s> ".formatted(directoryTree.getCwdPath()));
                String[] command = scanner.nextLine().split(" ");

                switch (command[0]) {
                    case "stat":
                        stat(command[1]);
                        break;
                    case "create":
                        create(command[1]);
                        break;
                    case "link":
                        link(command[1], command[2]);
                        break;
                    case "unlink":
                        unlink(command[1]);
                        break;
                    case "ls":
                        ls();
                        break;
                    case "open":
                        open(command[1]);
                        break;
                    case "close":
                        close(command[1]);
                        break;
                    case "seek":
                        seek(command[1], command[2]);
                        break;
                    case "read":
                        read(command[1], command[2]);
                        break;
                    case "write":
                        String dataToWrite = Arrays.stream(command)
                                .skip(2)
                                .reduce("", (a, b) -> a + b + " ");
                        write(command[1], dataToWrite);
                        break;
                    case "truncate":
                        truncate(command[1], command[2]);
                        break;
                    case "cd":
                        cd(command[1]);
                        break;
                    default:
                        System.out.println("Unknown command.");
                }
            } catch (Exception ex) {
                System.out.printf("Error: %s\n".formatted(ex.getMessage()));
            }
        }
    }

    private static void create(String path) {
        int newId = directoryTree.findFreeDescriptorId();

        FileDescriptor fileDescriptor = FileDescriptor.builder()
                .id(newId)
                .type(FileType.REGULAR)
                .linkCount(1)
                .size(0)
                .directBlocks(new ArrayList<>())
                .build();

        FileDescriptor directory = directoryTree.resolveDirectory(Path.of(path));
        fileDescriptor.setParentId(directory.getId());

        directoryTree.addNewDescriptor(path, fileDescriptor);
        System.out.printf("File %s created successfully. Descriptor number - %d\n".formatted(path, newId));
    }

    private static void link(String path1, String path2) {
        FileDescriptor descriptor = directoryTree.resolvePath(path1);
        descriptor.setLinkCount(descriptor.getLinkCount() + 1);

        directoryTree.addHardLinkToDescriptor(path2, descriptor.getId());
        System.out.printf("File %s linked to descriptor %d successfully.\n".formatted(path2, descriptor.getId()));
    }

    private static void unlink(String path) {
        FileDescriptor descriptor = directoryTree.resolvePath(path);
        descriptor.setLinkCount(descriptor.getLinkCount() - 1);

        directoryTree.removeHardLinkFromDescriptor(path, descriptor.getId());

        if (descriptor.getLinkCount() == 0 &&
                openFileTable.getOpenFileByDescriptorId(descriptor.getId()) == null) {
            descriptor.getDirectBlocks().forEach(virtualDisk::removeBlock);
        }

        System.out.printf("File %s unlinked successfully.\n".formatted(path));
    }

    private static void stat(String name) {
        FileDescriptor descriptor = directoryTree.resolvePath(name);
        System.out.println(descriptor.toString());
    }

    private static void ls() {
        List<DirectoryEntry> cwdEntries = new ArrayList<>(
                directoryTree.getCwd().getDirectoryEntries().values());
        for (DirectoryEntry entry : cwdEntries) {
            System.out.println(entry.toString());
        }
    }

    private static void open(String path) {
        FileDescriptor descriptor = directoryTree.resolvePath(path);
        int descriptorId = descriptor.getId();

        int fd = openFileTable.addOpenFile(descriptorId);

        System.out.printf("File %s opened successfully. FD: %d\n".formatted(path, fd));
    }

    private static void close(String stringFd) {
        int fd = Integer.parseInt(stringFd);

        openFileTable.removeOpenFile(fd);

        System.out.printf("FD %d closed successfully.\n".formatted(fd));
    }

    private static void seek(String stringFd, String stringOffset) {
        int fd = Integer.parseInt(stringFd);
        int offset = Integer.parseInt(stringOffset);

        OpenFile openFile = openFileTable.getOpenFileByFd(fd);
        openFile.setCurrentOffset(offset);

        System.out.printf("Seeked to offset %d successfully.\n".formatted(offset));
    }

    private static void read(String stringFd, String stringSize) {
        int fd = Integer.parseInt(stringFd);
        int size = Integer.parseInt(stringSize);

        OpenFile openFile = openFileTable.getOpenFileByFd(fd);
        int descriptorId = openFile.getDescriptorId();
        int offset = openFile.getCurrentOffset();

        FileDescriptor descriptor = directoryTree.getFileDescriptorById(descriptorId);
        List<Integer> directBlocks = descriptor.getDirectBlocks();

        byte[] readData = virtualDisk.readBlocksWithOffset(
                directBlocks,
                offset,
                size
        );

        openFile.setCurrentOffset(readData.length);
        String stringReadData = new String(readData, StandardCharsets.UTF_8);

        System.out.printf("Read %d bytes to offset %d: %s\n"
                .formatted(readData.length, openFile.getCurrentOffset(), stringReadData));
    }

    private static void write(String stringFd, String stringData) {
        byte[] data = stringData.getBytes();
        int fd = Integer.parseInt(stringFd);

        OpenFile openFile = openFileTable.getOpenFileByFd(fd);
        int descriptorId = openFile.getDescriptorId();
        int offset = openFile.getCurrentOffset();

        FileDescriptor descriptor = directoryTree.getFileDescriptorById(descriptorId);
        List<Integer> directBlocks = descriptor.getDirectBlocks();

        int newOffset = virtualDisk.writeBlocksWithOffset(data, directBlocks, offset);
        openFile.setCurrentOffset(newOffset);

        if (newOffset > descriptor.getSize()) {
            descriptor.setSize(newOffset);
        }

        System.out.printf("Wrote %d bytes to offset %d.\n".formatted(data.length, openFile.getCurrentOffset()));
    }

    private static void truncate(String path, String stringSize) {
        int size = Integer.parseInt(stringSize);

        FileDescriptor fileDescriptor = directoryTree.resolvePath(path);
        List<Integer> directBlocks = fileDescriptor.getDirectBlocks();

        if (size < fileDescriptor.getSize()) {
            fileDescriptor.setDirectBlocks(virtualDisk.truncateBlocks(directBlocks, size));
            fileDescriptor.setSize(size);
            System.out.printf("Truncated file %s to size %d.\n".formatted(path, size));
        }
    }

    private static void cd(String path) {
        FileDescriptor fileDescriptor = directoryTree.resolvePath(path);
        if (fileDescriptor.getType() != FileType.DIRECTORY) {
            throw new InvalidFileTypeException("Path %s is not a directory.".formatted(path));
        }
        directoryTree.setCwd(fileDescriptor);
    }
}