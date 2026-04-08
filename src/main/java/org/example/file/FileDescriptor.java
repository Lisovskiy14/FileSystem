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
@ToString
public class FileDescriptor {
    private int id;
    private FileType type;
    private int linkCount;
    private int size;
    private List<Integer> directBlocks;
    private int indirectBlock;
    private Map<String, DirectoryEntry> directoryEntries;
}
