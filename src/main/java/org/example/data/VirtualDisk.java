package org.example.data;

import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

@Getter
public class VirtualDisk {
    private final int blockSize = 20;
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
