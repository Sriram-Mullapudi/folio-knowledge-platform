package com.ekp.storage;

import java.io.InputStream;

/**
 * Object-storage abstraction. The rest of the app depends only on this
 * interface, never on the AWS SDK, so the backing store can change without
 * touching business code.
 */
public interface StorageService {

    /** Stores {@code bytes} under {@code key} with the given content type. */
    void store(String key, byte[] bytes, String contentType);

    /** Opens a stream to the stored object. Caller must close it. */
    StorageObject load(String key);

    /** Deletes the object. No-op if it does not exist. */
    void delete(String key);

    /** A retrieved object: its stream plus the metadata needed to serve it. */
    record StorageObject(InputStream stream, long size, String contentType) {}
}
