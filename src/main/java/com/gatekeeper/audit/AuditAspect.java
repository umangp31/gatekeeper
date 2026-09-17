package com.gatekeeper.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Records an audit entry after a @Auditable method returns successfully — runs in the same
 * transaction as the intercepted method (AuditService.record's own @Transactional joins it), so
 * the audit entry only persists if the underlying change actually commits. See doc/scope.md §12.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @AfterReturning("@annotation(auditable)")
    public void recordAudit(JoinPoint joinPoint, Auditable auditable) {
        UUID actorId = currentActorId();
        String target = firstArgAsString(joinPoint);
        Map<String, Object> payload = argsAsPayload(joinPoint);
        auditService.record(actorId, auditable.action(), target, payload);
    }

    private UUID currentActorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        return null;
    }

    private String firstArgAsString(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        return args.length > 0 && args[0] != null ? args[0].toString() : null;
    }

    private Map<String, Object> argsAsPayload(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = names != null && i < names.length ? names[i] : "arg" + i;
            payload.put(key, args[i] == null ? null : args[i].toString());
        }
        return payload;
    }
}
