package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorkerSupervisionTest {
    @Test
    void forwardsUtf8AndRejectsNonzeroExit() throws Exception {
        var lines = new CopyOnWriteArrayList<String>();
        Process success = child("success");
        OfflinePreparation.supervise(success, lines::add);
        assertEquals(List.of("ready", "Água 世界"), lines);
        assertFalse(success.isAlive());
        Process failure = child("failure");
        assertThrows(IOException.class, () -> OfflinePreparation.supervise(failure, line -> {}));
        assertFalse(failure.isAlive());
    }

    @Test
    void interruptionKillsSilentChildAndPreservesInterrupt() throws Exception {
        Process child = child("wait");
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread owner = new Thread(() -> {
            try { OfflinePreparation.supervise(child, line -> ready.countDown()); }
            catch (Throwable exception) { failure.set(exception); }
            finally { interrupted.set(Thread.currentThread().isInterrupted()); }
        });
        try {
            owner.start();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            owner.interrupt();
            owner.join(10_000);
            assertFalse(owner.isAlive());
            assertInstanceOf(IOException.class, failure.get());
            assertTrue(interrupted.get());
            assertFalse(child.isAlive());
        } finally {
            child.destroyForcibly();
            child.waitFor(5, TimeUnit.SECONDS);
            owner.interrupt();
            owner.join(5_000);
        }
    }

    @Test
    void outputConsumerFailureTerminatesWorker() throws Exception {
        Process child = child("wait");
        assertThrows(IOException.class, () -> OfflinePreparation.supervise(child, line -> {
            throw new IllegalStateException("failed logger");
        }));
        assertFalse(child.isAlive());
    }

    private Process child(String mode) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String classes = Path.of(WorkerFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Xmx64m", "-Dfile.encoding=UTF-8", "-cp", classes, WorkerFixture.class.getName(), mode)
                .redirectErrorStream(true).start();
    }

    public static class WorkerFixture {
        public static void main(String[] args) throws Exception {
            PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
            output.println("ready");
            if ("wait".equals(args[0])) Thread.sleep(60_000);
            else if ("failure".equals(args[0])) System.exit(1);
            else output.println("Água 世界");
        }
    }
}
