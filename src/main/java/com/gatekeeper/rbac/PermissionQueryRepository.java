package com.gatekeeper.rbac;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Resolves a user's effective permissions via the recursive role-hierarchy CTE — this is the
 * contract query from doc/scope.md §6.2, verbatim. It is native SQL, so it deliberately binds
 * :tenantId explicitly rather than relying on the Hibernate @Filter (which does not apply to
 * native queries — see doc/scope.md §5.2).
 */
@Repository
public class PermissionQueryRepository {

    private static final String EFFECTIVE_PERMISSIONS_SQL = """
            WITH RECURSIVE reachable_roles(role_id, depth) AS (
                SELECT ur.role_id, 0
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = :userId
                  AND r.tenant_id = :tenantId

                UNION

                SELECT rh.parent_role_id, rr.depth + 1
                FROM role_hierarchy rh
                JOIN reachable_roles rr ON rr.role_id = rh.child_role_id
                WHERE rr.depth < 32
            )
            SELECT DISTINCT p.code
            FROM reachable_roles rr
            JOIN role_permissions rp ON rp.role_id = rr.role_id
            JOIN permissions p       ON p.id = rp.permission_id
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public Set<String> findEffectivePermissionCodes(UUID userId, UUID tenantId) {
        List<String> codes = entityManager.createNativeQuery(EFFECTIVE_PERMISSIONS_SQL)
                .setParameter("userId", userId)
                .setParameter("tenantId", tenantId)
                .getResultList();
        // LinkedHashSet, not Set.copyOf(): the immutable JDK collection Set.copyOf() returns is
        // an internal, non-public class (ImmutableCollections$SetN) that Jackson's polymorphic
        // typing (used by the Redis cache serializer, T-22) cannot deserialize back.
        return new LinkedHashSet<>(codes);
    }
}
