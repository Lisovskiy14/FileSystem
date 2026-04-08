package org.example.file;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.example.common.FileType;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class FileDescriptor {
    private int id;
    private FileType type;
    private int linkCount;
    private int size;
    private List<Integer> directBlocks;
    private int indirectBlock;
    private Map<String, DirectoryEntry> directoryEntries;

    @Override
    public String toString() {
        String pattern = """
                Desc. Id: %d
                Type: %s
                LinkCount: %d
                Size: %d
                DirectBlocks: %s
                IndirectBlock: %d
                DirectoryEntries: %s
                """.trim();

        return String.format(pattern, id, type, linkCount, size, directBlocks, indirectBlock, directoryEntries);
    }
}
