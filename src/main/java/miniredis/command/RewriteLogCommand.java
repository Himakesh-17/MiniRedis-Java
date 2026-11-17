package miniredis.command;

import miniredis.persistence.PersistenceLog;
import miniredis.store.Store;

/**
 * REWRITELOG
 * Compacts the append-only log so it contains only current live keys.
 */
public class RewriteLogCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        return execute(args, store, null);
    }

    @Override
    public String execute(String[] args, Store store, PersistenceLog persistenceLog) {
        if (persistenceLog == null) {
            return "(error) ERR persistence is disabled";
        }
        boolean ok = persistenceLog.compact(store);
        return ok ? "OK" : "(error) ERR log rewrite failed";
    }
}
