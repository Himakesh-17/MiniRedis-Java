package miniredis.manual;

import miniredis.command.CommandParser;
import miniredis.store.Store;

/**
 * Demonstrates LRU eviction using a max capacity of 3 keys:
 * 1. SET a 1
 * 2. SET b 2
 * 3. SET c 3 (Cache is full: [c, b, a])
 * 4. GET a (Touches 'a': [a, c, b])
 * 5. SET d 4 (Forces eviction of 'b', the least recently used: [d, a, c])
 */
public class LruDemo {
    public static void main(String[] args) {
        System.out.println("=== MiniRedis LRU Cache Eviction Demo (Max Size: 3) ===\n");

        Store store = new Store(3);
        CommandParser parser = new CommandParser(store);

        // Step 1: SET a 1
        System.out.println("Step 1: SET a 1 -> " + parser.parseAndExecute("SET a 1"));
        printStatus(parser);

        // Step 2: SET b 2
        System.out.println("Step 2: SET b 2 -> " + parser.parseAndExecute("SET b 2"));
        printStatus(parser);

        // Step 3: SET c 3 (Capacity reached: 3 items)
        System.out.println("Step 3: SET c 3 -> " + parser.parseAndExecute("SET c 3"));
        printStatus(parser);

        // Step 4: GET a -> Accessing 'a' makes it Most Recently Used! Order is now [a, c, b]
        System.out.println("Step 4: GET a -> " + parser.parseAndExecute("GET a") + " (Touches 'a'; 'b' is now LRU)");
        printStatus(parser);

        // Step 5: SET d 4 -> Store is at limit (3), so 'b' MUST be evicted!
        System.out.println("Step 5: SET d 4 -> " + parser.parseAndExecute("SET d 4") + " (Evicts least recently used 'b')");
        printStatus(parser);

        // Verification checks
        String getB = parser.parseAndExecute("GET b");
        String getA = parser.parseAndExecute("GET a");
        String getC = parser.parseAndExecute("GET c");
        String getD = parser.parseAndExecute("GET d");

        System.out.println("--- Final Verifications ---");
        System.out.println("GET b (evicted): " + getB + " (expected: (nil))");
        System.out.println("GET a (kept):    " + getA + " (expected: 1)");
        System.out.println("GET c (kept):    " + getC + " (expected: 3)");
        System.out.println("GET d (kept):    " + getD + " (expected: 4)");

        if ("(nil)".equals(getB) && "1".equals(getA) && "3".equals(getC) && "4".equals(getD)) {
            System.out.println("\nSUCCESS: 'b' was correctly evicted, others retained, DBSIZE <= 3!");
        } else {
            System.err.println("\nFAILURE: Eviction did not behave as expected.");
        }
    }

    private static void printStatus(CommandParser parser) {
        System.out.println("   KEYS:   " + parser.parseAndExecute("KEYS"));
        System.out.println("   DBSIZE: " + parser.parseAndExecute("DBSIZE"));
        System.out.println();
    }
}
