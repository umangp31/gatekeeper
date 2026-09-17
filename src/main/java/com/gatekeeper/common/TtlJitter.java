package com.gatekeeper.common;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds up-to-maxJitter randomness to a base TTL so keys created together don't expire together
 * and re-stampede. See doc/scope.md §8.1.
 */
public final class TtlJitter {

    private TtlJitter() {
    }

    public static Duration jitter(Duration base, Duration maxJitter) {
        long jitterMillis = ThreadLocalRandom.current().nextLong(maxJitter.toMillis() + 1);
        return base.plusMillis(jitterMillis);
    }
}
