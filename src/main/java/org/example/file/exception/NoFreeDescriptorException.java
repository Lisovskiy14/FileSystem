package org.example.file.exception;

public class NoFreeDescriptorException extends RuntimeException {
    public NoFreeDescriptorException(String message) {
        super(message);
    }
}
