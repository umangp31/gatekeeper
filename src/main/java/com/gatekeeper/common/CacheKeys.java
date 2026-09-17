package com.gatekeeper.common;

import java.util.UUID;

/** Centralizes the "gk:" key namespace so every cache consumer builds keys the same way. */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static String permissions(UUID tenantId, UUID userId) {
        return "gk:perm:" + tenantId + ":" + userId;
    }

    public static String flag(UUID tenantId, String flagKey) {
        return "gk:flag:" + tenantId + ":" + flagKey;
    }

    public static String lock(String targetKey) {
        return "gk:lock:" + targetKey;
    }
}
