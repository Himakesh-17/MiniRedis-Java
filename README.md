# MiniRedis (Java)

A high-performance, lightweight, in-memory key-value database server built from scratch in pure Java (JDK 17+) using TCP sockets and concurrency primitives.

---

## 🚀 Overview

**MiniRedis** is a custom implementation of an in-memory database inspired by Redis. It allows multiple clients to connect concurrently over TCP, issue text-based commands, and manipulate in-memory data structures with sub-millisecond response times.

Built without external heavy frameworks (no Spring, no Netty), this project directly leverages:
* Java Standard Library Networking (`ServerSocket`, `Socket`)
* High-throughput Concurrency (`ExecutorService`, `ConcurrentHashMap`)
* Clean Design Patterns (**Command Pattern**, Single Responsibility Principle)
* Thread-safe Atomic Operations (`ConcurrentHashMap.compute()`)

---

## 🏗️ Architecture

```text
 +-------------------------------------------------------------------+
 |                        Connected Clients                          |
 |         [Client 1]             [Client 2]             [Client 3]  |
 +-------------+----------------------+----------------------+-------+
               |                      |                      |
               +----------------------+----------------------+
                                      |  TCP (Port 6380)
                                      v
                       +------------------------------+
                       |      miniredis.server        |
                       |         Server.java          |
                       | (ServerSocket, accept loop)  |
                       +--------------+---------------+
                                      |
                                      v Dispatches to
                       +------------------------------+
                       |       ExecutorService        |
                       |    (Fixed Thread Pool: 10)   |
                       +--------------+---------------+
                                      |
                 +--------------------+--------------------+
                 |                    |                    |
                 v                    v                    v
          [ClientHandler]      [ClientHandler]      [ClientHandler]
         (Worker Thread 1)    (Worker Thread 2)    (Worker Thread 3)
                 |                    |                    |
                 +--------------------+--------------------+
                                      |
                                      v Parses via
                       +------------------------------+
                       |      miniredis.command       |
                       |      CommandParser.java      |
                       +--------------+---------------+
                                      | Executes
                 +---------+----------+----------+---------+
                 |         |          |          |         |
                 v         v          v          v         v
               [SET]     [GET]      [DEL]      [INCR]    [DECR] ...
                 |         |          |          |         |
                 +---------+----------+----------+---------+
                                      |
                                      v Reads / Writes atomically
                       +------------------------------+
                       |       miniredis.store        |
                       |          Store.java          |
                       |    (ConcurrentHashMap)       |
                       +------------------------------+
```

---

## 🛠️ Tech Stack

* **Language:** Java 17+ (Java 25 LTS compatible)
* **Build System:** Apache Maven
* **Testing:** JUnit 5 Jupiter + Custom Multithreaded Stress Tests
* **Port:** `6380`
* **Protocol:** Text-based protocol (1 request per line, 1 response per line)

---

## 📋 Completed Phases

### ✅ Phase 1: TCP Server with Thread Pool
* Multi-client connection handling using `ServerSocket.accept()`.
* Scalable fixed thread pool (`ExecutorService`) preventing thread-exhaustion attacks.
* Graceful shutdown with JVM shutdown hooks (`Runtime.getRuntime().addShutdownHook`).
* Clean disconnect and abrupt connection drop handling (`IOException` resilience).

### ✅ Phase 2: In-Memory Storage & Command Pattern
* Thread-safe centralized storage backed by `ConcurrentHashMap`.
* Implementation of the **Command Pattern** (`Command` interface, decoupled command classes, and `CommandParser`).
* Case-insensitive command execution.
* Supported commands: `SET`, `GET`, `DEL`, `EXISTS`, `KEYS`, and `QUIT`.

### ✅ Phase 3: Atomic INCR & DECR Operations
* Elimination of the **Read-Modify-Write race condition** using `ConcurrentHashMap.compute()`.
* Key-level locking ensuring atomicity without global map lock contention.
* Integer overflow detection via `Math.addExact()`.
* Concurrency stress-tested with **100 threads simultaneously** firing against a single key with 0 lost updates.

### ✅ Phase 4: TTL Key Expiry (Lazy & Active Expiration)
* Support for `SET key value EX seconds`, `TTL key`, and `EXPIRE key seconds`.
* **Hybrid Expiration Architecture:**
  * **Lazy Expiration (Passive):** Expired keys are detected and evicted on-the-fly during read operations (`GET`, `EXISTS`, `TTL`, `KEYS`).
  * **Active Expiration (Background Daemon):** `ExpiryManager` runs a scheduled daemon thread sweeping expired keys periodically to prevent memory leaks.
* Redis-compliant return codes: `-1` for keys without expiry, `-2` for non-existent/expired keys.

