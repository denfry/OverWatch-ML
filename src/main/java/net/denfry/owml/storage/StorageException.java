package net.denfry.owml.storage;

/**
 * Exception thrown when storage operations fail.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public class StorageException extends Exception {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(Throwable cause) {
        super(cause);
    }
}