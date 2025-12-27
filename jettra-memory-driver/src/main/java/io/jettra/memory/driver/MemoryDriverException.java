package io.jettra.memory.driver;

public class MemoryDriverException extends RuntimeException {
    public MemoryDriverException(String message) {
        super(message);
    }
    public MemoryDriverException(String message, Throwable cause) {
        super(message, cause);
    }
}
