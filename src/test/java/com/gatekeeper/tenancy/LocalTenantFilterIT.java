package com.gatekeeper.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.security.User;
import com.gatekeeper.security.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sandbox stand-in for the T-10/§10 "tenant leakage - repository" test (see T-05 note):
 * proves the Hibernate @Filter enabled by TenantFilterActivator actually isolates tenants at
 * the repository layer, run against docker-compose Postgres instead of Testcontainers.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class LocalTenantFilterIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void findAllOnlyReturnsCurrentTenantsUsers() {
        Tenant tenantA = new Tenant(UUID.randomUUID(), "tenant-a-" + UUID.randomUUID(), "Tenant A");
        Tenant tenantB = new Tenant(UUID.randomUUID(), "tenant-b-" + UUID.randomUUID(), "Tenant B");
        tenantRepository.saveAndFlush(tenantA);
        tenantRepository.saveAndFlush(tenantB);

        TenantContext.set(tenantA.getId());
        User userA = new User(UUID.randomUUID(), "a-" + UUID.randomUUID() + "@test", "hash");
        userRepository.saveAndFlush(userA);

        TenantContext.set(tenantB.getId());
        User userB = new User(UUID.randomUUID(), "b-" + UUID.randomUUID() + "@test", "hash");
        userRepository.saveAndFlush(userB);

        TenantContext.set(tenantA.getId());
        var visible = userRepository.findAll();

        assertThat(visible).extracting(User::getId).containsExactly(userA.getId());
    }
}
