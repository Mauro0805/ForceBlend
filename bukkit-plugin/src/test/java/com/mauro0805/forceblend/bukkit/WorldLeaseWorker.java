package com.mauro0805.forceblend.bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

public final class WorldLeaseWorker {
    private WorldLeaseWorker() {}

    public static void main(String[] args) {
        try (WorldLeases leases = WorldLeases.acquire(Arrays.stream(args).map(Path::of).toList());
             var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.println("READY");
            String command;
            while ((command = input.readLine()) != null && !"STOP".equals(command)) {
                if (!"CHECK".equals(command)) throw new IllegalArgumentException("Invalid lease command");
                leases.validate();
                System.out.println("VALID");
            }
        } catch (Exception exception) {
            System.out.println("REFUSED: " + exception);
            System.exit(1);
        }
    }
}
