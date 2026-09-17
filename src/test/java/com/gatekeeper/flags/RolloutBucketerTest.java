package com.gatekeeper.flags;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RolloutBucketerTest {

    private final RolloutBucketer bucketer = new RolloutBucketer();

    @Test
    void sameUserSameFlagIsStableAcrossCalls() {
        UUID userId = UUID.randomUUID();
        int first = bucketer.bucket("checkout-v2", userId);
        int second = bucketer.bucket("checkout-v2", userId);
        int third = bucketer.bucket("checkout-v2", userId);

        assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    @Test
    void sameUserIsNotAlwaysInTheSameBucketAcrossDifferentFlags() {
        UUID userId = UUID.randomUUID();
        long distinctBuckets = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> bucketer.bucket("flag-" + i, userId))
                .distinct()
                .count();

        // With 20 independent flag keys, a fixed user landing in the exact same bucket every
        // time would indicate the hash isn't actually mixing flagKey in.
        assertThat(distinctBuckets).isGreaterThan(1);
    }

    @Test
    void distributionIsApproximatelyUniformAtScale() {
        int totalUsers = 10_000;
        int rolloutPercentage = 30;
        long inRollout = java.util.stream.IntStream.range(0, totalUsers)
                .filter(i -> bucketer.isInRollout("big-rollout", UUID.randomUUID(), rolloutPercentage))
                .count();

        double actualPercentage = (inRollout * 100.0) / totalUsers;
        assertThat(actualPercentage).isBetween(27.0, 33.0);
    }

    @Test
    void bucketIsAlwaysWithinZeroToNinetyNine() {
        for (int i = 0; i < 1000; i++) {
            int bucket = bucketer.bucket("range-check", UUID.randomUUID());
            assertThat(bucket).isBetween(0, 99);
        }
    }
}
