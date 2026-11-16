package miniredis.command;

import miniredis.store.Store;

/**
 * SET key value
 * Stores a key-value pair in the database.
 * Returns: "OK"
 */
public class SetCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        if (args.length < 2) {
            return "(error) ERR wrong number of arguments for 'set' command";
        }
        String key = args[0];

        // If the value contains spaces, join all remaining arguments
        String value;
        if (args.length == 2) {
            value = args[1];
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) {
                    sb.append(" ");
                }
                sb.append(args[i]);
            }
            value = sb.toString();
        }

        store.put(key, value);
        return "OK";
    }
}
