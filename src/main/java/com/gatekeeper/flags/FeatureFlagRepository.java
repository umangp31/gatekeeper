package com.gatekeeper.flags;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    /** findById() alone would leak across tenants — see doc/scope.md §5.2 and the T-19 finding. */
    Optional<FeatureFlag> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<FeatureFlag> findByFlagKeyAndTenantId(String flagKey, UUID tenantId);
}
