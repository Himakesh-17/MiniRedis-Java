package miniredis.manual;

import miniredis.command.CommandParser;
import miniredis.persistence.PersistenceLog;
import miniredis.store.Store;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Demonstrates AOF persistence:
 * a) Writes data to AOF (SET, SET EX, INCR, DEL).
 * b) Replays into a brand new Store and proves state is restored.
 * c) Demonstrates already-expired keys are skipped on replay.
 * d) Demonstrates corrupted last lines are safely skipped.
 * e) Demonstrates REWRITELOG shrinks the log file.
 */
public class PersistenceDemo {
    private static final String TEST_LOG = "target/test_appendonly.log";

    public static void main(String[] args) throws Exception {
        System.out.println("=== MiniRedis AOF Persistence Demo ===\n");

        File logFile = new File(TEST_LOG);
        if (logFile.exists()) {
            logFile.delete();
        }

        // Part A: Execute write commands on Store 1
        System.out.println("Part A: Executing write commands on Store 1...");
        Store store1 = new Store(100);
        PersistenceLog aof1 = new PersistenceLog(TEST_LOG, PersistenceLog.FsyncPolicy.ALWAYS);
        CommandParser parser1 = new CommandParser(store1, aof1);

        parser1.parseAndExecute("SET a 1");
        parser1.parseAndExecute("SET b 2 EX 60");
        parser1.parseAndExecute("INCR visits");
        parser1.parseAndExecute("DEL a");

        System.out.println("   Store 1 DBSIZE: " + parser1.parseAndExecute("DBSIZE"));
        System.out.println("   Store 1 KEYS:   " + parser1.parseAndExecute("KEYS"));
        System.out.println("   Written to AOF (" + countLines(logFile) + " lines):");
        printLogContents(logFile);
        aof1.close();

        // Part B: Replay into a completely NEW empty Store 2
        System.out.println("\nPart B: Replaying AOF into a brand NEW Store 2...");
        Store store2 = new Store(100);
        PersistenceLog aof2 = new PersistenceLog(TEST_LOG, PersistenceLog.FsyncPolicy.ALWAYS);
        int replayed = aof2.replay(store2);
        CommandParser parser2 = new CommandParser(store2, aof2);

        System.out.println("   Replayed lines: " + replayed);
        System.out.println("   Store 2 'a' (deleted):  " + parser2.parseAndExecute("GET a") + " (expected: (nil))");
        System.out.println("   Store 2 'b' (restored): " + parser2.parseAndExecute("GET b") + " (expected: 2)");
        System.out.println("   Store 2 'b' TTL:        " + parser2.parseAndExecute("TTL b") + "s (expected: ~59-60s)");
        System.out.println("   Store 2 'visits':       " + parser2.parseAndExecute("GET visits") + " (expected: 1)");

        // Part C: Skip already-expired keys on replay
        System.out.println("\nPart C: Testing that already-expired keys are skipped on replay...");
        parser2.parseAndExecute("SET otp 9999 EX 1");
        System.out.println("   Waiting 1.2s for 'otp' to expire...");
        Thread.sleep(1200);

        Store store3 = new Store(100);
        aof2.replay(store3);
        CommandParser parser3 = new CommandParser(store3);
        System.out.println("   Store 3 'otp' after replay: " + parser3.parseAndExecute("GET otp") + " (expected: (nil))");

        // Part D: Corrupted / half-written last line
        System.out.println("\nPart D: Testing resilience against a corrupted/truncated last line...");
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write("SET corrupt_key_half_written_val\n"); // Malformed line
            fw.write("INCR_BROKEN\n");                       // Malformed line
        }

        Store store4 = new Store(100);
        int replayed4 = aof2.replay(store4);
        System.out.println("   Replay finished safely without crashing! Valid lines replayed: " + replayed4);

        // Part E: REWRITELOG compaction
        System.out.println("\nPart E: Testing REWRITELOG compaction...");
        for (int i = 0; i < 20; i++) {
            parser2.parseAndExecute("INCR visits"); // Appends 20 redundant INCR lines
        }
        int linesBefore = countLines(logFile);
        System.out.println("   Lines in AOF before compaction: " + linesBefore);

        String compactResp = parser2.parseAndExecute("REWRITELOG");
        int linesAfter = countLines(logFile);
        System.out.println("   REWRITELOG response: " + compactResp);
        System.out.println("   Lines in AOF after compaction:  " + linesAfter + " (compacted to snapshot!)");
        System.out.println("   Compacted AOF contents:");
        printLogContents(logFile);

        aof2.close();
        System.out.println("\n=== AOF Persistence Demo Completed Successfully ===");
    }

    private static int countLines(File file) throws IOException {
        return Files.readAllLines(file.toPath()).size();
    }

    private static void printLogContents(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        for (String line : lines) {
            System.out.println("      | " + line);
        }
    }
}
