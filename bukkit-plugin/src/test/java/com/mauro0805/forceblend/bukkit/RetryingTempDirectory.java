package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public final class RetryingTempDirectory implements TempDirFactory, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(RetryingTempDirectory.class);

    @Override
    public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context) throws IOException {
        Path root = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        Path directory = Files.createTempDirectory(root, "fixture-");
        paths(context).add(directory);
        return directory;
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Path root = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        for (Path directory : paths(context)) {
            if (!directory.normalize().startsWith(root) || directory.equals(root)) {
                throw new IOException("Refusing cleanup outside the test temporary root");
            }
            for (int attempt = 0; ; attempt++) {
                try {
                    if (Files.exists(directory)) Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                        @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }
                        @Override public FileVisitResult postVisitDirectory(Path path, IOException failure) throws IOException {
                            if (failure != null) throw failure;
                            Files.delete(path);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    if (attempt > 0) context.publishReportEntry("cleanup-retries", Integer.toString(attempt));
                    break;
                } catch (IOException failure) {
                    if (attempt == 7) throw failure;
                    Thread.sleep(25L * (attempt + 1));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Path> paths(ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent("paths", key -> new ArrayList<Path>(), List.class);
    }
}
