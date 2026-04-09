package org.example;

import org.example.common.FileType;
import org.example.data.VirtualDisk;
import org.example.exception.CannotCreateFileException;
import org.example.exception.CannotRemoveFileException;
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
        virtualDisk = new VirtualDisk(100);
        directoryTree = DirectoryTree.init(virtualDisk);
        openFileTable = new OpenFileTable();
    }

    public static void main(String[] args) {
        init();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            try {
                FileDescriptor cwd = directoryTree.getCwd();
                System.out.printf("%s> ".formatted(directoryTree.getPath(cwd)));
                String[] command = scanner.nextLine().trim().split(" ");

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
                    case "mkdir":
                        mkdir(command[1]);
                        break;
                    case "rmdir":
                        rmdir(command[1]);
                        break;
                    case "symlink":
                        symlink(command[1], command[2]);
                        break;
                    default:
                        if (!command[0].isBlank()) {
                            System.out.println("Unknown command: " + command[0]);
                        }
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

        directoryTree.addNewDescriptor(path, fileDescriptor);
    }

    private static void link(String path1, String path2) {
        FileDescriptor targetDescriptor = directoryTree.resolvePath(path1);
        if (targetDescriptor.getType() == FileType.DIRECTORY) {
            throw new InvalidFileTypeException("Cannot link a directory.");
        }

        targetDescriptor.setLinkCount(targetDescriptor.getLinkCount() + 1);
        directoryTree.addHardLinkToDescriptor(path2, targetDescriptor.getId());

        System.out.printf("File %s linked to descriptor %d successfully.\n".formatted(path2, targetDescriptor.getId()));
    }

    private static void unlink(String path) {
        FileDescriptor descriptor = directoryTree.resolvePath(path);
        if (descriptor.getType() == FileType.DIRECTORY) {
            throw new InvalidFileTypeException("Cannot unlink a directory.");
        }

        descriptor.setLinkCount(descriptor.getLinkCount() - 1);
        directoryTree.removeHardLink(path);

        if (descriptor.getLinkCount() == 0 &&
                openFileTable.getOpenFileByDescriptorId(descriptor.getId()) == null) {
            descriptor.getDirectBlocks().forEach(virtualDisk::removeBlock);
            directoryTree.removeDescriptor(descriptor);
        }

        System.out.printf("File %s unlinked successfully.\n".formatted(path));
    }

    private static void stat(String path) {
        FileDescriptor descriptor = directoryTree.resolvePath(path);
        System.out.println(descriptor.toString());
    }

    private static void ls() {
        List<DirectoryEntry> cwdEntries = new ArrayList<>(
                directoryTree.getCwd().getDirectoryEntries().values());

        List<DirectoryEntry> entriesToExclude = new ArrayList<>();
        cwdEntries.stream()
                .filter(entry -> entry.getName().equals(".") || entry.getName().equals(".."))
                .forEach(entriesToExclude::add);
        cwdEntries.removeAll(entriesToExclude);

        for (DirectoryEntry entry : cwdEntries) {
            System.out.println(entry.toString());
        }
    }

    private static void open(String path) {
        FileDescriptor descriptor = directoryTree.resolvePathWithSymlinkResolving(path);

        if (descriptor.getType() == FileType.DIRECTORY) {
            throw new InvalidFileTypeException("Cannot open a directory %s".formatted(path));
        }

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

        System.out.println(stringReadData);
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

        FileDescriptor fileDescriptor = directoryTree.resolvePathWithSymlinkResolving(path);
        if (fileDescriptor.getType() != FileType.REGULAR) {
            throw new InvalidFileTypeException("Cannot truncate not a REGULAR file %s"
                    .formatted(path));
        }

        List<Integer> directBlocks = fileDescriptor.getDirectBlocks();

        if (size < fileDescriptor.getSize()) {
            fileDescriptor.setDirectBlocks(virtualDisk.truncateBlocks(directBlocks, size));
            fileDescriptor.setSize(size);
            System.out.printf("Truncated file %s to size %d.\n".formatted(path, size));
        }
    }

    private static void mkdir(String path) {
        int descriptorId = directoryTree.findFreeDescriptorId();

        Path fullPath = Path.of(path);
        FileDescriptor parentDirectory = directoryTree.resolveDirectory(fullPath);

        Map<String, DirectoryEntry>  directoryEntries = new HashMap<>();
        directoryEntries.put(".", new DirectoryEntry(".", descriptorId));
        directoryEntries.put("..", new DirectoryEntry("..", parentDirectory.getId()));

        FileDescriptor newDirectory = FileDescriptor.builder()
                .id(descriptorId)
                .type(FileType.DIRECTORY)
                .directoryEntries(directoryEntries)
                .build();

        directoryTree.addNewDescriptor(path, newDirectory);

        directoryTree.setCwd(newDirectory);
    }

    private static void rmdir(String path) {
        FileDescriptor directory = directoryTree.resolvePathWithSymlinkResolving(path);
        if (directory.getType() != FileType.DIRECTORY) {
            throw new InvalidFileTypeException("Provided file is not a directory: " + directory.getType());
        }

        if (directory.equals(directoryTree.getRoot())) {
            throw new CannotRemoveFileException("Cannot remove root directory");
        }

        Map<String, DirectoryEntry> directoryEntries = directory.getDirectoryEntries();
        if (directoryEntries.size() == 2) {
            directoryTree.removeHardLink(path);
            directoryTree.removeDescriptor(directory);

        } else {
            throw new CannotRemoveFileException("Target directory is not empty.");
        }
    }

    private static void cd(String path) {
        FileDescriptor fileDescriptor = directoryTree.resolvePathWithSymlinkResolving(path);
        if (fileDescriptor.getType() != FileType.DIRECTORY) {
            throw new InvalidFileTypeException("Path %s is not a directory."
                    .formatted(path));
        }
        directoryTree.setCwd(fileDescriptor);
    }

    private static void symlink(String str, String path) {
        // Here the str value must be validated to be sure it is a valid path.
        // resolvePath() method will throw an exception if the path is incorrect.
        directoryTree.resolvePath(str);

        if (str.length() > virtualDisk.getBLOCK_SIZE()) {
            throw new CannotCreateFileException("Provided file is too large: %d. Max size of symlink is %d."
                    .formatted(str.length(), virtualDisk.getBLOCK_SIZE()));
        }

        int descriptorId = directoryTree.findFreeDescriptorId();
        byte[] block = str.getBytes(StandardCharsets.UTF_8);

        int freeIndex = virtualDisk.pollFreeIndex();
        System.arraycopy(
                block, 0,
                virtualDisk.getBlock(freeIndex), 0,
                block.length
        );

        FileDescriptor fileDescriptor = FileDescriptor.builder()
                .id(descriptorId)
                .type(FileType.SYMLINK)
                .linkCount(1)
                .size(str.length())
                .directBlocks(List.of(freeIndex))
                .build();

        directoryTree.addNewDescriptor(path, fileDescriptor);
    }
}