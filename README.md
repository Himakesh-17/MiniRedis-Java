# MiniRedis

A high-performance, lightweight, in-memory key-value database server built from scratch in pure Java (JDK 17+) using TCP sockets, concurrency primitives, and crash-resilient persistence.

---

## 🚀 Features

* **Multi-Client TCP Server:** Concurrent connection handling using Java `ServerSocket` and a fixed thread pool (`ExecutorService`).
* **Clean Architecture:** Strict decoupling using the **Command Pattern** (`Command`, `CommandParser`) and the Single Responsibility Principle.
* **Thread-Safe Storage:** Backed by `ConcurrentHashMap` with atomic operations preventing read-modify-write race conditions.
* **Key Expiration (TTL):** Hybrid expiration architecture combining **Lazy Expiration** on read and an **Active Background Expiration** daemon.
* **Custom LRU Cache Eviction:** Custom-built $O(1)$ Least Recently Used eviction engine using a `HashMap` and **Doubly Linked List with Sentinel Nodes** (no `LinkedHashMap`).
* **Append-Only File (AOF) Persistence:** Crash-resilient logging of all write mutations with configurable `fsync` policies (`EVERYSEC`, `ALWAYS`, `OFF`).
* **Absolute TTL Replay (`PXAT`):** Expirations are logged as absolute epoch millisecond timestamps, preventing expiration drift across restarts.
* **Atomic Log Compaction (`REWRITELOG`):** Atomically compacts the log file using temporary files and filesystem atomic moves.
* **Zero External Dependencies:** Built entirely with the Java Standard Library.

---

## 🏗️ Architecture

```text
 +-------------------------------------------------------------------------------+
 |                              Connected Clients                                |
 |            [Client 1]               [Client 2]              [Client N]        |
 +----------------+------------------------+-----------------------+-------------+
                  |                        |                       |
                  +------------------------+-----------------------+
                                           |  TCP Connection (Port 6380)
                                           v
                          +---------------------------------+
                          |        miniredis.server         |
                          |           Server.java           |
                          |   (ServerSocket, Accept Loop)   |
                          +----------------+----------------+
                                           |
                                           v Dispatches via
                          +---------------------------------+
                          |         ExecutorService         |
                          |     (Worker Pool: 64 Threads)   |
                          +----------------+----------------+
                                           |
                    +----------------------+----------------------+
                    |                      |                      |
                    v                      v                      v
             [ClientHandler]        [ClientHandler]        [ClientHandler]
            (Worker Thread 1)      (Worker Thread 2)      (Worker Thread N)
                    |                      |                      |
                    +----------------------+----------------------+
                                           |
                                           v Parses Command
                          +---------------------------------+
                          |        miniredis.command        |
                          |       CommandParser.java        |
                          +----------------+----------------+
                                           |
             +----------+----------+-------+--------+----------+----------+
             |          |          |                |          |          |
             v          v          v                v          v          v
           [SET]      [GET]      [DEL]            [INCR]     [TTL]    [REWRITELOG] ...
             |          |          |                |          |          |
             +----------+----------+-------+--------+----------+----------+
                                           |
                                           +-----------------------+
                                           |                       |
                                           v Mutates State         v Logs Mutations
                          +---------------------------------+     +-------------------+
                          |         miniredis.store         |     | miniredis.        |
                          |           Store.java            |     | persistence       |
                          +----------------+----------------+     | PersistenceLog    |
                          | - ConcurrentHashMap<K, Entry>   |     +---------+---------+
                          | - LruCache (Doubly Linked List) |               |
                          +----------------+----------------+               v
                                           ^                      [ appendonly.log ]
                                           | Sweeps Expired                 ^
                          +----------------+----------------+               |
                          |          ExpiryManager          |               | Replays
                          |   (Background Daemon: 100ms)    |---------------+ on startup
                          +---------------------------------+
```

---

## 📁 Project Structure

