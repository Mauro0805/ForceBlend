package com.mauro0805.forceblend.config;

import java.util.List;
import java.util.Objects;

/**
 * A loaded configuration and any recoverable problems found while parsing it.
 */
public record ConfigLoadResult(ForceBlendConfig config, List<String> warnings) {
    public ConfigLoadResult {
        Objects.requireNonNull(config, "config");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
