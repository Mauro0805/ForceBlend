package com.mauro0805.forceblend.processing;

import com.mauro0805.forceblend.version.BlendingProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

public final class WorldProcessor {
    private static final String REGION_DIRECTORY_NAME = "region";

    private WorldProcessor() {
    }

    public static ProcessingReport process(
        String levelId,
        Path overworldDirectory,
        BlendingProfile profile
    ) throws IOException {
        return process(
            levelId,
            overworldDirectory,
            profile,
            ProcessingOptions.DEFAULT,
            ProcessingProgressListener.NO_OP
        );
    }

    public static ProcessingReport process(
        String levelId,
        Path overworldDirectory,
        BlendingProfile profile,
        ProcessingOptions options,
        ProcessingProgressListener progressListener
    ) throws IOException {
        return apply(analyze(levelId, overworldDirectory, profile, options, progressListener), progressListener);
    }

    public static ProcessingPlan analyze(
        String levelId,
        Path overworldDirectory,
        BlendingProfile profile,
        ProcessingOptions options
    ) throws IOException {
        return analyze(levelId, overworldDirectory, profile, options, ProcessingProgressListener.NO_OP);
    }

    public static ProcessingPlan analyze(
        String levelId,
        Path overworldDirectory,
        BlendingProfile profile,
        ProcessingOptions options,
        ProcessingProgressListener progressListener
    ) throws IOException {
        Objects.requireNonNull(levelId, "levelId");
        Objects.requireNonNull(overworldDirectory, "overworldDirectory");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(progressListener, "progressListener");

        Path normalizedOverworld = overworldDirectory.toAbsolutePath().normalize();
        Path regionDirectory = normalizedOverworld.resolve(REGION_DIRECTORY_NAME).normalize();
        if (!regionDirectory.startsWith(normalizedOverworld)) {
            throw new IOException("Region directory resolves outside the selected Overworld: " + regionDirectory);
        }
        if (Files.isSymbolicLink(regionDirectory)) {
            throw new IOException("Symbolic links are not allowed for the region directory: " + regionDirectory);
        }
        if (Files.exists(regionDirectory, LinkOption.NOFOLLOW_LINKS)
            && !Files.isDirectory(regionDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The Overworld region path is not a directory: " + regionDirectory);
        }

        return RegionScanner.analyze(
            levelId,
            normalizedOverworld,
            regionDirectory,
            profile,
            options,
            progressListener
        );
    }

    public static ProcessingReport apply(ProcessingPlan plan) throws IOException {
        return apply(plan, ProcessingProgressListener.NO_OP);
    }

    public static ProcessingReport apply(
        ProcessingPlan plan,
        ProcessingProgressListener progressListener
    ) throws IOException {
        return RegionScanner.apply(plan, progressListener);
    }
}
