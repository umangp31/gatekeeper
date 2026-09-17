package com.gatekeeper.flags;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic percentage rollout: same user + same flag always lands in the same bucket,
 * independently of every other flag. See doc/scope.md §7.2.
 */
@Component
public class RolloutBucketer {

    public boolean isInRollout(String flagKey, UUID userId, int rolloutPercentage) {
        return bucket(flagKey, userId) < rolloutPercentage;
    }

    /** 0-99, stable across restarts/instances; hashing flagKey+userId together decorrelates buckets across flags. */
    public int bucket(String flagKey, UUID userId) {
        String input = flagKey + ":" + userId;
        int hash = Hashing.murmur3_32_fixed().hashString(input, StandardCharsets.UTF_8).asInt();
        return Math.floorMod(hash, 100);
    }
}
