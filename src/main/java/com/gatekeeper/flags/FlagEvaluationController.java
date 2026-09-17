package com.gatekeeper.flags;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.gatekeeper.flags.dto.EvaluateResponse;
import com.gatekeeper.rbac.GatekeeperPermissionEvaluator;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Flag Evaluation", description = "Resolve whether a flag is on for a user. Evaluation order: environment override > whitelist > global disable > percentage rollout.")
@RequestMapping("/api/v1/flags")
public class FlagEvaluationController {

    private final FlagEvaluator flagEvaluator;
    private final GatekeeperPermissionEvaluator permissionEvaluator;

    public FlagEvaluationController(FlagEvaluator flagEvaluator, GatekeeperPermissionEvaluator permissionEvaluator) {
        this.flagEvaluator = flagEvaluator;
        this.permissionEvaluator = permissionEvaluator;
    }

    /** Evaluates for the caller by default, or for a target user (requires flag:read) — see doc/scope.md §9. */
    @Operation(summary = "Evaluate one flag", description = "Returns whether the flag is enabled for the caller, or for another user of the same tenant when userId is given (that requires flag:read). Unknown keys evaluate to false. Rollout bucketing is deterministic: the same user always gets the same answer for a given flag.")
    @PostMapping("/{key}/evaluate")
    public EvaluateResponse evaluate(@PathVariable String key,
                                      @Parameter(description = "Evaluate for this user instead of the caller (requires flag:read)") @RequestParam(required = false) UUID userId,
                                      @AuthenticationPrincipal Jwt jwt) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant"));

        UUID targetUserId = userId != null ? userId : callerId;
        if (!targetUserId.equals(callerId) && !permissionEvaluator.effectivePermissions(jwtAuthentication(jwt))
                .contains("flag:read")) {
            throw new AccessDeniedException("flag:read required to evaluate for another user");
        }

        boolean enabled = flagEvaluator.evaluate(key, targetUserId, tenantId);
        return new EvaluateResponse(key, enabled);
    }

    @Operation(summary = "Evaluate all flags", description = "Returns a map of every flag key in the tenant to its evaluated value for the caller — intended for client bootstrap in a single request. No special permission required.")
    @GetMapping("/evaluate")
    public Map<String, Boolean> evaluateAll(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant"));
        return flagEvaluator.evaluateAll(userId, tenantId);
    }

    private org.springframework.security.core.Authentication jwtAuthentication(Jwt jwt) {
        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
    }
}
