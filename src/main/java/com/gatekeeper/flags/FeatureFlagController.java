package com.gatekeeper.flags;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gatekeeper.flags.dto.CreateFlagRequest;
import com.gatekeeper.flags.dto.EnvironmentOverrideRequest;
import com.gatekeeper.flags.dto.FlagResponse;
import com.gatekeeper.flags.dto.UpdateFlagRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Feature Flags", description = "Runtime-mutable feature flags: CRUD with optimistic locking, per-user whitelists and per-environment overrides.")
@RequestMapping("/api/v1/flags")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    public FeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @Operation(summary = "Create flag", description = "Creates a flag in the caller's tenant, initially disabled at 0% rollout. Requires flag:write. 409 if the key exists.")
    @PostMapping
    public ResponseEntity<FlagResponse> create(@Valid @RequestBody CreateFlagRequest request) {
        FeatureFlag flag = featureFlagService.createFlag(request.flagKey(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(FlagResponse.from(flag));
    }

    @Operation(summary = "List flags", description = "Lists all flags in the caller's tenant. Requires flag:read.")
    @GetMapping
    public List<FlagResponse> list() {
        return featureFlagService.listFlags().stream().map(FlagResponse::from).toList();
    }

    @Operation(summary = "Get flag", description = "Returns one flag by key, including its current version for optimistic locking. Requires flag:read.")
    @GetMapping("/{key}")
    public FlagResponse get(@PathVariable String key) {
        return FlagResponse.from(featureFlagService.getFlag(key));
    }

    @Operation(summary = "Update flag", description = "Sets enabled, rolloutPercentage (0-100) and description. The body must carry the version from the last read; a stale version is rejected with 409 (optimistic lock). Change takes effect immediately across all instances (cache invalidated). Audited. Requires flag:write.")
    @PutMapping("/{key}")
    public FlagResponse update(@PathVariable String key, @Valid @RequestBody UpdateFlagRequest request) {
        FeatureFlag flag = featureFlagService.updateFlag(key, request.enabled(), request.rolloutPercentage(),
                request.description(), request.version());
        return FlagResponse.from(flag);
    }

    @Operation(summary = "Delete flag", description = "Deletes the flag together with its whitelist and overrides. Subsequent evaluations return false. Audited. Requires flag:write.")
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        featureFlagService.deleteFlag(key);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Whitelist user", description = "Forces the flag ON for this user regardless of the global enabled switch or rollout percentage (only an environment override outranks it). Idempotent. Requires flag:write.")
    @PutMapping("/{key}/whitelist/{userId}")
    public ResponseEntity<Void> addToWhitelist(@PathVariable String key, @PathVariable UUID userId) {
        featureFlagService.addToWhitelist(key, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove user from whitelist", description = "Removes the per-user override; the user falls back to normal evaluation. Requires flag:write.")
    @DeleteMapping("/{key}/whitelist/{userId}")
    public ResponseEntity<Void> removeFromWhitelist(@PathVariable String key, @PathVariable UUID userId) {
        featureFlagService.removeFromWhitelist(key, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set environment override", description = "Pins the flag to enabled/disabled for one environment (e.g. prod, staging). This is the highest-priority rule: it applies when the server's APP_ENVIRONMENT matches. Requires flag:write.")
    @PutMapping("/{key}/overrides/{environment}")
    public ResponseEntity<Void> setOverride(@PathVariable String key, @PathVariable String environment,
                                             @Valid @RequestBody EnvironmentOverrideRequest request) {
        featureFlagService.setEnvironmentOverride(key, environment, request.enabled());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove environment override", description = "Removes the environment pin so the flag is evaluated normally in that environment. Requires flag:write.")
    @DeleteMapping("/{key}/overrides/{environment}")
    public ResponseEntity<Void> removeOverride(@PathVariable String key, @PathVariable String environment) {
        featureFlagService.removeEnvironmentOverride(key, environment);
        return ResponseEntity.noContent().build();
    }
}
