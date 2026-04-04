package org.example.file.exception;

public class FileDescriptorNotFoundException extends RuntimeException {
    public FileDescriptorNotFoundException(String message) {
        super(message);
    }
}
