package miniredis.command;

import miniredis.store.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTest {
    private Store store;
    private CommandParser parser;

    @BeforeEach
    void setUp() {
        store = new Store();
        parser = new CommandParser(store);
    }

    @Test
    void testSetAndGet() {
        assertEquals("OK", parser.parseAndExecute("SET name Asha"));
        assertEquals("Asha", parser.parseAndExecute("GET name"));
    }

    @Test
    void testGetNonExistentKey() {
        assertEquals("(nil)", parser.parseAndExecute("GET missing"));
    }

    @Test
    void testSetValueWithSpaces() {
        assertEquals("OK", parser.parseAndExecute("SET greeting Hello World and Universe"));
        assertEquals("Hello World and Universe", parser.parseAndExecute("GET greeting"));
    }

    @Test
    void testExists() {
        parser.parseAndExecute("SET role engineer");
        assertEquals("1", parser.parseAndExecute("EXISTS role"));
        assertEquals("0", parser.parseAndExecute("EXISTS salary"));
    }

    @Test
    void testDel() {
        parser.parseAndExecute("SET token 12345");
        assertEquals("1", parser.parseAndExecute("DEL token"));
        assertEquals("0", parser.parseAndExecute("DEL token"));
        assertEquals("(nil)", parser.parseAndExecute("GET token"));
    }

    @Test
    void testKeys() {
        assertEquals("(empty list)", parser.parseAndExecute("KEYS"));
        parser.parseAndExecute("SET k1 v1");
        parser.parseAndExecute("SET k2 v2");
        String keysResponse = parser.parseAndExecute("KEYS");
        assertTrue(keysResponse.contains("k1"));
        assertTrue(keysResponse.contains("k2"));
    }

    @Test
    void testCaseInsensitiveCommands() {
        assertEquals("OK", parser.parseAndExecute("set user Alice"));
        assertEquals("Alice", parser.parseAndExecute("get user"));
        assertEquals("1", parser.parseAndExecute("exists user"));
        assertEquals("1", parser.parseAndExecute("del user"));
    }

    @Test
    void testUnknownCommand() {
        assertEquals("(error) ERR unknown command 'FOOBAR'", parser.parseAndExecute("FOOBAR hello"));
    }

    @Test
    void testWrongArgumentCounts() {
        assertEquals("(error) ERR wrong number of arguments for 'set' command", parser.parseAndExecute("SET onlyone"));
        assertEquals("(error) ERR wrong number of arguments for 'get' command", parser.parseAndExecute("GET"));
        assertEquals("(error) ERR wrong number of arguments for 'del' command", parser.parseAndExecute("DEL"));
        assertEquals("(error) ERR wrong number of arguments for 'exists' command", parser.parseAndExecute("EXISTS"));
        assertEquals("(error) ERR wrong number of arguments for 'incr' command", parser.parseAndExecute("INCR"));
        assertEquals("(error) ERR wrong number of arguments for 'incr' command", parser.parseAndExecute("INCR a b"));
        assertEquals("(error) ERR wrong number of arguments for 'decr' command", parser.parseAndExecute("DECR"));
        assertEquals("(error) ERR wrong number of arguments for 'decr' command", parser.parseAndExecute("DECR a b"));
    }

    @Test
    void testIncrOnNonexistentKey() {
        assertEquals("1", parser.parseAndExecute("INCR visits"));
        assertEquals("1", parser.parseAndExecute("GET visits"));
    }

    @Test
    void testConsecutiveIncrAndDecr() {
        assertEquals("1", parser.parseAndExecute("INCR counter"));
        assertEquals("2", parser.parseAndExecute("INCR counter"));
        assertEquals("3", parser.parseAndExecute("INCR counter"));
        assertEquals("2", parser.parseAndExecute("DECR counter"));
        assertEquals("1", parser.parseAndExecute("DECR counter"));
    }

    @Test
    void testDecrIntoNegative() {
        assertEquals("-1", parser.parseAndExecute("DECR balance"));
        assertEquals("-2", parser.parseAndExecute("DECR balance"));
        assertEquals("-1", parser.parseAndExecute("INCR balance"));
    }

    @Test
    void testIncrOnInvalidString() {
        parser.parseAndExecute("SET name Asha");
        assertEquals("(error) ERR value is not an integer or out of range", parser.parseAndExecute("INCR name"));
        assertEquals("(error) ERR value is not an integer or out of range", parser.parseAndExecute("DECR name"));
    }

    @Test
    void testIncrOverflow() {
        parser.parseAndExecute("SET big " + Long.MAX_VALUE);
        assertEquals("(error) ERR value is not an integer or out of range", parser.parseAndExecute("INCR big"));
    }

    @Test
    void testTtlWithoutExpiry() {
        parser.parseAndExecute("SET user Alice");
        assertEquals("-1", parser.parseAndExecute("TTL user"));
    }

    @Test
    void testTtlNonExistentKey() {
        assertEquals("-2", parser.parseAndExecute("TTL non_existent"));
    }

    @Test
    void testSetWithExAndTtl() {
        assertEquals("OK", parser.parseAndExecute("SET session abc123xyz EX 30"));
        assertEquals("abc123xyz", parser.parseAndExecute("GET session"));
        long ttl = Long.parseLong(parser.parseAndExecute("TTL session"));
        assertTrue(ttl > 0 && ttl <= 30);
    }

    @Test
    void testLazyExpiration() throws InterruptedException {
        // Set key with 1 second TTL
        assertEquals("OK", parser.parseAndExecute("SET temp key123 EX 1"));
        assertEquals("key123", parser.parseAndExecute("GET temp"));

        // Wait 1.1 seconds so key expires
        Thread.sleep(1100);

        // Accessing expired key should trigger lazy expiration
        assertEquals("(nil)", parser.parseAndExecute("GET temp"));
        assertEquals("-2", parser.parseAndExecute("TTL temp"));
        assertEquals("0", parser.parseAndExecute("EXISTS temp"));
    }

    @Test
    void testExpireCommand() throws InterruptedException {
        parser.parseAndExecute("SET token secret");
        assertEquals("-1", parser.parseAndExecute("TTL token"));

        // Set expiration using EXPIRE
        assertEquals("1", parser.parseAndExecute("EXPIRE token 1"));
        long ttl = Long.parseLong(parser.parseAndExecute("TTL token"));
        assertTrue(ttl > 0 && ttl <= 1);

        Thread.sleep(1100);
        assertEquals("(nil)", parser.parseAndExecute("GET token"));
        assertEquals("0", parser.parseAndExecute("EXPIRE token 10")); // Cannot expire nonexistent key
    }
}
