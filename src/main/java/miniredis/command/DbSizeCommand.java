package miniredis.command;

import miniredis.store.Store;

/**
 * DBSIZE
 * Returns the number of keys stored in the database.
 */
public class DbSizeCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        return String.valueOf(store.size());
    }
}
