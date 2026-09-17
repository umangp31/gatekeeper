package com.gatekeeper.tenancy;

import com.gatekeeper.rbac.Permission;
import com.gatekeeper.rbac.PermissionRepository;
import com.gatekeeper.rbac.Role;
import com.gatekeeper.rbac.RoleGraphRepository;
import com.gatekeeper.rbac.RoleRepository;
import com.gatekeeper.security.User;
import com.gatekeeper.security.UserService;
import com.gatekeeper.tenancy.dto.CreateTenantRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleGraphRepository roleGraphRepository;
    private final UserService userService;
    private final String bootstrapToken;

    public TenantService(TenantRepository tenantRepository,
                          RoleRepository roleRepository,
                          PermissionRepository permissionRepository,
                          RoleGraphRepository roleGraphRepository,
                          UserService userService,
                          @Value("${gatekeeper.bootstrap-token:}") String bootstrapToken) {
        this.tenantRepository = tenantRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.roleGraphRepository = roleGraphRepository;
        this.userService = userService;
        this.bootstrapToken = bootstrapToken;
    }

    /**
     * Creates a tenant plus its first administrator (an {@code admin} role with every catalogue
     * permission, and a user holding it). TenantContext is set to the new tenant for the
     * duration so the @PrePersist listener stamps {@code tenant_id} on the role and user, and
     * always cleared afterwards. See doc/scope.md §11.
     */
    @Transactional
    public Tenant createTenant(CreateTenantRequest request, String presentedToken) {
        if (bootstrapToken.isBlank() || !Objects.equals(bootstrapToken, presentedToken)) {
            throw new InvalidBootstrapTokenException();
        }
        Tenant tenant = tenantRepository.save(new Tenant(UUID.randomUUID(), request.slug(), request.name()));

        UUID previous = TenantContext.get();
        TenantContext.set(tenant.getId());
        try {
            Role adminRole = roleRepository.save(new Role(UUID.randomUUID(), "admin", "Tenant administrator"));
            for (Permission permission : permissionRepository.findAll()) {
                roleGraphRepository.addPermission(adminRole.getId(), permission.getId());
            }
            User admin = userService.createUserInternal(request.adminEmail(), request.adminPassword());
            roleGraphRepository.assignRoleToUser(admin.getId(), adminRole.getId());
        } finally {
            if (previous != null) {
                TenantContext.set(previous);
            } else {
                TenantContext.clear();
            }
        }
        return tenant;
    }

    @Transactional(readOnly = true)
    public Tenant getCurrentTenant() {
        UUID tenantId = TenantContext.require();
        return tenantRepository.findById(tenantId).orElseThrow(TenantContextMissingException::new);
    }

    @PreAuthorize("hasPermission('tenant', 'admin')")
    @Transactional
    public Tenant updateCurrentTenant(String newName) {
        Tenant tenant = getCurrentTenant();
        tenant.setName(newName);
        return tenant;
    }
}
