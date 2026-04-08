package org.example.exception;

public class CannotCreateFileException extends RuntimeException {
    public CannotCreateFileException(String message) {
        super(message);
    }
}
