package miniredis.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LruTest {

    @Test
    void testLruEvictionWhenCapacityExceeded() {
        Store store = new Store(3);

        store.put("a", "1");
        store.put("b", "2");
        store.put("c", "3");

        assertEquals(3, store.size());

        // Access "a" to make it Most Recently Used
        assertEquals("1", store.get("a"));

        // Insert "d" -> "b" was least recently accessed, so "b" must be evicted!
        store.put("d", "4");

        assertEquals(3, store.size());
        assertNull(store.get("b"), "Key 'b' should have been evicted by LRU");
        assertEquals("1", store.get("a"));
        assertEquals("3", store.get("c"));
        assertEquals("4", store.get("d"));
    }

    @Test
    void testUpdateExistingKeyDoesNotEvict() {
        Store store = new Store(2);

        store.put("k1", "v1");
        store.put("k2", "v2");

        // Updating existing key "k1" should not cause "k2" to be evicted
        store.put("k1", "v1_updated");

        assertEquals(2, store.size());
        assertEquals("v1_updated", store.get("k1"));
        assertEquals("v2", store.get("k2"));
    }

    @Test
    void testExpiredKeyLeavesLruList() throws InterruptedException {
        Store store = new Store(3);

        // Put a key with 1 second TTL
        store.put("temp", "val", 1);
        store.put("k1", "v1");
        store.put("k2", "v2");

        assertEquals(3, store.size());

        // Wait 1.1s for temp to expire
        Thread.sleep(1100);

        // Put another key "k3"
        store.put("k3", "v3");

        // "temp" expired, so it should have been purged, leaving k1, k2, k3 intact
        assertNull(store.get("temp"));
        assertEquals("v1", store.get("k1"));
        assertEquals("v2", store.get("k2"));
        assertEquals("v3", store.get("k3"));
        assertEquals(3, store.size());
    }
}
