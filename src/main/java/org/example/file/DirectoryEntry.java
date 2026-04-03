package org.example.file;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class DirectoryEntry {
    private String name;
    private int descriptorId;

    @Override
    public String toString() {
        return name + " -> descriptor_id: " + descriptorId;
    }
}
