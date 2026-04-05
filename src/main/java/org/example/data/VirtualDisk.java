package org.example.data;

import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

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
        List<Byte> allBlocks = readAllBlocks(directBlocks);

        int end = Math.min(offset + size, allBlocks.size());
        List<Byte> readData = allBlocks.subList(offset, end);

        byte[] readDataArray = new byte[readData.size()];
        for (int i = 0; i < readData.size(); i++) {
            readDataArray[i] = readData.get(i);
        }

        return readDataArray;
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

    private List<Byte> readAllBlocks(List<Integer> directBlocks) {
        List<Byte> allData = new ArrayList<>();
        for (int blockLink : directBlocks) {
            byte[] block = blocks[blockLink];
            for (byte b : block) {
                allData.add(b);
            }
        }
        return allData;
    }
}
