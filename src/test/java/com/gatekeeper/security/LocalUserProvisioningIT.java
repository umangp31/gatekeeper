package com.gatekeeper.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.tenancy.Tenant;
import com.gatekeeper.tenancy.TenantContext;
import com.gatekeeper.tenancy.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class LocalUserProvisioningIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void createdUserPasswordIsBcryptHashedNeverPlaintext() {
        Tenant tenant = new Tenant(UUID.randomUUID(), "pw-" + UUID.randomUUID(), "PW Co");
        tenantRepository.saveAndFlush(tenant);
        TenantContext.set(tenant.getId());

        String rawPassword = "correct-horse-battery-staple";
        User user = userService.createUserInternal("u-" + UUID.randomUUID() + "@test", rawPassword);

        assertThat(user.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(rawPassword, user.getPasswordHash())).isTrue();
    }
}
