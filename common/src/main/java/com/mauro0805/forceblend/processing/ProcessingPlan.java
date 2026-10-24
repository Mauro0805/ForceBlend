package com.mauro0805.forceblend.processing;

import com.mauro0805.forceblend.version.BlendingProfile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of a read-only world analysis. The same plan is passed to {@link WorldProcessor#apply}.
 */
public final class ProcessingPlan {
    private final String levelId;
    private final Path overworldDirectory;
    private final Path regionDirectory;
    private final BlendingProfile profile;
    private final ProcessingOptions options;
    private final ProcessingPlanSummary summary;
    private final Duration analysisDuration;
    private final List<PlannedRegion> regions;
    private final int alreadyPreparedChunks;

    ProcessingPlan(
        String levelId,
        Path overworldDirectory,
        Path regionDirectory,
        BlendingProfile profile,
        ProcessingOptions options,
        ProcessingPlanSummary summary,
        Duration analysisDuration,
        List<PlannedRegion> regions,
        int alreadyPreparedChunks
    ) {
        this.levelId = Objects.requireNonNull(levelId, "levelId");
        this.overworldDirectory = Objects.requireNonNull(overworldDirectory, "overworldDirectory");
        this.regionDirectory = Objects.requireNonNull(regionDirectory, "regionDirectory");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.options = Objects.requireNonNull(options, "options");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.analysisDuration = Objects.requireNonNull(analysisDuration, "analysisDuration");
        this.regions = List.copyOf(regions);
        if (alreadyPreparedChunks < 0) {
            throw new IllegalArgumentException("Already-prepared chunks must not be negative");
        }
        this.alreadyPreparedChunks = alreadyPreparedChunks;
    }

    static ProcessingPlan empty(
        String levelId,
        Path overworldDirectory,
        Path regionDirectory,
        BlendingProfile profile,
        ProcessingOptions options
    ) {
        return new ProcessingPlan(
            levelId,
            overworldDirectory,
            regionDirectory,
            profile,
            options,
            new ProcessingPlanSummary(0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L),
            Duration.ZERO,
            List.of(),
            0
        );
    }

    public String levelId() {
        return levelId;
    }

    public Path overworldDirectory() {
        return overworldDirectory;
    }

    public BlendingProfile profile() {
        return profile;
    }

    public ProcessingOptions options() {
        return options;
    }

    public ProcessingPlanSummary summary() {
        return summary;
    }

    public Duration analysisDuration() {
        return analysisDuration;
    }

    /**
     * Initial estimate derived from this machine's measured analysis time and the selected region/chunk ratio.
     */
    public Optional<Duration> estimatedApplyDuration() {
        if (summary.selectedChunks() == 0) {
            return Optional.of(Duration.ZERO);
        }
        if (analysisDuration.isZero() || summary.analyzedRegionBytes() == 0 || summary.discoveredChunks() == 0) {
            return Optional.empty();
        }

        double byteRatio = (double) summary.selectedRegionBytes() / summary.analyzedRegionBytes();
        double chunkRatio = (double) summary.selectedChunks() / summary.discoveredChunks();
        double workRatio = Math.max(byteRatio, chunkRatio);
        double estimatedNanos = analysisDuration.toNanos() * workRatio;
        long boundedNanos = estimatedNanos >= Long.MAX_VALUE
            ? Long.MAX_VALUE
            : Math.max(1_000_000L, (long) estimatedNanos);
        return Optional.of(Duration.ofNanos(boundedNanos));
    }

    Path regionDirectory() {
        return regionDirectory;
    }

    List<PlannedRegion> regions() {
        return regions;
    }

    int alreadyPreparedChunks() {
        return alreadyPreparedChunks;
    }
}
