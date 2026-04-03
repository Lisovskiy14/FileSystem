package org.example.openFile;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class OpenFile {
    private int descriptorId;
    private int currentOffset;
}
