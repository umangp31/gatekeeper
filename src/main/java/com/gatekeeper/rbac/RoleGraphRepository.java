package com.gatekeeper.rbac;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Manages the role_hierarchy and role_permissions join tables directly (native SQL, no
 * composite-key entities) — mirrors PermissionQueryRepository's approach. See doc/scope.md §6.
 */
@Repository
public class RoleGraphRepository {

    /**
     * Ancestors of :startRoleId, walking UP (child -> parent) exactly like the effective-
     * permissions CTE (§6.2). Used for cycle detection when adding a hierarchy edge.
     */
    private static final String ANCESTORS_SQL = """
            WITH RECURSIVE ancestors(role_id, depth) AS (
                SELECT :startRoleId, 0

                UNION

                SELECT rh.parent_role_id, a.depth + 1
                FROM role_hierarchy rh
                JOIN ancestors a ON rh.child_role_id = a.role_id
                WHERE a.depth < 32
            )
            SELECT EXISTS (SELECT 1 FROM ancestors WHERE role_id = :targetRoleId AND depth > 0)
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /** True if targetRoleId is reachable by walking up the ancestor chain from startRoleId. */
    public boolean isAncestor(UUID startRoleId, UUID targetRoleId) {
        Object result = entityManager.createNativeQuery(ANCESTORS_SQL)
                .setParameter("startRoleId", startRoleId)
                .setParameter("targetRoleId", targetRoleId)
                .getSingleResult();
        return (Boolean) result;
    }

    public void addHierarchyEdge(UUID parentRoleId, UUID childRoleId) {
        entityManager.createNativeQuery(
                        "INSERT INTO role_hierarchy (parent_role_id, child_role_id) VALUES (:parentId, :childId)")
                .setParameter("parentId", parentRoleId)
                .setParameter("childId", childRoleId)
                .executeUpdate();
    }

    public void removeHierarchyEdge(UUID parentRoleId, UUID childRoleId) {
        entityManager.createNativeQuery(
                        "DELETE FROM role_hierarchy WHERE parent_role_id = :parentId AND child_role_id = :childId")
                .setParameter("parentId", parentRoleId)
                .setParameter("childId", childRoleId)
                .executeUpdate();
    }

    public void addPermission(UUID roleId, UUID permissionId) {
        entityManager.createNativeQuery(
                        "INSERT INTO role_permissions (role_id, permission_id) VALUES (:roleId, :permissionId) "
                                + "ON CONFLICT DO NOTHING")
                .setParameter("roleId", roleId)
                .setParameter("permissionId", permissionId)
                .executeUpdate();
    }

    public void removePermission(UUID roleId, UUID permissionId) {
        entityManager.createNativeQuery(
                        "DELETE FROM role_permissions WHERE role_id = :roleId AND permission_id = :permissionId")
                .setParameter("roleId", roleId)
                .setParameter("permissionId", permissionId)
                .executeUpdate();
    }

    public void assignRoleToUser(UUID userId, UUID roleId) {
        entityManager.createNativeQuery(
                        "INSERT INTO user_roles (user_id, role_id) VALUES (:userId, :roleId) ON CONFLICT DO NOTHING")
                .setParameter("userId", userId)
                .setParameter("roleId", roleId)
                .executeUpdate();
    }

    public void revokeRoleFromUser(UUID userId, UUID roleId) {
        entityManager.createNativeQuery(
                        "DELETE FROM user_roles WHERE user_id = :userId AND role_id = :roleId")
                .setParameter("userId", userId)
                .setParameter("roleId", roleId)
                .executeUpdate();
    }

    private static final String DESCENDANTS_SQL = """
            WITH RECURSIVE descendants(role_id, depth) AS (
                SELECT :roleId, 0

                UNION

                SELECT rh.child_role_id, d.depth + 1
                FROM role_hierarchy rh
                JOIN descendants d ON rh.parent_role_id = d.role_id
                WHERE d.depth < 32
            )
            SELECT role_id FROM descendants
            """;

    /**
     * roleId plus every role that (transitively) inherits its permissions — i.e. everyone who
     * needs their cached permissions invalidated when roleId's own permission grants change.
     * See doc/scope.md §8.3.
     */
    @SuppressWarnings("unchecked")
    public Set<UUID> findRoleIdAndDescendants(UUID roleId) {
        List<UUID> ids = entityManager.createNativeQuery(DESCENDANTS_SQL)
                .setParameter("roleId", roleId)
                .getResultList();
        return new LinkedHashSet<>(ids);
    }

    @SuppressWarnings("unchecked")
    public Set<UUID> findUserIdsWithAnyRole(Set<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = entityManager.createNativeQuery(
                        "SELECT DISTINCT user_id FROM user_roles WHERE role_id IN (:roleIds)")
                .setParameter("roleIds", roleIds)
                .getResultList();
        return new LinkedHashSet<>(ids);
    }
}
