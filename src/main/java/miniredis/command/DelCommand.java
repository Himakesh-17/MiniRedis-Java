package miniredis.command;

import miniredis.persistence.PersistenceLog;
import miniredis.store.Store;

/**
 * DEL key
 * Removes the specified key from the database.
 * Returns: "1" if deleted, "0" if the key did not exist.
 */
public class DelCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        return execute(args, store, null);
    }

    @Override
    public String execute(String[] args, Store store, PersistenceLog persistenceLog) {
        if (args.length != 1) {
            return "(error) ERR wrong number of arguments for 'del' command";
        }
        boolean deleted = store.delete(args[0]);
        if (deleted && persistenceLog != null) {
            persistenceLog.logDel(args[0]);
        }
        return deleted ? "1" : "0";
    }
}

