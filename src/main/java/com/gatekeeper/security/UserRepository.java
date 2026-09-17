package com.gatekeeper.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    /**
     * Use this instead of findById() for anything reachable from a tenant-scoped controller.
     * findById() translates to Hibernate's get()-by-primary-key path, which does NOT apply
     * @Filter conditions (only HQL/JPQL queries do) — see doc/scope.md §5.2 and the T-19
     * tenant-leakage test that caught this.
     */
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);
}
