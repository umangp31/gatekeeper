package com.gatekeeper.flags;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Manages flag_whitelist and flag_environment_override directly, mirroring RoleGraphRepository's approach. */
@Repository
public class FlagGraphRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void addToWhitelist(UUID flagId, UUID userId) {
        entityManager.createNativeQuery(
                        "INSERT INTO flag_whitelist (flag_id, user_id) VALUES (:flagId, :userId) ON CONFLICT DO NOTHING")
                .setParameter("flagId", flagId)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public void removeFromWhitelist(UUID flagId, UUID userId) {
        entityManager.createNativeQuery(
                        "DELETE FROM flag_whitelist WHERE flag_id = :flagId AND user_id = :userId")
                .setParameter("flagId", flagId)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public boolean isWhitelisted(UUID flagId, UUID userId) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT count(*) FROM flag_whitelist WHERE flag_id = :flagId AND user_id = :userId")
                .setParameter("flagId", flagId)
                .setParameter("userId", userId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    public void setOverride(UUID flagId, String environment, boolean enabled) {
        entityManager.createNativeQuery("""
                        INSERT INTO flag_environment_override (flag_id, environment, enabled)
                        VALUES (:flagId, :environment, :enabled)
                        ON CONFLICT (flag_id, environment) DO UPDATE SET enabled = :enabled
                        """)
                .setParameter("flagId", flagId)
                .setParameter("environment", environment)
                .setParameter("enabled", enabled)
                .executeUpdate();
    }

    public void removeOverride(UUID flagId, String environment) {
        entityManager.createNativeQuery(
                        "DELETE FROM flag_environment_override WHERE flag_id = :flagId AND environment = :environment")
                .setParameter("flagId", flagId)
                .setParameter("environment", environment)
                .executeUpdate();
    }

    public Optional<Boolean> getOverride(UUID flagId, String environment) {
        var results = entityManager.createNativeQuery(
                        "SELECT enabled FROM flag_environment_override WHERE flag_id = :flagId AND environment = :environment")
                .setParameter("flagId", flagId)
                .setParameter("environment", environment)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of((Boolean) results.get(0));
    }
}
