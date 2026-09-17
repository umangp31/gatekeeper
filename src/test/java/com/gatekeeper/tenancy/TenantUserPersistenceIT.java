package com.gatekeeper.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.security.User;
import com.gatekeeper.security.UserRepository;
import com.gatekeeper.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TenantUserPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsAndReadsTenantAndUser() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "acme", "Acme Inc");
        tenantRepository.saveAndFlush(tenant);

        User user = new User(UUID.randomUUID(), "admin@acme.test", "bcrypt-hash");
        user.setTenantId(tenant.getId());
        userRepository.saveAndFlush(user);

        assertThat(tenantRepository.findBySlug("acme")).isPresent();
        assertThat(userRepository.findByEmail("admin@acme.test"))
                .isPresent()
                .get()
                .extracting(User::getTenantId)
                .isEqualTo(tenant.getId());
    }
}
