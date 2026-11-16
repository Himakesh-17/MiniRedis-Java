package miniredis.command;

import miniredis.store.Store;

import java.util.Set;

/**
 * KEYS
 * Returns all stored keys in the database.
 * Returns: comma-separated list of keys, or "(empty list)" if empty.
 */
public class KeysCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        Set<String> keys = store.keys();
        if (keys.isEmpty()) {
            return "(empty list)";
        }
        return String.join(", ", keys);
    }
}
