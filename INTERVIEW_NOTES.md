# MiniRedis — Technical Interview Guide & Resume Notes

This document provides concise, high-impact answers to likely technical interview questions about MiniRedis, demonstrating deep knowledge of networking, concurrency, storage engines, and systems design.

---

## 🎯 Resume Bullet Points (Tailored for Top Tech Roles)

* **Engineered a distributed-ready in-memory key-value database server in pure Java (JDK 17+)** supporting concurrent TCP clients, custom $O(1)$ LRU cache eviction, and atomic mutations, achieving **240,000+ requests/sec** with **0.12 ms p99 latency** across 50 concurrent client connections.
* **Designed a crash-resilient Append-Only File (AOF) persistence engine** featuring configurable `fsync` policies, absolute epoch timestamp replay (`PXAT`), and atomic snapshot compaction via OS-level atomic file renames, sustaining **149,000+ durable writes/sec**.

---

## 💡 Top 8 Technical Interview Questions & Answers

### 1. Why does `ConcurrentHashMap` alone NOT prevent lost updates on `INCR`? How did you fix it?
**Answer:**  
"While `ConcurrentHashMap` guarantees that individual method calls (`get()`, `put()`) are atomic and thread-safe, **combining two thread-safe calls is not an atomic transaction**. A naive `INCR` executes a Read-Modify-Write sequence: Thread A reads `10`, Thread B reads `10`, both increment to `11`, and both write `11`, losing one increment.  
I eliminated this race condition using `ConcurrentHashMap.compute()`. It locks only the individual bucket for that key, reading, computing, and writing the updated value in a single, uninterrupted critical section without locking the entire map."

### 2. Why did you use an `ExecutorService` thread pool instead of spawning a `new Thread()` per client?
**Answer:**  
"Creating an unbound thread per connection is an anti-pattern that leaves the server vulnerable to **Denial of Service (DoS) through thread exhaustion**. Each Java OS thread consumes ~1MB of call-stack memory; 10,000 clients would consume 10GB of RAM just for thread stacks, triggering `OutOfMemoryError` and excessive CPU context-switching overhead.  
A fixed thread pool decouples connection handling from worker execution, caps maximum resource utilization, and queues excess requests predictably in the OS backlog."

### 3. How does your key expiration (TTL) work? Why is a hybrid approach necessary?
**Answer:**  
"We use Redis's two-pronged **hybrid expiration strategy**:
1. **Lazy Expiration (Passive):** Whenever a key is accessed (`GET`, `EXISTS`, `TTL`, `KEYS`), we check `System.currentTimeMillis() >= expiresAt`. If expired, it is evicted immediately.
2. **Active Expiration (Background Daemon):** If a client sets an OTP with a 30-second TTL and disconnects, lazy expiration will never touch it, leaking memory. We run an `ExpiryManager` scheduled daemon thread every 100ms to sweep expired keys.  
Neither strategy alone is optimal: lazy alone leaks memory for cold keys, while active alone wastes CPU cycles scanning keys that could be checked on demand."

### 4. Why did you combine a `HashMap` and a custom Doubly Linked List for LRU eviction? Why use sentinel nodes?
**Answer:**  
"A `HashMap` provides $O(1)$ key lookup but has no access order. A Doubly Linked List maintains chronological access order but requires an $O(n)$ linear scan to find a node. By storing linked list `Node` references directly inside the `HashMap`, we get **$O(1)$ lookup AND $O(1)$ re-linking to the front (MRU) or eviction from the tail (LRU)**.  
We use sentinel (dummy) `HEAD` and `TAIL` nodes so that every real node always has non-null `prev` and `next` pointers. This completely eliminates edge-case checks for empty lists or head/tail boundaries, making pointer manipulation safe and concise."

### 5. Why must an Append-Only Log record absolute expiry timestamps (`PXAT`) rather than relative durations (`EX`)?
**Answer:**  
"If a client executes `SET otp 4821 EX 30` and the server crashes and restarts **10 minutes later**, replaying `EX 30` would wrongly grant the OTP another fresh 30 seconds of life.  
To guarantee deterministic replay, we translate relative timeouts to absolute Unix epoch timestamps (`PXAT <epochMillis>`). On replay, if `System.currentTimeMillis() >= epochMillis`, the entry is discarded on-the-fly and never loaded into RAM."

### 6. What is the difference between `flush()` and `fsync()`? What trade-off does `everysec` represent?
**Answer:**  
"`writer.flush()` pushes bytes from Java's application buffer into the **Operating System Page Cache**. The data is in OS memory: if the JVM process crashes, the OS will still write it. However, if the physical server loses power, data in the page cache is lost.  
`fsync` (`FileChannel.force()`) forces the OS to physically write the page cache to the disk hardware. Calling `fsync` on every write drops throughput by ~95% due to disk write latency. MiniRedis defaults to `everysec`: flushing to the OS on every write and scheduling an `fsync` once per second. This delivers RAM-speed throughput (~150k writes/sec) with a guaranteed maximum loss window of at most 1 second during hard power failure."

### 7. How does log compaction (`REWRITELOG`) work, and why does it write to a temporary file first?
**Answer:**  
"Without compaction, an append-only log grows indefinitely (e.g. 10,000 `INCR` lines for a single key). Compaction writes a snapshot of only current, unexpired keys to `appendonly.log.tmp`.  
Writing directly over the active log file would corrupt the database if the server crashed halfway through. By writing to a temporary file first and then performing an atomic filesystem rename (`Files.move` with `ATOMIC_MOVE`), the replacement is all-or-nothing: the operating system guarantees the old log remains intact until the new one is completely in place."

### 8. If you were taking MiniRedis to 1,000,000 requests/second, what would you change?
**Answer:**  
"1. **Non-Blocking I/O (NIO / Netty):** Replace thread-per-client blocking I/O with an event-driven reactor loop (`Selector`, epoll / kqueue), eliminating thread stack memory overhead and supporting 100,000+ concurrent idle connections.  
2. **Min-Heap for Active Expiration:** Replace the $O(n)$ periodic map scan with a priority queue (min-heap) ordered by expiration timestamp, enabling $O(1)$ lookup of the next key to expire.  
3. **Partitioning / Sharding:** Split the global store across multiple worker threads using lock-free ring buffers (Actor model or Redis cluster-style hash slots) to eliminate thread contention entirely."
