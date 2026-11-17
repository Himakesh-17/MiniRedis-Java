package miniredis.command;

import miniredis.store.Store;

/**
 * EXPIRE key seconds
 * Sets a timeout on key. After the timeout has expired, the key will automatically be deleted.
 * Returns:
 *   "1" if the timeout was set.
 *   "0" if key does not exist or timeout could not be set.
 */
public class ExpireCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        if (args.length != 2) {
            return "(error) ERR wrong number of arguments for 'expire' command";
        }
        long seconds;
        try {
            seconds = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            return "(error) ERR value is not an integer or out of range";
        }

        boolean updated = store.expire(args[0], seconds);
        return updated ? "1" : "0";
    }
}