```text
miniredis/
├── pom.xml                                    # Maven configuration (JUnit 5, compiler plugin)
├── README.md                                  # Complete project documentation
├── INTERVIEW_NOTES.md                         # Technical interview guide & resume bullets
├── demo_commands.txt                          # Exact script to record demo GIF
├── .gitignore                                 # Ignores build artifacts and runtime logs
└── src/
    ├── main/java/miniredis/
    │   ├── Main.java                          # Server entry point & CLI parameter parsing
    │   ├── server/
    │   │   ├── Server.java                    # TCP ServerSocket listener & thread pool dispatcher
    │   │   └── ClientHandler.java             # Per-client TCP session handler (Runnable)
    │   ├── command/
    │   │   ├── Command.java                   # Command interface (Command Pattern)
    │   │   ├── CommandParser.java             # Command dispatcher & tokenizer
    │   │   ├── SetCommand.java                # SET key value [EX seconds]
    │   │   ├── GetCommand.java                # GET key
    │   │   ├── DelCommand.java                # DEL key
    │   │   ├── ExistsCommand.java             # EXISTS key
    │   │   ├── KeysCommand.java               # KEYS
    │   │   ├── IncrCommand.java               # INCR key (Atomic)
    │   │   ├── DecrCommand.java               # DECR key (Atomic)
    │   │   ├── TtlCommand.java                # TTL key
    │   │   ├── ExpireCommand.java             # EXPIRE key seconds
    │   │   ├── DbSizeCommand.java             # DBSIZE
    │   │   └── RewriteLogCommand.java         # REWRITELOG / COMPACT
    │   ├── store/
    │   │   ├── Store.java                     # Central thread-safe key-value store
    │   │   ├── Entry.java                     # Value wrapper with absolute expiration epoch
    │   │   ├── LruCache.java                  # Custom Doubly Linked List with Sentinel Nodes
    │   │   └── ExpiryManager.java             # Scheduled background cleaner daemon
    │   └── persistence/
    │       └── PersistenceLog.java            # Append-Only File (AOF) manager & replay engine
    └── test/java/miniredis/
        ├── command/
        │   ├── CommandTest.java               # JUnit unit tests for all commands
        │   └── ConcurrentIncrStressTest.java  # JUnit concurrency test (100 threads)
        ├── server/
        │   └── ServerTest.java                # ServerSocket integration tests
        ├── store/
        │   └── LruTest.java                   # JUnit LRU cache capacity & eviction tests
        ├── persistence/
        │   └── PersistenceTest.java           # JUnit AOF replay, compaction & 100k test
        └── manual/
            ├── AllTestsRunner.java            # Standalone test runner (no Maven needed)
            ├── BenchmarkClient.java           # Performance benchmarking client
            ├── ConcurrentIncrDemo.java        # 100-thread race condition demonstration
            ├── ConcurrentLruStressDemo.java   # 20-thread LRU stress test (10,000 ops)
            ├── ConcurrentVerification.java    # Simultaneous multi-client TCP verification
            ├── LruDemo.java                   # Step-by-step LRU eviction walkthrough
            ├── PersistenceDemo.java           # Crash recovery & compaction demo
            ├── TestClient.java                # Interactive TCP client
            └── TtlExpiryDemo.java             # TTL expiration verification
```

---

## 💻 Supported Commands

| Command | Arguments | Description | Return Value / Example |
|---|---|---|---|
| `SET` | `<key> <value> [EX seconds]` | Stores a key-value pair, with optional TTL | `OK` |
| `GET` | `<key>` | Retrieves the value for the key | `Asha` or `(nil)` |
| `DEL` | `<key>` | Deletes the key from the store | `1` (deleted) or `0` (missing) |
| `EXISTS`| `<key>` | Checks if key exists in the database | `1` (exists) or `0` (missing) |
| `KEYS` | *(none)* | Returns all active, non-expired keys | `user:1, city, visits` |
| `INCR` | `<key>` | Atomically increments numeric value by 1 | `1`, `2`, `3` ... |
| `DECR` | `<key>` | Atomically decrements numeric value by 1 | `2`, `1`, `0`, `-1` ... |
| `TTL` | `<key>` | Returns remaining time-to-live in seconds | `28`, `-1` (persist), `-2` (missing) |
| `EXPIRE`| `<key> <seconds>` | Sets a timeout in seconds on an existing key | `1` (success) or `0` (missing) |
| `DBSIZE`| *(none)* | Returns current number of keys in store | `3` |
| `REWRITELOG`| *(none)* | Compacts the AOF log file | `OK` |
| `QUIT` | *(none)* | Closes the client connection gracefully | `BYE` |

---

## ⚡ Performance Benchmarks

