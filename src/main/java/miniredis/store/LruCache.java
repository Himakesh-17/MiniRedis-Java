package miniredis.store;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Custom LRU (Least Recently Used) tracking cache.
 * Implemented with a HashMap and a custom Doubly Linked List with Sentinel HEAD and TAIL nodes.
 *
 * Provides O(1) thread-safe operations:
 * - touch(key): moves key node to front (MRU)
 * - add(key): inserts key node at front (MRU)
 * - removeLast(): evicts and returns the key at the back (LRU)
 * - remove(key): removes a key node anywhere in O(1)
 */
public class LruCache {

    // Doubly linked list node
    static class Node {
        final String key;
        Node prev;
        Node next;

        Node(String key) {
            this.key = key;
        }
    }

    private final Map<String, Node> nodeMap = new HashMap<>();
    private final Node head;
    private final Node tail;
    private final ReentrantLock lock = new ReentrantLock();

    public LruCache() {
        // Sentinel (dummy) nodes eliminate edge-case null checks on empty list or head/tail inserts
        this.head = new Node("__HEAD__");
        this.tail = new Node("__TAIL__");
        head.next = tail;
        tail.prev = head;
    }

    /**
     * Marks an existing key as Most Recently Used by moving it immediately after HEAD.
     * O(1) time complexity.
     */
    public void touch(String key) {
        lock.lock();
        try {
            Node node = nodeMap.get(key);
            if (node != null) {
                removeNode(node);
                insertAtFront(node);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds a key to the front (Most Recently Used).
     * If already present, moves it to the front.
     * O(1) time complexity.
     */
    public void add(String key) {
        lock.lock();
        try {
            Node existing = nodeMap.get(key);
            if (existing != null) {
                removeNode(existing);
                insertAtFront(existing);
            } else {
                Node newNode = new Node(key);
                nodeMap.put(key, newNode);
                insertAtFront(newNode);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes and returns the Least Recently Used key (the node right before TAIL).
     * O(1) time complexity.
     *
     * @return the evicted key, or null if the cache is empty.
     */
    public String removeLast() {
        lock.lock();
        try {
            if (head.next == tail) {
                return null; // Cache is empty
            }
            Node lru = tail.prev;
            removeNode(lru);
            nodeMap.remove(lru.key);
            return lru.key;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a key from the LRU structure.
     * O(1) time complexity.
     */
    public void remove(String key) {
        lock.lock();
        try {
            Node node = nodeMap.remove(key);
            if (node != null) {
                removeNode(node);
            }
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return nodeMap.size();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            nodeMap.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            lock.unlock();
        }
    }

    // Helper: Unlinks node from its neighbors in O(1)
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }

    // Helper: Inserts node immediately after the sentinel HEAD in O(1)
    private void insertAtFront(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }
}
