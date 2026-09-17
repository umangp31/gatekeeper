package com.gatekeeper.flags;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the §7.1 evaluation order exactly:
 *   1. flag not found -> false
 *   2. environment override for the running APP_ENVIRONMENT -> its value (outranks everything)
 *   3. user is whitelisted -> true (outranks the global kill-switch — lets QA in early)
 *   4. flag globally disabled -> false
 *   5. percentage rollout -> deterministic bucket < rolloutPercentage
 *   6. otherwise -> true (enabled, 100% rollout)
 */
@Component
public class FlagEvaluator {

    private final CachedFlagRepository cachedFlagRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final FlagGraphRepository flagGraphRepository;
    private final RolloutBucketer rolloutBucketer;
    private final String environment;

    public FlagEvaluator(CachedFlagRepository cachedFlagRepository, FeatureFlagRepository featureFlagRepository,
                          FlagGraphRepository flagGraphRepository, RolloutBucketer rolloutBucketer,
                          @Value("${gatekeeper.environment}") String environment) {
        this.cachedFlagRepository = cachedFlagRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.flagGraphRepository = flagGraphRepository;
        this.rolloutBucketer = rolloutBucketer;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public boolean evaluate(String flagKey, UUID userId, UUID tenantId) {
        var flagOpt = cachedFlagRepository.getFlag(flagKey, tenantId);
        if (flagOpt.isEmpty()) {
            return false;
        }
        CachedFlag flag = flagOpt.get();

        var override = flagGraphRepository.getOverride(flag.id(), environment);
        if (override.isPresent()) {
            return override.get();
        }

        if (flagGraphRepository.isWhitelisted(flag.id(), userId)) {
            return true;
        }

        if (!flag.enabled()) {
            return false;
        }

        return rolloutBucketer.isInRollout(flagKey, userId, flag.rolloutPercentage());
    }

    /**
     * Evaluates every flag in the tenant for one user in a single flags query (§9 GET
     * /flags/evaluate bootstrap endpoint) — bypasses the per-key cache since this already reads
     * the whole tenant's flag set at once; per-flag override/whitelist checks still run
     * individually (small N, not the bulk fan-out this endpoint exists to avoid).
     */
    @Transactional(readOnly = true)
    public Map<String, Boolean> evaluateAll(UUID userId, UUID tenantId) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        for (FeatureFlag flag : featureFlagRepository.findAll()) {
            results.put(flag.getFlagKey(), evaluateLoadedFlag(flag, userId));
        }
        return results;
    }

    private boolean evaluateLoadedFlag(FeatureFlag flag, UUID userId) {
        var override = flagGraphRepository.getOverride(flag.getId(), environment);
        if (override.isPresent()) {
            return override.get();
        }
        if (flagGraphRepository.isWhitelisted(flag.getId(), userId)) {
            return true;
        }
        if (!flag.isEnabled()) {
            return false;
        }
        return rolloutBucketer.isInRollout(flag.getFlagKey(), userId, flag.getRolloutPercentage());
    }
}
