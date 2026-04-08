package org.example.data;

import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.util.*;

@Getter
public class VirtualDisk {
    private final int BLOCK_SIZE = 20;
    private final byte[][] blocks;
    private Queue<Integer> freeIndexQueue;

    public VirtualDisk(int size) {
        blocks = new byte[size][20];
        freeIndexQueue = new ArrayDeque<>();
        for (int i = 0; i < size; i++) {
            freeIndexQueue.add(i);
        }
    }

    public byte[] getBlock(int index) {
        return blocks[index];
    }

    public void setBlock(int index, byte[] block) {
        blocks[index] = block;
        freeIndexQueue.remove(index);
    }

    public void removeBlock(int index) {
        blocks[index] = new byte[20];
        freeIndexQueue.add(index);
    }

    public int pollFreeIndex() {
        Integer freeIndex = freeIndexQueue.poll();
        if (freeIndex == null) {
            return -1;
        }
        return freeIndex;
    }

    public byte[] readBlocksWithOffset(List<Integer> directBlocks, int offset, int size) {
        byte[] allBlocks = readAllBlocks(directBlocks);
        int end = Math.min(offset + size, allBlocks.length);

        return Arrays.copyOfRange(allBlocks, offset, end);
    }

    public byte[] readAllBlocks(List<Integer> directBlocks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (int blockIndex : directBlocks) {
            byte[] block = blocks[blockIndex];
            for (byte b : block) {
                if (b != 0) {
                    out.write(b);
                }
            }
        }

        return out.toByteArray();
    }

    public int writeBlocksWithOffset(byte[] data, List<Integer> directBlocks, int offset) {
        int sizeToWrite = data.length;
        int bytesWritten = 0;

        int currentOffset = offset;
        while (bytesWritten < sizeToWrite) {
            int logicalBlockIndex = currentOffset / BLOCK_SIZE;
            int offsetInBlock = currentOffset % BLOCK_SIZE;

            int spaceLeftInBlock = BLOCK_SIZE - offsetInBlock;
            int bytesToWrite = Math.min(spaceLeftInBlock, sizeToWrite - bytesWritten);

            int physicalBlockIndex;
            if (logicalBlockIndex >= directBlocks.size()) {
                physicalBlockIndex = pollFreeIndex();
                directBlocks.add(physicalBlockIndex);
            } else {
                physicalBlockIndex = directBlocks.get(logicalBlockIndex);
            }

            System.arraycopy(
                    data, bytesWritten,
                    getBlock(physicalBlockIndex), offsetInBlock,
                    bytesToWrite
            );

            currentOffset += bytesToWrite;
            bytesWritten += bytesToWrite;
        }

        return currentOffset;
    }

    public List<Integer> truncateBlocks(List<Integer> directBlocks, int size) {
        int logicalBlockIndex = size / BLOCK_SIZE;
        int offsetInBlock = size % BLOCK_SIZE;

        System.arraycopy(
                new byte[BLOCK_SIZE], 0,
                getBlock(directBlocks.get(logicalBlockIndex)), offsetInBlock,
                BLOCK_SIZE - offsetInBlock
        );

        List<Integer> linksToRemove = new ArrayList<>();
        directBlocks.stream()
                .filter(i -> i > logicalBlockIndex)
                .forEach(i -> {
                    linksToRemove.add(i);
                    removeBlock(i);
                });
        directBlocks.removeAll(linksToRemove);

        return directBlocks;
    }
}
