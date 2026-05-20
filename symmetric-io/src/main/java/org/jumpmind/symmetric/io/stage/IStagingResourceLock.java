package org.jumpmind.symmetric.io.stage;

public interface IStagingResourceLock {
    long getLockAge();

    boolean isAcquired();

    void setAcquired(boolean acquired);

    String getLockFailureMessage();

    void setLockFailureMessage(String lockFailureMessage);

    void releaseLock();

    void breakLock();

    boolean isPresent();

    String getAbsolutePath();
}