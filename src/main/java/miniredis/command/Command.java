package miniredis.command;

import miniredis.persistence.PersistenceLog;
import miniredis.store.Store;

/**
 * Common interface for all database commands.
 * Following the Command Pattern: each command encapsulates an action and its parameters.
 */
public interface Command {
    /**
     * Executes the command against the provided store.
     *
     * @param args  arguments passed after the command name (e.g. key, value)
     * @param store the shared key-value store
     * @return the formatted response string to send back to the client
     */
    String execute(String[] args, Store store);

    /**
     * Executes the command and optionally logs writes to the persistence log.
     */
    default String execute(String[] args, Store store, PersistenceLog persistenceLog) {
        return execute(args, store);
    }
}

