package miniredis.command;

import miniredis.store.Store;

/**
 * SET key value [EX seconds]
 * Stores a key-value pair, with an optional expiration time in seconds.
 * Returns: "OK"
 */
public class SetCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        if (args.length < 2) {
            return "(error) ERR wrong number of arguments for 'set' command";
        }

        String key = args[0];
        Long ttlSeconds = null;
        int valueEndIndex = args.length;

        // Check if the command includes the optional EX parameter at the end
        if (args.length >= 4 && "EX".equalsIgnoreCase(args[args.length - 2])) {
            try {
                long parsedTtl = Long.parseLong(args[args.length - 1]);
                if (parsedTtl <= 0) {
                    return "(error) ERR invalid expire time in 'set' command";
                }
                ttlSeconds = parsedTtl;
                valueEndIndex = args.length - 2;
            } catch (NumberFormatException e) {
                return "(error) ERR value is not an integer or out of range";
            }
        } else if (args.length >= 3 && "EX".equalsIgnoreCase(args[args.length - 1])) {
            return "(error) ERR syntax error";
        }

        // Combine value tokens
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < valueEndIndex; i++) {
            if (i > 1) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        String value = sb.toString();

        if (ttlSeconds != null) {
            store.put(key, value, ttlSeconds);
        } else {
            store.put(key, value);
        }

        return "OK";
    }
}
