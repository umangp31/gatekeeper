package com.gatekeeper.rbac;

import com.gatekeeper.audit.Auditable;
import com.gatekeeper.common.CacheInvalidationPublisher;
import com.gatekeeper.common.CacheKeys;
import com.gatekeeper.tenancy.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleGraphRepository roleGraphRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final com.gatekeeper.security.UserRepository userRepository;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository,
                        RoleGraphRepository roleGraphRepository, CacheInvalidationPublisher cacheInvalidationPublisher,
                        com.gatekeeper.security.UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.roleGraphRepository = roleGraphRepository;
        this.cacheInvalidationPublisher = cacheInvalidationPublisher;
        this.userRepository = userRepository;
    }

    /**
     * Assigns roleId to userId (doc/scope.md §9, {@code POST /users/{id}/roles}). Both ids are
     * resolved tenant-scoped first, so a cross-tenant user or role id is a 404, never a silent
     * no-op. The user's cached effective permissions are evicted after commit (§8.3).
     */
    @Auditable(action = "USER_ROLE_ASSIGN")
    @PreAuthorize("hasPermission('role', 'assign')")
    @Transactional
    public void assignRoleToUser(UUID userId, UUID roleId) {
        Role role = getRoleInternal(roleId);
        requireUserInTenant(userId);
        roleGraphRepository.assignRoleToUser(userId, roleId);
        cacheInvalidationPublisher.afterCommitEvict(CacheKeys.permissions(role.getTenantId(), userId));
    }

    @Auditable(action = "USER_ROLE_REVOKE")
    @PreAuthorize("hasPermission('role', 'assign')")
    @Transactional
    public void revokeRoleFromUser(UUID userId, UUID roleId) {
        Role role = getRoleInternal(roleId);
        requireUserInTenant(userId);
        roleGraphRepository.revokeRoleFromUser(userId, roleId);
        cacheInvalidationPublisher.afterCommitEvict(CacheKeys.permissions(role.getTenantId(), userId));
    }

    private void requireUserInTenant(UUID userId) {
        userRepository.findByIdAndTenantId(userId, TenantContext.require())
                .orElseThrow(com.gatekeeper.security.UserNotFoundException::new);
    }

    @PreAuthorize("hasPermission('role', 'write')")
    @Transactional
    public Role createRole(String name, String description) {
        Role role = new Role(UUID.randomUUID(), name, description);
        return roleRepository.save(role);
    }

    @PreAuthorize("hasPermission('role', 'read')")
    @Transactional(readOnly = true)
    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    @PreAuthorize("hasPermission('role', 'read')")
    @Transactional(readOnly = true)
    public Role getRole(UUID id) {
        return getRoleInternal(id);
    }

    @PreAuthorize("hasPermission('role', 'write')")
    @Transactional
    public Role updateRole(UUID id, String description) {
        Role role = getRoleInternal(id);
        role.setDescription(description);
        return role;
    }

    @PreAuthorize("hasPermission('role', 'write')")
    @Transactional
    public void deleteRole(UUID id) {
        roleRepository.delete(getRoleInternal(id));
    }

    @PreAuthorize("hasPermission('role', 'read')")
    @Transactional(readOnly = true)
    public List<Permission> listPermissionCatalog() {
        return permissionRepository.findAll();
    }

    @Auditable(action = "ROLE_PERMISSION_GRANT")
    @PreAuthorize("hasPermission('role', 'write')")
    @Transactional
    public void grantPermission(UUID roleId, String permissionCode) {
        Role role = getRoleInternal(roleId); // 404 if missing/cross-tenant
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(PermissionNotFoundException::new);
        roleGraphRepository.addPermission(role.getId(), permission.getId());
        invalidateAffectedUsersAfterCommit(role);
    }

    @Auditable(action = "ROLE_PERMISSION_REVOKE")
    @PreAuthorize("hasPermission('role', 'write')")
    @Transactional
    public void revokePermission(UUID roleId, String permissionCode) {
        Role role = getRoleInternal(roleId);
        Permission permission = permissionRepository.findByCode(permissionCode)
                .orElseThrow(PermissionNotFoundException::new);
        roleGraphRepository.removePermission(role.getId(), permission.getId());
        invalidateAffectedUsersAfterCommit(role);
    }

    /**
     * Adds a hierarchy edge where childRoleId inherits parentRoleId's permissions. Rejects the
     * edge with a 409 if it would introduce a cycle — see doc/scope.md §6.2.
     */
    @Auditable(action = "ROLE_HIERARCHY_ADD")
    @PreAuthorize("hasPermission('role', 'write')")
    @Transactional
    public void addParent(UUID childRoleId, UUID parentRoleId) {
        Role child = getRoleInternal(childRoleId);
        Role parent = getRoleInternal(parentRoleId);
        if (Objects.equals(child.getId(), parent.getId())) {
            throw new CyclicHierarchyException();
        }
        if (roleGraphRepository.isAncestor(parent.getId(), child.getId())) {
            throw new CyclicHierarchyException();
        }
        roleGraphRepository.addHierarchyEdge(parent.getId(), child.getId());
        invalidateAffectedUsersAfterCommit(child); // child + its own descendants gained parent's permissions
    }

    @Auditable(action = "ROLE_HIERARCHY_REMOVE")
    @PreAuthorize("hasPermission('role', 'write')")
    @Transactional
    public void removeParent(UUID childRoleId, UUID parentRoleId) {
        Role child = getRoleInternal(childRoleId);
        Role parent = getRoleInternal(parentRoleId);
        roleGraphRepository.removeHierarchyEdge(parent.getId(), child.getId());
        invalidateAffectedUsersAfterCommit(child);
    }

    /**
     * Unauthorized internal lookup — the public getRole() already enforces role:read.
     * findByIdAndTenantId, not findById: see the Javadoc on RoleRepository for why findById()
     * alone would leak across tenants.
     */
    private Role getRoleInternal(UUID id) {
        return roleRepository.findByIdAndTenantId(id, TenantContext.require()).orElseThrow(RoleNotFoundException::new);
    }

    /**
     * Evicts perm:{tenant}:{userId} (after commit — see CacheInvalidationPublisher) for every
     * user holding role or any role that transitively inherits from it. See doc/scope.md §8.3.
     */
    private void invalidateAffectedUsersAfterCommit(Role role) {
        var affectedRoleIds = roleGraphRepository.findRoleIdAndDescendants(role.getId());
        var affectedUserIds = roleGraphRepository.findUserIdsWithAnyRole(affectedRoleIds);
        for (UUID userId : affectedUserIds) {
            cacheInvalidationPublisher.afterCommitEvict(CacheKeys.permissions(role.getTenantId(), userId));
        }
    }
}
