package dev.automata.automata.security;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
@RequiredArgsConstructor
public class RoleAccessAspect {

    @Before("@annotation(requireRole)")
    public void checkRoleAccess(JoinPoint joinPoint, RequireRole requireRole) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }

        Set<String> userRoles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        String[] required = requireRole.value();
        boolean hasAccess;

        if (requireRole.matchAll()) {
            // AND logic — user must have ALL listed roles
            hasAccess = Arrays.stream(required)
                    .allMatch(role -> userRoles.contains("ROLE_" + role));
        } else {
            // OR logic — user must have AT LEAST ONE
            hasAccess = Arrays.stream(required)
                    .anyMatch(role -> userRoles.contains("ROLE_" + role));
        }

        if (!hasAccess) {
            throw new AccessDeniedException(requireRole.message());
        }
    }
}
