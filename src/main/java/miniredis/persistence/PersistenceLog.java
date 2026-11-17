package miniredis.persistence;

import miniredis.store.Entry;
import miniredis.store.Store;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-Only File (AOF) persistence engine.
 * Records all write mutations (SET, DEL, INCR, DECR, EXPIRE) to an append-only disk log.
 * Replays the log on server boot to rebuild state, and supports atomic log compaction.
 */
public class PersistenceLog {

    public enum FsyncPolicy {
        ALWAYS,   // fsync on every write: safest, slowest
        EVERYSEC, // flush on every write, fsync in background once per second: fast, <=1s loss window
        OFF       // let the OS handle flushing: fastest, least safe
    }

    public static final String DEFAULT_LOG_PATH = "appendonly.log";

    private final File logFile;
    private final FsyncPolicy policy;
    private final ReentrantLock lock = new ReentrantLock();

    private FileOutputStream fileOutputStream;
    private BufferedWriter writer;
    private ScheduledExecutorService fsyncExecutor;

    public PersistenceLog(String logFilePath, FsyncPolicy policy) throws IOException {
        this.logFile = new File(logFilePath);
        this.policy = policy;
        initWriter();

        if (policy == FsyncPolicy.EVERYSEC) {
            this.fsyncExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "miniredis-aof-fsync");
                t.setDaemon(true);
                return t;
            });
            this.fsyncExecutor.scheduleAtFixedRate(this::syncToDisk, 1, 1, TimeUnit.SECONDS);
        }
    }

    public PersistenceLog(String logFilePath) throws IOException {
        this(logFilePath, FsyncPolicy.EVERYSEC);
    }

    public PersistenceLog() throws IOException {
        this(DEFAULT_LOG_PATH, FsyncPolicy.EVERYSEC);
    }

    private void initWriter() throws IOException {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        // Open file in append mode (true)
        this.fileOutputStream = new FileOutputStream(logFile, true);
        this.writer = new BufferedWriter(new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8));
    }

    /**
     * Appends a SET mutation to the log.
     * Uses absolute epoch timestamp (PXAT) to ensure TTL does not reset on restart.
     */
    public void logSet(String key, String value, Long expiresAtMillis) {
        lock.lock();
        try {
            if (expiresAtMillis != null) {
                writeLine("SET " + key + " PXAT " + expiresAtMillis + " " + value);
            } else {
                writeLine("SET " + key + " NONE " + value);
            }
        } finally {
            lock.unlock();
        }
    }

    public void logDel(String key) {
        lock.lock();
        try {
            writeLine("DEL " + key);
        } finally {
            lock.unlock();
        }
    }

    public void logIncr(String key) {
        lock.lock();
        try {
            writeLine("INCR " + key);
        } finally {
            lock.unlock();
        }
    }

    public void logDecr(String key) {
        lock.lock();
        try {
            writeLine("DECR " + key);
        } finally {
            lock.unlock();
        }
    }

    public void logExpire(String key, long expiresAtMillis) {
        lock.lock();
        try {
            writeLine("EXPIRE " + key + " " + expiresAtMillis);
        } finally {
            lock.unlock();
        }
    }

    private void writeLine(String line) {
        try {
            writer.write(line);
            writer.newLine();
            writer.flush(); // Flush from Java buffer to OS buffer

            if (policy == FsyncPolicy.ALWAYS) {
                fileOutputStream.getChannel().force(false); // Immediate physical disk write
            }
        } catch (IOException e) {
            System.err.println("[Persistence] Error writing to AOF: " + e.getMessage());
        }
    }

    private void syncToDisk() {
        lock.lock();
        try {
            if (fileOutputStream != null && fileOutputStream.getChannel().isOpen()) {
                fileOutputStream.getChannel().force(false);
            }
        } catch (IOException ignored) {
        } finally {
            lock.unlock();
        }
    }

    /**
     * Replays the AOF log from disk to rebuild the Store state on startup.
     * Corrupted or half-written lines at the end are safely skipped.
     * Expired keys are skipped so memory is not polluted.
     *
     * @return the number of replayed commands
     */
    public int replay(Store store) {
        lock.lock();
        try {
            if (!logFile.exists() || logFile.length() == 0) {
                return 0;
            }

            int replayedCount = 0;
            long now = System.currentTimeMillis();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    try {
                        if (applyReplayLine(line, store, now)) {
                            replayedCount++;
                        }
                    } catch (Exception e) {
                        System.err.println("[Persistence] Warning: Skipping corrupted AOF line: \"" + line + "\" (" + e.getMessage() + ")");
                    }
                }
            } catch (IOException e) {
                System.err.println("[Persistence] Error reading AOF during replay: " + e.getMessage());
            }

            return replayedCount;
        } finally {
            lock.unlock();
        }
    }

    private boolean applyReplayLine(String line, Store store, long now) {
        String[] parts = line.split("\\s+", 4);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Incomplete command");
        }

        String command = parts[0].toUpperCase();
        String key = parts[1];

        switch (command) {
            case "SET":
                if (parts.length < 4) {
                    throw new IllegalArgumentException("Invalid SET format");
                }
                String expiryType = parts[2];
                String value = parts[3];

                if ("PXAT".equalsIgnoreCase(expiryType)) {
                    // parts[2] is "PXAT", parts[3] starts with millis then value
                    String[] pxatParts = parts[3].split("\\s+", 2);
                    if (pxatParts.length < 2) {
                        throw new IllegalArgumentException("Invalid PXAT format");
                    }
                    long expiresAt = Long.parseLong(pxatParts[0]);
                    String realValue = pxatParts[1];

                    if (now >= expiresAt) {
                        return false; // Key already expired before restart; skip!
                    }
                    long remainingSeconds = (expiresAt - now + 999) / 1000;
                    store.put(key, realValue, remainingSeconds);
                } else {
                    store.put(key, value);
                }
                return true;

            case "DEL":
                store.delete(key);
                return true;

            case "INCR":
                store.incrementBy(key, 1);
                return true;

            case "DECR":
                store.incrementBy(key, -1);
                return true;

            case "EXPIRE":
                if (parts.length < 3) {
                    throw new IllegalArgumentException("Invalid EXPIRE format");
                }
                long expiresAt = Long.parseLong(parts[2]);
                if (now >= expiresAt) {
                    store.delete(key);
                } else {
                    long remainingSeconds = (expiresAt - now + 999) / 1000;
                    store.expire(key, remainingSeconds);
                }
                return true;

            default:
                throw new IllegalArgumentException("Unknown AOF command: " + command);
        }
    }

    /**
     * Compacts the log by writing only current active keys to a temporary file
     * and atomically renaming it over the existing log file.
     *
     * @param store the current store state
     * @return true if compaction succeeded
     */
    public boolean compact(Store store) {
        lock.lock();
        try {
            File tempFile = new File(logFile.getAbsolutePath() + ".tmp");
            Map<String, Entry> activeEntries = store.getActiveEntries();

            try (BufferedWriter tempWriter = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {

                for (Map.Entry<String, Entry> e : activeEntries.entrySet()) {
                    String key = e.getKey();
                    Entry entry = e.getValue();
                    if (entry.isExpired()) {
                        continue;
                    }
                    String line;
                    if (entry.hasExpiry()) {
                        line = "SET " + key + " PXAT " + entry.getExpiresAtMillis() + " " + entry.getValue();
                    } else {
                        line = "SET " + key + " NONE " + entry.getValue();
                    }
                    tempWriter.write(line);
                    tempWriter.newLine();
                }
                tempWriter.flush();
            }

            // Close existing writer so Windows releases the file handle
            closeWriter();

            // Atomically replace old log with compacted log
            try {
                Files.move(tempFile.toPath(), logFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // Fallback to standard replace if atomic move is unsupported on file system
                Files.move(tempFile.toPath(), logFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Re-open fresh append writer on compacted file
            initWriter();
            return true;
        } catch (IOException e) {
            System.err.println("[Persistence] Compaction failed: " + e.getMessage());
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void closeWriter() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        } catch (IOException ignored) {
        }
    }

    public void close() {
        lock.lock();
        try {
            if (fsyncExecutor != null) {
                fsyncExecutor.shutdown();
                try {
                    fsyncExecutor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }
            syncToDisk();
            closeWriter();
        } finally {
            lock.unlock();
        }
    }

    public File getLogFile() {
        return logFile;
    }
}
