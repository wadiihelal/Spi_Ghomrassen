package com.promoteur.app.attachment;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where attachment bytes live (FE-05). The application only ever knows a storage key; the
 * backend behind this interface decides what that key means.
 */
public interface StorageService {

    /**
     * Stores a stream of bytes and returns the key it was stored under.
     *
     * @param content   the bytes to keep
     * @param extension file extension without the dot, used to keep files recognisable on disk
     * @return an opaque, unique key
     * @throws IOException when the bytes could not be written
     */
    String store(InputStream content, String extension) throws IOException;

    /**
     * @return a readable handle on the stored bytes
     */
    Resource load(String storageKey);

    /**
     * Removes the stored bytes. Removing a key that no longer exists is not an error.
     */
    void delete(String storageKey) throws IOException;
}
