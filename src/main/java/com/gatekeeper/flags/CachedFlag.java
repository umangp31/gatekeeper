package com.gatekeeper.flags;

import java.util.UUID;

/** Cache-safe projection of FeatureFlag. exists=false is the negative-cache tombstone (§8.5). */
public record CachedFlag(boolean exists, UUID id, boolean enabled, int rolloutPercentage) {

    public static CachedFlag of(FeatureFlag flag) {
        return new CachedFlag(true, flag.getId(), flag.isEnabled(), flag.getRolloutPercentage());
    }

    public static CachedFlag missing() {
        return new CachedFlag(false, null, false, 0);
    }
}