Measured on a local workstation using [`BenchmarkClient.java`](file:///C:/Users/HIMAKESH%20PUTHUMBAKU/.gemini/antigravity/scratch/miniredis/src/test/java/miniredis/manual/BenchmarkClient.java) over loopback TCP with a 50% SET / 50% GET workload.

### Scenario A: Pure In-Memory Engine (Persistence OFF)
```text
Clients    | Total Reqs   | Throughput     | Avg Latency | p50        | p95        | p99        | Errors
------------------------------------------------------------------------------------------------------
1          | 2,000        | 24,991  req/s  | 0.04 ms     | 0.03 ms    | 0.07 ms    | 0.11 ms    | 0
10         | 20,000       | 81,357  req/s  | 0.11 ms     | 0.08 ms    | 0.20 ms    | 0.58 ms    | 0
50         | 100,000      | 240,313 req/s  | 0.12 ms     | 0.05 ms    | 0.09 ms    | 0.12 ms    | 0
```

### Scenario B: Durable Persistence (AOF `EVERYSEC`)
```text
Clients    | Total Reqs   | Throughput     | Avg Latency | p50        | p95        | p99        | Errors
------------------------------------------------------------------------------------------------------
1          | 2,000        | 16,273  req/s  | 0.06 ms     | 0.05 ms    | 0.12 ms    | 0.21 ms    | 0
10         | 20,000       | 69,380  req/s  | 0.13 ms     | 0.08 ms    | 0.27 ms    | 0.75 ms    | 0
50         | 100,000      | 149,636 req/s  | 0.33 ms     | 0.05 ms    | 1.11 ms    | 1.26 ms    | 0
```

> **Takeaway:** MiniRedis sustains over **240,000 req/sec** in memory and over **149,000 req/sec** with durable AOF persistence. The durability cost is ~37% throughput overhead to continuously flush mutations to the OS page cache.

---

## 🛠️ Design Decisions

1. **Fixed Worker Thread Pool (`ExecutorService`):** Spawning a thread per client risks memory exhaustion and CPU thrashing under high load. A fixed worker pool (64 threads) bounds system resource usage while serving concurrent clients.
2. **`ConcurrentHashMap.compute()` for Atomicity:** Avoids global locks on reads while providing bucket-level critical sections for `INCR`/`DECR`, eliminating lost updates with zero lock contention across different keys.
3. **Custom Doubly Linked List with Sentinel Nodes for LRU:** Standard `HashMap` has no order ($O(n)$ eviction scan); `LinkedList` has slow lookups ($O(n)$). Combining them gives $O(1)$ lookup and $O(1)$ re-linking. Sentinel `HEAD` and `TAIL` nodes eliminate `null` checks for boundary insertions.
4. **Absolute Timestamps in AOF (`PXAT`):** Logging relative seconds (`EX 30`) causes expiration drift if the server remains offline. Storing absolute epoch milliseconds guarantees deterministic replay.
5. **Atomic Compaction via Temporary Files:** Compacting directly into the active log file risks data corruption on crash. Writing to `appendonly.log.tmp` and using `Files.move(..., ATOMIC_MOVE)` guarantees all-or-nothing replacement.

---

## ⚠️ Limitations & Trade-Offs

* **The 1-Second fsync Window:** In `EVERYSEC` mode, a sudden loss of physical power can lose up to 1 second of buffered writes (matching Redis's default trade-off).
* **Compaction Locking:** To ensure consistency without OS `fork()`, log compaction briefly locks write mutations while snapshotting active keys.
* **$O(n)$ Background Expiration Sweep:** Active cleanup iterates the map rather than using a priority queue (min-heap).
* **Protocol Simplicity:** Uses a line-based text protocol rather than the binary-safe Redis RESP protocol.

---

## 🔮 Future Work

* **Non-Blocking I/O (Java NIO / Netty):** Event-loop reactor pattern to handle 100,000+ idle client connections with minimal threads.
* **Min-Heap for Expiration:** $O(1)$ next-to-expire lookup using a priority queue.
* **RESP Protocol:** Compatibility with official Redis CLI (`redis-cli`) and client drivers (Jedis, Lettuce).
* **Pub/Sub Messaging:** Channel subscriptions and message broadcasting across client sockets.

---

## 🚦 Getting Started

### Prerequisites
* **Java 17** or newer (`java -version`)
* **Maven** (`mvn -version`) *[Optional: pure javac/java is fully supported]*

### 1. Compile the Project
Using Maven:
```bash
mvn compile
```
Or with plain `javac`:
```bash
javac -d target/classes src/main/java/miniredis/Main.java src/main/java/miniredis/server/*.java src/main/java/miniredis/store/*.java src/main/java/miniredis/command/*.java src/main/java/miniredis/persistence/*.java
javac -cp target/classes -d target/test-classes src/test/java/miniredis/manual/*.java
```

### 2. Start the Server
```bash
# Syntax: java -cp target/classes miniredis.Main [maxKeys] [aofEnabled: true|false]
java -cp target/classes miniredis.Main 1000 true
```

### 3. Connect a Client
Using PowerShell:
```powershell
$c = New-Object System.Net.Sockets.TcpClient("127.0.0.1", 6380); $s = $c.GetStream(); $r = New-Object System.IO.StreamReader($s); $w = New-Object System.IO.StreamWriter($s); $w.AutoFlush = $true
$w.WriteLine("SET name Asha"); $r.ReadLine()
$w.WriteLine("GET name"); $r.ReadLine()
```
Or run the interactive test client:
```bash
java -cp "target/classes;target/test-classes" miniredis.manual.TestClient
```

---

## 🧪 Running Tests & Benchmarks

### Run the Complete Test Suite (Without Maven)
```bash
java -cp "target/classes;target/test-classes" miniredis.manual.AllTestsRunner
```

### Run with Maven
```bash
mvn test
```

### Run the Benchmark Suite
```bash
java -cp "target/classes;target/test-classes" miniredis.manual.BenchmarkClient
```
