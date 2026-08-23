package com.amit.jobagent.document;
public interface ObjectStorage {
    void store(String storageKey,byte[] content,String contentType);
    byte[] load(String storageKey);
    void archive(String storageKey);
    default void delete(String storageKey) {
        // Optional rollback hook for storage implementations that support object deletion.
    }
}
