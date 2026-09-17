package com.gatekeeper.flags.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.gatekeeper.flags.FeatureFlag;
import java.util.UUID;

public record FlagResponse(
        @Schema(description = "Server-generated flag id")
        UUID id,
        @Schema(description = "Unique key of the flag within the tenant", example = "new-checkout")
        String flagKey,
        @Schema(description = "Global on/off switch. When false the flag is OFF for everyone except whitelisted users or environment overrides", example = "false")
        boolean enabled,
        @Schema(description = "Percentage (0-100) of users that see the flag ON when enabled; bucketing is deterministic per user", example = "50")
        int rolloutPercentage,
        @Schema(description = "Free-text description")
        String description,
        @Schema(description = "Optimistic-lock version; send it back unchanged in PUT /flags/{key}", example = "3")
        long version) {

    public static FlagResponse from(FeatureFlag flag) {
        return new FlagResponse(flag.getId(), flag.getFlagKey(), flag.isEnabled(), flag.getRolloutPercentage(),
                flag.getDescription(), flag.getVersion());
    }
}
