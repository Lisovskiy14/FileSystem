package org.example.exception;

public class CannotRemoveFileException extends RuntimeException {
    public CannotRemoveFileException(String message) {
        super(message);
    }
}
