package com.gatekeeper.security;

import com.gatekeeper.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.gatekeeper.rbac.CachedPermissionResolver cachedPermissionResolver;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       com.gatekeeper.rbac.CachedPermissionResolver cachedPermissionResolver) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cachedPermissionResolver = cachedPermissionResolver;
    }

    /**
     * The user's effective permission set — the recursive role-hierarchy CTE from doc/scope.md
     * §6.2, read through the permission cache (§8.2). Backs {@code GET /users/{id}/permissions}.
     */
    @PreAuthorize("hasPermission('user', 'read')")
    @Transactional(readOnly = true)
    public java.util.Set<String> getEffectivePermissions(UUID id) {
        UUID tenantId = TenantContext.require();
        userRepository.findByIdAndTenantId(id, tenantId).orElseThrow(UserNotFoundException::new);
        return cachedPermissionResolver.resolve(id, tenantId);
    }

    @PreAuthorize("hasPermission('user', 'write')")
    @Transactional
    public User createUser(String email, String rawPassword) {
        return createUserInternal(email, rawPassword);
    }

    /**
     * Unauthorized escape hatch for provisioning the very first user of a tenant (no one is
     * authenticated yet to hold user:write) — used by the bootstrap tenant-creation flow and by
     * test fixtures. Never expose this through a controller.
     */
    public User createUserInternal(String email, String rawPassword) {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    @PreAuthorize("hasPermission('user', 'read')")
    @Transactional(readOnly = true)
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @PreAuthorize("hasPermission('user', 'read')")
    @Transactional(readOnly = true)
    public User getUser(UUID id) {
        return userRepository.findByIdAndTenantId(id, TenantContext.require()).orElseThrow(UserNotFoundException::new);
    }

    @PreAuthorize("hasPermission('user', 'write')")
    @Transactional
    public User updateAttributes(UUID id, Map<String, Object> attributes) {
        User user = userRepository.findByIdAndTenantId(id, TenantContext.require()).orElseThrow(UserNotFoundException::new);
        user.setAttributes(attributes);
        return user;
    }
}
