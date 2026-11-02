package com.mauro0805.forceblend.bukkit;

import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.config.RunMode;
import java.nio.file.Path;
import java.util.Arrays;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class OfflinePreparationWorker {
    private OfflinePreparationWorker() {}

    public static void main(String[] args) {
        PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Thread owner = new Thread(() -> {
            try {
                while (System.in.read() != -1) { }
            } catch (Exception exception) {
                System.err.println("Owner pipe failed: " + exception);
            }
            // No writes may continue after the server disappears. Treat owner loss like process interruption.
            Runtime.getRuntime().halt(2);
        }, "forceblend-owner-watch");
        owner.setDaemon(true);
        owner.start();
        int status = 0;
        try {
            var targets = Arrays.stream(args).skip(7).map(Path::of).toList();
            var config = new ForceBlendConfig(true, true, Boolean.parseBoolean(args[5]),
                    Long.parseLong(args[2]), Integer.parseInt(args[3]), RunMode.valueOf(args[4]));
            // Locks and all mutating I/O live in this process so worker death cannot leave an unlocked writer.
            try (WorldLeases leases = WorldLeases.acquire(targets)) {
                OfflinePreparation.execute(targets, Path.of(args[0]), args[1], config, output::println, leases,
                        Boolean.parseBoolean(args[6]));
            }
        } catch (Exception exception) {
            output.println("Offline worker refused: " + exception);
            status = 1;
        }
        System.exit(status);
    }
}
