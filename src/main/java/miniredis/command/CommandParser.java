package miniredis.command;

import miniredis.store.Store;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses incoming raw text lines, extracts the command name and arguments,
 * and delegates execution to the registered Command implementation.
 */
public class CommandParser {
    private final Store store;
    private final Map<String, Command> commandRegistry = new HashMap<>();

    public CommandParser(Store store) {
        this.store = store;
        registerDefaultCommands();
    }

    private void registerDefaultCommands() {
        commandRegistry.put("SET", new SetCommand());
        commandRegistry.put("GET", new GetCommand());
        commandRegistry.put("DEL", new DelCommand());
        commandRegistry.put("EXISTS", new ExistsCommand());
        commandRegistry.put("KEYS", new KeysCommand());
        commandRegistry.put("INCR", new IncrCommand());
        commandRegistry.put("DECR", new DecrCommand());
        commandRegistry.put("TTL", new TtlCommand());
        commandRegistry.put("EXPIRE", new ExpireCommand());
    }

    /**
     * Parses a raw line and executes the matching command.
     *
     * @param rawLine line of text received from the client
     * @return response string to send back to the client
     */
    public String parseAndExecute(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        String trimmed = rawLine.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        // Split on one or more whitespace characters
        String[] tokens = trimmed.split("\\s+");
        String commandName = tokens[0].toUpperCase();
        String[] args = Arrays.copyOfRange(tokens, 1, tokens.length);

        Command command = commandRegistry.get(commandName);
        if (command == null) {
            return "(error) ERR unknown command '" + tokens[0] + "'";
        }

        return command.execute(args, store);
    }

    public Store getStore() {
        return store;
    }
}
