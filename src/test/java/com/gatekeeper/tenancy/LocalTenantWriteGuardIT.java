package com.gatekeeper.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Sandbox stand-in for §10 T9 (tenant leakage - write), see T-05 note.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class LocalTenantWriteGuardIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void persistStampsTenantIdFromContext() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "stamp-" + UUID.randomUUID(), "Stamp Co");
        tenantRepository.saveAndFlush(tenant);

        TenantContext.set(tenant.getId());
        User user = new User(UUID.randomUUID(), "u-" + UUID.randomUUID() + "@test", "hash");
        userRepository.saveAndFlush(user);

        assertThat(user.getTenantId()).isEqualTo(tenant.getId());
    }

    @Test
    void updateWithMismatchedContextIsRejected() {
        Tenant tenantA = new Tenant(UUID.randomUUID(), "guard-a-" + UUID.randomUUID(), "A");
        Tenant tenantB = new Tenant(UUID.randomUUID(), "guard-b-" + UUID.randomUUID(), "B");
        tenantRepository.saveAndFlush(tenantA);
        tenantRepository.saveAndFlush(tenantB);

        TenantContext.set(tenantA.getId());
        User user = new User(UUID.randomUUID(), "u-" + UUID.randomUUID() + "@test", "hash");
        userRepository.saveAndFlush(user);

        TenantContext.set(tenantB.getId());
        user.setPasswordHash("new-hash");

        assertThatThrownBy(() -> userRepository.saveAndFlush(user))
                .isInstanceOf(CrossTenantAccessException.class);
    }
}
