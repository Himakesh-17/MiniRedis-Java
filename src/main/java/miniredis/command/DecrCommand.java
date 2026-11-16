package miniredis.command;

import miniredis.store.Store;

/**
 * DECR key
 * Decrements the number stored at key by one.
 * If the key does not exist, it is set to 0 before performing the operation.
 * Returns: the value of key after the decrement, or an error.
 */
public class DecrCommand implements Command {
    @Override
    public String execute(String[] args, Store store) {
        if (args.length != 1) {
            return "(error) ERR wrong number of arguments for 'decr' command";
        }
        try {
            long result = store.incrementBy(args[0], -1);
            return String.valueOf(result);
        } catch (NumberFormatException | ArithmeticException e) {
            return "(error) ERR value is not an integer or out of range";
        }
    }
}
