package com.mauro0805.forceblend.bukkit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class WorldLeaseProcess implements AutoCloseable {
    private final Process process;
    private final BufferedReader output;
    private final BufferedWriter input;

    private WorldLeaseProcess(Process process) {
        this.process = process;
        output = process.inputReader(StandardCharsets.UTF_8);
        input = process.outputWriter(StandardCharsets.UTF_8);
    }

    static WorldLeaseProcess acquire(List<Path> worlds) throws IOException {
        String code;
        try {
            code = Path.of(WorldLeaseWorker.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    + File.pathSeparator
                    + Path.of(WorldLeases.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception exception) {
            throw new IOException("Cannot locate the lease worker", exception);
        }
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xms16m", "-Xmx64m", "-cp", code, WorldLeaseWorker.class.getName()));
        worlds.forEach(world -> command.add(world.toString()));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        for (String name : List.of("_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(name);
        WorldLeaseProcess lease = new WorldLeaseProcess(builder.start());
        try {
            lease.expect("READY");
            return lease;
        } catch (IOException exception) {
            try { lease.close(); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    void validate() throws IOException {
        input.write("CHECK\n");
        input.flush();
        expect("VALID");
    }

    private void expect(String expected) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try {
            while (System.nanoTime() < deadline) {
                if (output.ready()) {
                    String line = output.readLine();
                    if (expected.equals(line)) return;
                    throw new IOException("Lease worker " + line);
                }
                if (!process.isAlive()) throw new IOException("Lease worker exited before " + expected);
                Thread.sleep(20);
            }
            throw new IOException("Lease worker timed out before " + expected);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for the lease worker", exception);
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            if (process.isAlive()) {
                try {
                    input.write("STOP\n");
                    input.flush();
                } catch (IOException exception) { failure = exception; }
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IOException("Lease worker did not exit");
                throw new IOException("Lease worker required forced termination");
            }
            if (process.exitValue() != 0) throw new IOException("Lease worker exited unsuccessfully");
            if (failure != null) throw failure;
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while closing the lease worker", exception);
        } finally {
            try { input.close(); } finally { output.close(); }
        }
    }
}
