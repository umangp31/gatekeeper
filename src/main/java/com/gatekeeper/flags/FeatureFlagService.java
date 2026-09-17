package com.gatekeeper.flags;

import com.gatekeeper.audit.Auditable;
import com.gatekeeper.common.CacheInvalidationPublisher;
import com.gatekeeper.common.CacheKeys;
import com.gatekeeper.tenancy.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final FlagGraphRepository flagGraphRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;

    public FeatureFlagService(FeatureFlagRepository featureFlagRepository, FlagGraphRepository flagGraphRepository,
                               CacheInvalidationPublisher cacheInvalidationPublisher) {
        this.featureFlagRepository = featureFlagRepository;
        this.flagGraphRepository = flagGraphRepository;
        this.cacheInvalidationPublisher = cacheInvalidationPublisher;
    }

    @Auditable(action = "FLAG_CREATE")
    @PreAuthorize("hasPermission('flag', 'write')")
    @Transactional
    public FeatureFlag createFlag(String flagKey, String description) {
        FeatureFlag flag = new FeatureFlag(UUID.randomUUID(), flagKey, description);
        FeatureFlag saved = featureFlagRepository.save(flag);
        invalidateAfterCommit(flagKey); // clears any stale negative-cache tombstone for this key
        return saved;
    }

    @PreAuthorize("hasPermission('flag', 'read')")
    @Transactional(readOnly = true)
    public List<FeatureFlag> listFlags() {
        return featureFlagRepository.findAll();
    }

    @PreAuthorize("hasPermission('flag', 'read')")
    @Transactional(readOnly = true)
    public FeatureFlag getFlag(String flagKey) {
        return getFlagInternal(flagKey);
    }

    /**
     * Optimistic locking: expectedVersion must match the current row or Hibernate throws
     * ObjectOptimisticLockingFailureException on flush, mapped to 409 by ApiExceptionHandler
     * (T-32 upgrades this to full RFC 7807). See doc/scope.md §7.3, §9.
     */
    @Auditable(action = "FLAG_UPDATE")
    @PreAuthorize("hasPermission('flag', 'write')")
    @Transactional
    public FeatureFlag updateFlag(String flagKey, Boolean enabled, Integer rolloutPercentage, String description,
                                   long expectedVersion) {
        FeatureFlag flag = getFlagInternal(flagKey);
        if (flag.getVersion() != expectedVersion) {
            throw new jakarta.persistence.OptimisticLockException("Stale version for flag " + flagKey);
        }
        if (enabled != null) {
            flag.setEnabled(enabled);
        }
        if (rolloutPercentage != null) {
            flag.setRolloutPercentage(rolloutPercentage);
        }
        if (description != null) {
            flag.setDescription(description);
        }
        invalidateAfterCommit(flagKey);
        return flag;
    }

    @Auditable(action = "FLAG_DELETE")
    @PreAuthorize("hasPermission('flag', 'write')")
    @Transactional
    public void deleteFlag(String flagKey) {
        featureFlagRepository.delete(getFlagInternal(flagKey));
        invalidateAfterCommit(flagKey);
    }

    private void invalidateAfterCommit(String flagKey) {
        cacheInvalidationPublisher.afterCommitEvict(CacheKeys.flag(TenantContext.require(), flagKey));
    }

    @PreAuthorize("hasPermission('flag', 'write')")
    @Transactional
    public void addToWhitelist(String flagKey, UUID userId) {
        FeatureFlag flag = getFlagInternal(flagKey);
        flagGraphRepository.addToWhitelist(flag.getId(), userId);
    }

    @PreAuthorize("hasPermission('flag', 'write')")
    @Transactional
    public void removeFromWhitelist(String flagKey, UUID userId) {
        FeatureFlag flag = getFlagInternal(flagKey);
        flagGraphRepository.removeFromWhitelist(flag.getId(), userId);
    }

    @PreAuthorize("hasPermission('flag', 'write')")
    @Transactional
    public void setEnvironmentOverride(String flagKey, String environment, boolean enabled) {
        FeatureFlag flag = getFlagInternal(flagKey);
        flagGraphRepository.setOverride(flag.getId(), environment, enabled);
    }

    @PreAuthorize("hasPermission('flag', 'write')")
    @Transactional
    public void removeEnvironmentOverride(String flagKey, String environment) {
        FeatureFlag flag = getFlagInternal(flagKey);
        flagGraphRepository.removeOverride(flag.getId(), environment);
    }

    private FeatureFlag getFlagInternal(String flagKey) {
        return featureFlagRepository.findByFlagKeyAndTenantId(flagKey, TenantContext.require())
                .orElseThrow(FeatureFlagNotFoundException::new);
    }
}
