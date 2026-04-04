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
    private static final FileDescriptor[] fileDescriptors = new FileDescriptor[100];
    private static final List<DirectoryEntry> rootDirectoryEntries = new ArrayList<>();
    private static final OpenFileTable openFileTable = new OpenFileTable();
    private static final VirtualDisk virtualDisk = new VirtualDisk(100);

    private static void init() {
        directoryTree = DirectoryTree.init();
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

    private static void open(String name) {
        DirectoryEntry directoryEntry = getEntryByFileName(name);
        if (directoryEntry == null) {
            System.out.println("File not found.");
            return;
        }
        int descriptorId = directoryEntry.getDescriptorId();

        int fd = openFileTable.addOpenFile(descriptorId);

        if (fd == -1) {
            System.out.println("No free FD available.");
            return;
        }

        System.out.printf("File %s opened successfully. FD: %d\n".formatted(name, fd));
    }

    private static void close(String stringFd) {
        int fd;
        try {
            fd = Integer.parseInt(stringFd);
        } catch (NumberFormatException ex) {
            System.out.println("Invalid FD.");
            return;
        }

        if (openFileTable.removeOpenFile(fd)) {
            System.out.printf("FD %d closed successfully.\n".formatted(fd));
        } else {
            System.out.println("FD not found.");
        }
    }

    private static void seek(String stringFd, String stringOffset) {
        int fd;
        int offset;
        try {
            fd = Integer.parseInt(stringFd);
            offset = Integer.parseInt(stringOffset);
        } catch (NumberFormatException ex) {
            System.out.println("Invalid FD or Offset.");
            return;
        }

        OpenFile openFile = openFileTable.getOpenFileByFd(fd);
        if (openFile == null) {
            System.out.println("FD not found.");
            return;
        }

        openFile.setCurrentOffset(offset);
        System.out.printf("Seeked to offset %d successfully.\n".formatted(offset));
    }

    private static void read(String stringFd, String stringSize) {
        int fd;
        int size;
        try {
            fd = Integer.parseInt(stringFd);
            size = Integer.parseInt(stringSize);
        } catch (NumberFormatException ex) {
            System.out.println("Invalid FD or Size.");
            return;
        }

        OpenFile openFile = openFileTable.getOpenFileByFd(fd);
        if (openFile == null) {
            System.out.println("FD not found.");
            return;
        }
        int descriptorId = openFile.getDescriptorId();
        int offset = openFile.getCurrentOffset();

        FileDescriptor descriptor = fileDescriptors[descriptorId];
        List<Integer> blockLinks = descriptor.getDirectBlocks();

        if (blockLinks == null || blockLinks.isEmpty()) {
            System.out.println("File is empty.");
            return;
        }

        List<Byte> allData = new ArrayList<>();
        for (int blockLink : blockLinks) {
            byte[] block = virtualDisk.getBlock(blockLink);
            for (byte b : block) {
                allData.add(b);
            }
        }

        int end = Math.min(offset + size, allData.size());
        List<Byte> readData = allData.subList(offset, end);

        openFile.setCurrentOffset(end);

        byte[] readDataArray = new byte[readData.size()];
        for (int i = 0; i < readData.size(); i++) {
            readDataArray[i] = readData.get(i);
        }
        String stringReadData = new String(readDataArray, StandardCharsets.UTF_8);
        System.out.printf("Read %d bytes to offset %d: %s\n"
                .formatted(readData.size(), openFile.getCurrentOffset(), stringReadData));
    }

    private static void write(String stringFd, String stringData) {
        byte[] data = stringData.getBytes();
        int fd;
        try {
            fd = Integer.parseInt(stringFd);
        } catch (NumberFormatException ex) {
            System.out.println("Invalid FD or Size.");
            return;
        }

        OpenFile openFile = openFileTable.getOpenFileByFd(fd);
        if (openFile == null) {
            System.out.println("FD not found.");
            return;
        }
        int descriptorId = openFile.getDescriptorId();


        FileDescriptor descriptor = fileDescriptors[descriptorId];
        List<Integer> blockLinks = descriptor.getDirectBlocks();
        int sizeToWrite = data.length;
        int bytesWritten = 0;

        int blockSize = virtualDisk.getBlockSize();

        while (bytesWritten < sizeToWrite) {
            int currentOffset = openFile.getCurrentOffset();
            int logicalBlockIndex = currentOffset / blockSize;
            int offsetInBlock = currentOffset % blockSize;

            int spaceLeftInBlock = blockSize - offsetInBlock;
            int bytesToWrite = Math.min(spaceLeftInBlock, sizeToWrite - bytesWritten);

            int physicalBlockIndex;
            if (logicalBlockIndex >= blockLinks.size()) {
                physicalBlockIndex = virtualDisk.pollFreeIndex();
                blockLinks.add(physicalBlockIndex);
            } else {
                physicalBlockIndex = blockLinks.get(logicalBlockIndex);
            }

            System.arraycopy(
                    data, bytesWritten,
                    virtualDisk.getBlock(physicalBlockIndex), offsetInBlock,
                    bytesToWrite
            );

            openFile.setCurrentOffset(currentOffset + bytesToWrite);
            bytesWritten += bytesToWrite;

            if (openFile.getCurrentOffset() > descriptor.getSize()) {
                descriptor.setSize(openFile.getCurrentOffset());
            }
        }

        System.out.printf("Wrote %d bytes to offset %d.\n".formatted(sizeToWrite, openFile.getCurrentOffset()));
    }

    private static void truncate(String name, String stringSize) {
        int size;
        try {
            size = Integer.parseInt(stringSize);
        } catch (NumberFormatException ex) {
            System.out.println("Invalid Size.");
            return;
        }

        DirectoryEntry directoryEntry = getEntryByFileName(name);
        if (directoryEntry == null) {
            System.out.println("File not found.");
            return;
        }
        int descriptorId = directoryEntry.getDescriptorId();

        FileDescriptor fileDescriptor = fileDescriptors[descriptorId];
        List<Integer> blockLinks = fileDescriptor.getDirectBlocks();
        int blockSize = virtualDisk.getBlockSize();

        if (size < fileDescriptor.getSize()) {
            int logicalBlockIndex = size / blockSize;
            int offsetInBlock = size % blockSize;

            System.arraycopy(
                    new byte[blockSize], 0,
                    virtualDisk.getBlock(blockLinks.get(logicalBlockIndex)), offsetInBlock,
                    blockSize - offsetInBlock
            );

            List<Integer> linksToRemove = new ArrayList<>();
            blockLinks.stream()
                    .filter(i -> i > logicalBlockIndex)
                    .forEach(i -> {
                        linksToRemove.add(i);
                        virtualDisk.removeBlock(i);
                    });
            blockLinks.removeAll(linksToRemove);

            fileDescriptor.setSize(size);

            System.out.printf("Truncated file %s to size %d.\n".formatted(name, size));
        }
    }

    private static void cd(String path) {
        FileDescriptor fileDescriptor = directoryTree.resolvePath(path);
        if (fileDescriptor.getType() != FileType.DIRECTORY) {
            throw new InvalidFileTypeException("Path %s is not a directory.".formatted(path));
        }
        directoryTree.setCwd(fileDescriptor);
    }



    private static int findFreeDescriptorId() {
        for (int i = 0; i < fileDescriptors.length; i++) {
            if (fileDescriptors[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private static DirectoryEntry getEntryByFileName(String name) {
        return rootDirectoryEntries.stream()
                .filter(entry -> entry.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}