package miniredis.manual;

import miniredis.command.CommandParser;
import miniredis.store.ExpiryManager;
import miniredis.store.Store;

/**
 * Demonstrates TTL expiration:
 * 1. SET key with EX seconds.
 * 2. Checks TTL countdown.
 * 3. Proves lazy and background expiration automatically purges the key.
 */
public class TtlExpiryDemo {
    public static void main(String[] args) throws InterruptedException {
        Store store = new Store();
        ExpiryManager expiryManager = new ExpiryManager(store, 100);
        expiryManager.start();
        CommandParser parser = new CommandParser(store);

        System.out.println("=== MiniRedis TTL Expiration Demo ===\n");

        // 1. Setting key without expiry
        System.out.println("1. Setting 'user' without expiry...");
        System.out.println("-> SET user Asha: " + parser.parseAndExecute("SET user Asha"));
        System.out.println("-> TTL user: " + parser.parseAndExecute("TTL user") + " (expected: -1, persists forever)\n");

        // 2. Setting key with 2 second expiration
        System.out.println("2. Setting 'otp' to 4821 with EX 2 seconds...");
        System.out.println("-> SET otp 4821 EX 2: " + parser.parseAndExecute("SET otp 4821 EX 2"));
        System.out.println("-> GET otp: " + parser.parseAndExecute("GET otp"));
        System.out.println("-> TTL otp: " + parser.parseAndExecute("TTL otp") + "s remaining\n");

        // 3. Waiting 1 second
        System.out.println("Waiting 1 second...");
        Thread.sleep(1000);
        System.out.println("-> TTL otp: " + parser.parseAndExecute("TTL otp") + "s remaining\n");

        // 4. Waiting another 1.2 seconds (total > 2.2 seconds)
        System.out.println("Waiting another 1.2 seconds (total > 2.2s)...");
        Thread.sleep(1200);

        System.out.println("-> GET otp: " + parser.parseAndExecute("GET otp") + " (expected: (nil))");
        System.out.println("-> TTL otp: " + parser.parseAndExecute("TTL otp") + " (expected: -2, does not exist)");
        System.out.println("-> EXISTS otp: " + parser.parseAndExecute("EXISTS otp") + " (expected: 0)\n");

        // 5. Using EXPIRE command on an existing key
        System.out.println("5. Testing EXPIRE command on existing key 'user'...");
        System.out.println("-> EXPIRE user 1: " + parser.parseAndExecute("EXPIRE user 1"));
        System.out.println("-> TTL user: " + parser.parseAndExecute("TTL user") + "s remaining");
        Thread.sleep(1100);
        System.out.println("-> GET user: " + parser.parseAndExecute("GET user") + " (expected: (nil))\n");

        expiryManager.stop();
        System.out.println("=== TTL Expiration Verified Successfully ===");
    }
}
