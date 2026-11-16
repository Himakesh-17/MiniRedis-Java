package miniredis.command;

import miniredis.store.Store;

/**
 * EXISTS key
 * Checks if a key exists in the database.
 * Returns: "1" if key exists, "0" if it does not.
 */
public class ExistsCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        if (args.length != 1) {
            return "(error) ERR wrong number of arguments for 'exists' command";
        }
        boolean exists = store.exists(args[0]);
        return exists ? "1" : "0";
    }
}
