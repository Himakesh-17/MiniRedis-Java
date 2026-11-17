package miniredis.command;

import miniredis.store.Store;

/**
 * TTL key
 * Returns the remaining time-to-live of a key in seconds:
 *   -2 if the key does not exist or has expired.
 *   -1 if the key exists but has no associated expiration.
 *   >= 0 remaining time-to-live in seconds.
 */
public class TtlCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        if (args.length != 1) {
            return "(error) ERR wrong number of arguments for 'ttl' command";
        }
        long ttl = store.ttl(args[0]);
        return String.valueOf(ttl);
    }
}
