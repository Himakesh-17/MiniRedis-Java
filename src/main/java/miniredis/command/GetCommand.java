package miniredis.command;

import miniredis.store.Store;

/**
 * GET key
 * Retrieves the value stored at key.
 * Returns: the value, or "(nil)" if the key does not exist.
 */
public class GetCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        if (args.length != 1) {
            return "(error) ERR wrong number of arguments for 'get' command";
        }
        String value = store.get(args[0]);
        if (value == null) {
            return "(nil)";
        }
        return value;
    }
}
