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
 * Sandbox-only stand-in for {@link TenantUserPersistenceIT}: this environment's Testcontainers
 * cannot reach Docker Desktop's socket (see doc/session.md T-05 note), so this test exercises
 * the same persistence path directly against the docker-compose Postgres on the `local` profile
 * instead. Delete once Testcontainers is confirmed working in a normal terminal/CI.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class LocalTenantUserPersistenceIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void persistsAndReadsTenantAndUser() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "acme-" + UUID.randomUUID(), "Acme Inc");
        tenantRepository.saveAndFlush(tenant);

        TenantContext.set(tenant.getId());
        String email = "admin-" + UUID.randomUUID() + "@acme.test";
        User user = new User(UUID.randomUUID(), email, "bcrypt-hash");
        userRepository.saveAndFlush(user);

        assertThat(tenantRepository.findBySlug(tenant.getSlug())).isPresent();
        assertThat(userRepository.findByEmail(email))
                .isPresent()
                .get()
                .extracting(User::getTenantId)
                .isEqualTo(tenant.getId());
    }
}
