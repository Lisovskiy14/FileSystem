package org.example.data;

import lombok.Getter;

import java.util.ArrayDeque;
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
}
