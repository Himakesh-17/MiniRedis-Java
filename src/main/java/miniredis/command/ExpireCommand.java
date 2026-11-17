package miniredis.command;

import miniredis.persistence.PersistenceLog;
import miniredis.store.Entry;
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
        return execute(args, store, null);
    }

    @Override
    public String execute(String[] args, Store store, PersistenceLog persistenceLog) {
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
        if (updated && persistenceLog != null) {
            Entry entry = store.getEntry(args[0]);
            if (entry != null && entry.hasExpiry()) {
                persistenceLog.logExpire(args[0], entry.getExpiresAtMillis());
            }
        }
        return updated ? "1" : "0";
    }
}

