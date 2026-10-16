package com.ekp.storage;

/** Wraps low-level object-storage failures so callers don't depend on AWS types. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
