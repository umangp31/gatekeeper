package com.gatekeeper.tenancy;

import java.util.UUID;

/**
 * Per-request tenant id, propagated via a ThreadLocal. Must always be cleared in a `finally`
 * block by whoever sets it (TenantFilter) — a pooled thread that leaks a stale value would
 * apply the wrong tenant filter to the next, unrelated request. See doc/scope.md §5.1.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static UUID require() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new TenantContextMissingException();
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