### ✅ Phase 5: Memory Limits with Custom LRU Eviction
* Configurable max key limit (`Store(maxSize)` or CLI argument `miniredis.Main [maxKeys]`).
* Custom **Doubly Linked List + HashMap** LRU engine built from scratch (no `LinkedHashMap`).
* **Sentinel (dummy) HEAD and TAIL nodes** eliminating null checks and edge-case pointer bugs.
* Guaranteed $O(1)$ operations for `touch`, `add`, `remove`, and `removeLast`.
* Synchronization ensuring `data`, `lruCache`, and background `ExpiryManager` stay in lockstep without memory leaks or race conditions.
* Added `DBSIZE` command to monitor database key count.

### ✅ Phase 6: Persistence via Append-Only Log (AOF) & Recovery
* **Crash-Resilience:** All state-changing mutations (`SET`, `DEL`, `INCR`, `DECR`, `EXPIRE`) are logged to `appendonly.log`.
* **Fast Startup Replay:** Server replays the log line-by-line upon boot before accepting client connections.
* **Absolute TTL (`PXAT`):** Expirations are logged as epoch millisecond timestamps, preventing keys from receiving renewed TTLs upon replay.
* **Corrupted Line Resilience:** Corrupted or half-written trailing lines are gracefully detected and skipped without crashing.
* **Atomic Compaction (`REWRITELOG` / `COMPACT`):** Rewrites the log into a snapshot of only currently active keys using a temporary file and atomic replace (`Files.move`), preventing unbounded log growth.
* **Configurable fsync Policy:** Supports `ALWAYS` (fsync each write), `EVERYSEC` (flush every write, fsync once per second), and `OFF`.

---

## 💻 Supported Commands

| Command | Arguments | Description | Example Response |
|---|---|---|---|
| `SET` | `<key> <value> [EX seconds]` | Stores a key-value pair, with optional TTL | `OK` |
| `GET` | `<key>` | Retrieves the value for the key | `Asha` or `(nil)` |
| `DEL` | `<key>` | Deletes the key from the store | `1` (deleted) or `0` (not found) |
| `EXISTS`| `<key>` | Checks if key exists in the database | `1` (exists) or `0` (missing) |
| `KEYS` | *(none)* | Returns all active, non-expired keys | `user:1, city, visits` |
| `INCR` | `<key>` | Atomically increments numeric value by 1 | `1`, `2`, ... |
| `DECR` | `<key>` | Atomically decrements numeric value by 1 | `0`, `-1`, ... |
| `TTL` | `<key>` | Returns remaining time-to-live in seconds | `28`, `-1` (persist), `-2` (missing) |
| `EXPIRE`| `<key> <seconds>` | Sets a timeout on an existing key | `1` (success) or `0` (missing) |
| `DBSIZE`| *(none)* | Returns current number of keys in store | `3` |
| `REWRITELOG` | *(none)* | Compacts the AOF persistence log | `OK` |
| `QUIT` | *(none)* | Closes client connection gracefully | `BYE` |

---

## 🚦 Getting Started

### Prerequisites
* **Java 17** or newer (`java -version`)
* **Maven** (`mvn -version`) or standard `javac`

### 1. Compile the Project
Using Maven:
```bash
mvn compile
```
Or directly using `javac`:
```bash
javac -d target/classes src/main/java/miniredis/Main.java src/main/java/miniredis/server/*.java src/main/java/miniredis/store/*.java src/main/java/miniredis/command/*.java
```

### 2. Start the Server
```bash
java -cp target/classes miniredis.Main
```
Output:
```text
=========================================
  MiniRedis Server started on port 6380
  Ready to accept connections...
=========================================
```

### 3. Connect a Client
You can connect using any TCP tool:

**Using Netcat / Telnet:**
```bash
nc localhost 6380
# or
telnet localhost 6380
```

**Using the Built-in Test Client:**
```bash
javac -cp target/classes -d target/test-classes src/test/java/miniredis/manual/*.java
java -cp "target/classes;target/test-classes" miniredis.manual.TestClient
```

---

## 🧪 Testing & Concurrency Verification

### Run Unit Tests
```bash
mvn test
```

### Run 100-Thread Atomic Stress Test
Proves zero lost updates under concurrent read-modify-write load:
```bash
java -cp "target/classes;target/test-classes" miniredis.manual.ConcurrentIncrDemo
```
Output:
```text
Starting 100 threads ready to INCR 'counter'...
All 100 threads completed in 6ms.
Final value of 'counter': 100
SUCCESS: Exactly 100! Race condition was completely prevented!
```

---

## 🔮 Roadmap
- [x] Phase 4: TTL Key Expiry (`EX seconds`, background cleanup thread, lazy expiration)
- [x] Phase 5: Memory Limit & LRU Eviction Policy
- [x] Phase 6: Persistence (Append-Only Log & Recovery on Startup)
- [ ] Phase 7: Benchmarking Suite (Requests/sec) & Metrics
